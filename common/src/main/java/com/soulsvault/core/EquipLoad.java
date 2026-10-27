package com.soulsvault.core;

import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.soulsvault.Constants;
import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.platform.Services;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Souls-style Equip Load: the summed weight of worn gear against a carrying capacity.
 * <p>
 * Weights come from datapacks at {@code data/<namespace>/soulsvault/equip_load/<name>.json}:
 * <pre>{@code
 * { "entries": [ { "item": "minecraft:netherite_chestplate", "weight": 15.0 },
 *                { "tag": "minecraft:swords", "weight": 4.0 } ] }
 * }</pre>
 * then Epic Fight's weight attribute when present, then a fallback derived from armor / attack stats. Capacity is
 * {@code equipLoad.baseCapacity} plus the {@code soulsvault:equip_load_bonus} attribute, so rings, skills or
 * KubeJS scripts can raise it with ordinary attribute modifiers.
 */
public final class EquipLoad {
    public static final String DIRECTORY = "soulsvault/equip_load";
    private static final UUID OVERLOAD_MODIFIER_ID = UUID.fromString("5c1f3a5e-4b0e-4a8e-9a77-0e6b1d3c2f41");
    private static final EquipmentSlot[] WORN_SLOTS = {
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    /**
     * Load bands shown on the meter.
     */
    public enum Tier {
        LIGHT,
        MEDIUM,
        HEAVY,
        OVERLOADED;

        /**
         * The band for {@code load / capacity}.
         */
        public static Tier of(double ratio) {
            if (ratio > 1.0D) {
                return OVERLOADED;
            }
            if (ratio > 0.7D) {
                return HEAVY;
            }
            return ratio > 0.3D ? MEDIUM : LIGHT;
        }
    }

    /**
     * A player's summed gear weight and carrying capacity.
     */
    public record Result(double load, double capacity) {
        public static final Result NONE = new Result(0.0D, 0.0D);

        public double ratio() {
            return this.capacity <= 0.0D ? 0.0D : this.load / this.capacity;
        }

        public Tier tier() {
            return Tier.of(ratio());
        }
    }

    private record TagWeight(TagKey<Item> tag, double weight) {
    }

    private static volatile Map<Item, Double> itemWeights = Map.of();
    private static volatile List<TagWeight> tagWeights = List.of();
    private static final Map<UUID, Result> LAST_SENT = new ConcurrentHashMap<>();

    private EquipLoad() {
    }

    /**
     * Weight of one worn item: datapack item entry, then tag entry, then Epic Fight's weight, then a fallback
     * derived from its armor or attack stats.
     */
    public static double weightOf(ItemStack stack, EquipmentSlot slot) {
        if (stack.isEmpty()) {
            return 0.0D;
        }
        Double explicit = itemWeights.get(stack.getItem());
        if (explicit != null) {
            return explicit;
        }
        for (TagWeight tagWeight : tagWeights) {
            if (stack.is(tagWeight.tag())) {
                return tagWeight.weight();
            }
        }
        double epicFight = Services.PLATFORM.getEpicFightWeight(stack, slot);
        if (epicFight > 0.0D) {
            return epicFight;
        }
        return fallbackWeight(stack, slot);
    }

    private static double fallbackWeight(ItemStack stack, EquipmentSlot slot) {
        if (stack.getItem() instanceof ArmorItem armor) {
            return armor.getDefense() * 1.5D + armor.getToughness() + armor.getMaterial().getKnockbackResistance() * 10.0D;
        }
        if (stack.getItem() instanceof ShieldItem) {
            return 4.0D;
        }
        if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
            double damage = sumModifiers(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE);
            return damage > 0.0D ? damage * 0.7D : 0.0D;
        }
        return 0.0D;
    }

    private static double sumModifiers(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
        Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(slot);
        Collection<AttributeModifier> list = modifiers.get(attribute);
        double sum = 0.0D;
        for (AttributeModifier modifier : list) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                sum += modifier.getAmount();
            }
        }
        return sum;
    }

    /**
     * Sums the weight of hands, armor and Curios against the player's capacity.
     */
    public static Result compute(ServerPlayer player) {
        double load = 0.0D;
        for (EquipmentSlot slot : WORN_SLOTS) {
            load += weightOf(player.getItemBySlot(slot), slot);
        }
        for (List<ItemKey> curios : Services.PLATFORM.captureCurioSlots(player).values()) {
            for (ItemKey key : curios) {
                if (!key.isEmpty()) {
                    load += weightOf(key.toSingleStack(), EquipmentSlot.CHEST);
                }
            }
        }
        double capacity = SoulsVaultConfig.equipLoad().baseCapacity() + Services.PLATFORM.getEquipLoadBonus(player);
        return new Result(load, Math.max(1.0D, capacity));
    }

    /**
     * Recomputes the player's Equip Load, applies the optional overload slowdown, and syncs the meter when the
     * value changed. Called on equipment changes and periodically (Curios changes fire no vanilla event).
     */
    public static void update(ServerPlayer player) {
        SoulsVaultConfig.EquipLoad config = SoulsVaultConfig.equipLoad();
        Result result = config.enabled() ? compute(player) : Result.NONE;

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            boolean slow = config.enabled() && config.slowdownWhenOverloaded() && result.tier() == Tier.OVERLOADED;
            AttributeModifier existing = speed.getModifier(OVERLOAD_MODIFIER_ID);
            if (slow && existing == null) {
                speed.addTransientModifier(new AttributeModifier(
                        OVERLOAD_MODIFIER_ID,
                        "Casket of Echoes overloaded",
                        -Math.min(0.95D, Math.max(0.0D, config.overloadedSpeedPenalty())),
                        AttributeModifier.Operation.MULTIPLY_TOTAL
                ));
            } else if (!slow && existing != null) {
                speed.removeModifier(OVERLOAD_MODIFIER_ID);
            }
        }

        Result rounded = new Result(Math.round(result.load() * 10.0D) / 10.0D, Math.round(result.capacity() * 10.0D) / 10.0D);
        if (!rounded.equals(LAST_SENT.get(player.getUUID()))) {
            LAST_SENT.put(player.getUUID(), rounded);
            Services.PLATFORM.sendEquipLoad(player, rounded.load(), rounded.capacity());
        }
    }

    public static void forget(UUID playerId) {
        LAST_SENT.remove(playerId);
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
            Map<Item, Double> items = new HashMap<>();
            List<TagWeight> tags = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> file : files.entrySet()) {
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(file.getValue(), "equip load");
                    for (JsonElement element : GsonHelper.getAsJsonArray(json, "entries")) {
                        JsonObject entry = GsonHelper.convertToJsonObject(element, "entry");
                        double weight = Math.max(0.0D, GsonHelper.getAsDouble(entry, "weight"));
                        if (entry.has("tag")) {
                            tags.add(new TagWeight(
                                    TagKey.create(Registries.ITEM, new ResourceLocation(GsonHelper.getAsString(entry, "tag"))),
                                    weight
                            ));
                        } else {
                            ResourceLocation itemId = new ResourceLocation(GsonHelper.getAsString(entry, "item"));
                            RegistryLookups.item(itemId).ifPresent(item -> items.put(item, weight));
                        }
                    }
                } catch (RuntimeException e) {
                    Constants.LOG.error("Invalid Casket of Echoes equip load file {}: {}", file.getKey(), e.getMessage());
                }
            }
            itemWeights = Map.copyOf(items);
            tagWeights = List.copyOf(tags);
            LAST_SENT.clear();
            Constants.LOG.info("Loaded {} item and {} tag Equip Load weight(s)", items.size(), tags.size());
        }
    }
}
