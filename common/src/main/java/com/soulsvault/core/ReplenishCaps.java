package com.soulsvault.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.soulsvault.Constants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pack-enforced Estus-style carry caps for Rest &amp; Replenish, loaded from datapacks at
 * {@code data/<namespace>/soulsvault/replenish_caps/<name>.json}:
 * <pre>{@code
 * {
 *   "items": ["minecraft:golden_apple", "#forge:potions"],
 *   "max_carried": 10
 * }
 * }</pre>
 * Every item matching a rule shares its cap: resting never refills the player past {@code max_carried} matching
 * items in total, whatever refill target they picked. Picking up, crafting or withdrawing manually is not limited.
 */
public final class ReplenishCaps {
    public static final String DIRECTORY = "soulsvault/replenish_caps";

    /**
     * One cap: item ids and item tags that share {@code maxCarried}.
     */
    public record Rule(ResourceLocation id, Set<Item> items, List<TagKey<Item>> tags, int maxCarried) {
        public boolean matches(ItemKey key) {
            if (key.isEmpty()) {
                return false;
            }
            if (this.items.contains(key.getItem())) {
                return true;
            }
            ItemStack stack = key.toSingleStack();
            for (TagKey<Item> tag : this.tags) {
                if (stack.is(tag)) {
                    return true;
                }
            }
            return false;
        }

        public int countCarried(Inventory inventory) {
            int total = 0;
            for (ItemStack stack : inventory.items) {
                if (!stack.isEmpty() && matches(ItemKey.fromStack(stack))) {
                    total += stack.getCount();
                }
            }
            return total;
        }
    }

    private static volatile List<Rule> rules = List.of();

    private ReplenishCaps() {
    }

    public static List<Rule> getRules() {
        return rules;
    }

    /**
     * Datapack reload listener; register it with the loader's reload-listener hook.
     */
    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() {
            super(Constants.GSON, DIRECTORY);
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
            List<Rule> loaded = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(file.getValue(), "replenish cap");
                    Set<Item> items = new HashSet<>();
                    List<TagKey<Item>> tags = new ArrayList<>();
                    JsonArray entries = GsonHelper.getAsJsonArray(json, "items");
                    for (JsonElement element : entries) {
                        String value = GsonHelper.convertToString(element, "item");
                        if (value.startsWith("#")) {
                            tags.add(TagKey.create(Registries.ITEM, new ResourceLocation(value.substring(1))));
                        } else {
                            ResourceLocation itemId = new ResourceLocation(value);
                            BuiltInRegistries.ITEM.getOptional(itemId).ifPresentOrElse(
                                    items::add,
                                    () -> Constants.LOG.debug("Replenish cap {} skips unknown item {}", file.getKey(), itemId)
                            );
                        }
                    }
                    int maxCarried = Math.max(0, GsonHelper.getAsInt(json, "max_carried"));
                    loaded.add(new Rule(file.getKey(), Set.copyOf(items), List.copyOf(tags), maxCarried));
                } catch (RuntimeException e) {
                    Constants.LOG.error("Invalid Casket of Echoes replenish cap {}: {}", file.getKey(), e.getMessage());
                }
            }
            rules = List.copyOf(loaded);
            Constants.LOG.info("Loaded {} Casket of Echoes replenish cap(s)", loaded.size());
        }
    }
}
