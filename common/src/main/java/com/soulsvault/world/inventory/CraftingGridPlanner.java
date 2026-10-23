package com.soulsvault.world.inventory;

import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultEntry;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recipe-viewer agnostic planner for filling the Casket's 3x3 crafting grid (JEI / REI "+" button, EMI fill).
 * <p>
 * A recipe is described per grid slot (row-major 3x3) as a list of acceptable ingredient alternatives. The
 * planner picks, for every slot, an item the player actually owns, counting the 36 main inventory slots, the
 * current crafting grid contents, and (when access is allowed) the Vault. The same plan runs on the client, to
 * flag missing ingredients inside the recipe viewer, and on the server, which re-validates before moving items.
 */
public final class CraftingGridPlanner {
    public static final int GRID_SIZE = 9;
    /** Upper bound on candidate items sent per slot, keeping the fill packet small. */
    public static final int MAX_CANDIDATES_PER_SLOT = 32;

    private CraftingGridPlanner() {
    }

    /**
     * Everything usable for a fill, merged by {@link ItemKey}, with a per-{@link Item} index for fast
     * matching of NBT-less recipe alternatives.
     */
    public static final class Availability {
        private final Map<ItemKey, Long> counts = new LinkedHashMap<>();
        private final Map<Item, List<ItemKey>> keysByItem = new HashMap<>();

        private void add(ItemKey key, long count) {
            if (key.isEmpty() || count <= 0L) {
                return;
            }
            Long previous = this.counts.put(key, count + this.counts.getOrDefault(key, 0L));
            if (previous == null) {
                this.keysByItem.computeIfAbsent(key.getItem(), item -> new ArrayList<>(2)).add(key);
            }
        }

        public Map<ItemKey, Long> counts() {
            return Collections.unmodifiableMap(this.counts);
        }

        public long getCount(ItemKey key) {
            return this.counts.getOrDefault(key, 0L);
        }

        List<ItemKey> keysFor(Item item) {
            return this.keysByItem.getOrDefault(item, List.of());
        }
    }

    /**
     * Result of planning a recipe fill.
     *
     * @param chosen       item chosen per grid slot ({@link ItemKey#EMPTY} for slots without an ingredient)
     * @param missingSlots grid slot indices whose ingredient the player has none of
     * @param craftCount   number of crafts the grid is filled for (0 when the recipe cannot be filled)
     */
    public record Plan(ItemKey[] chosen, List<Integer> missingSlots, int craftCount) {
        public boolean canFill() {
            return this.missingSlots.isEmpty() && this.craftCount > 0;
        }
    }

    /**
     * Collects the player's main inventory, the crafting grid contents, and optionally the Vault.
     *
     * @param vault the Vault to draw from, or {@code null} when Vault access is not allowed
     */
    public static Availability collectAvailable(Player player, Container craftGrid, @Nullable PlayerVaultData vault) {
        Availability availability = new Availability();
        for (ItemStack stack : player.getInventory().items) {
            availability.add(ItemKey.fromStack(stack), stack.getCount());
        }
        for (int i = 0; i < craftGrid.getContainerSize(); i++) {
            ItemStack stack = craftGrid.getItem(i);
            availability.add(ItemKey.fromStack(stack), stack.getCount());
        }
        if (vault != null) {
            for (VaultEntry entry : vault.getEntries()) {
                availability.add(entry.getKey(), entry.getCount());
            }
        }
        return availability;
    }

    /**
     * Narrows each slot's ingredient alternatives (as displayed by a recipe viewer) to items the player owns.
     * Exact item + NBT matches come first, then same-item matches for alternatives that carry no NBT
     * (e.g. a damaged tool for a plain tool ingredient, mirroring vanilla {@code Ingredient} behaviour).
     *
     * @return per slot: {@code null} when the slot has no ingredient, otherwise the ordered candidates
     * (empty when the player owns none of them)
     */
    public static List<List<ItemKey>> resolveCandidates(Availability availability, List<List<ItemStack>> slotAlternatives) {
        List<List<ItemKey>> result = new ArrayList<>(GRID_SIZE);
        for (int slot = 0; slot < GRID_SIZE; slot++) {
            List<ItemStack> alternatives = slot < slotAlternatives.size() ? slotAlternatives.get(slot) : List.of();
            boolean hasIngredient = false;
            Set<ItemKey> exact = new LinkedHashSet<>();
            Set<ItemKey> sameItem = new LinkedHashSet<>();
            for (ItemStack alternative : alternatives) {
                if (alternative == null || alternative.isEmpty()) {
                    continue;
                }
                hasIngredient = true;
                ItemKey altKey = ItemKey.fromStack(alternative);
                if (availability.getCount(altKey) > 0L) {
                    exact.add(altKey);
                }
                if (!altKey.hasTag()) {
                    sameItem.addAll(availability.keysFor(altKey.getItem()));
                }
            }
            if (!hasIngredient) {
                result.add(null);
                continue;
            }
            List<ItemKey> candidates = new ArrayList<>(exact);
            for (ItemKey key : sameItem) {
                if (candidates.size() >= MAX_CANDIDATES_PER_SLOT) {
                    break;
                }
                if (!exact.contains(key)) {
                    candidates.add(key);
                }
            }
            result.add(candidates.size() > MAX_CANDIDATES_PER_SLOT
                    ? new ArrayList<>(candidates.subList(0, MAX_CANDIDATES_PER_SLOT))
                    : candidates);
        }
        return result;
    }

    /**
     * Chooses one candidate per slot (the one with the most left, earlier candidates winning ties) and computes
     * how many crafts the grid can hold, capped by {@code requestedCrafts}, ingredient totals, and stack sizes.
     */
    public static Plan plan(Availability availability, List<List<ItemKey>> slotCandidates, int requestedCrafts) {
        ItemKey[] chosen = new ItemKey[GRID_SIZE];
        Arrays.fill(chosen, ItemKey.EMPTY);
        List<Integer> missingSlots = new ArrayList<>();
        Map<ItemKey, Long> remaining = new HashMap<>(availability.counts);
        Map<ItemKey, Integer> uses = new LinkedHashMap<>();

        for (int slot = 0; slot < GRID_SIZE; slot++) {
            List<ItemKey> candidates = slot < slotCandidates.size() ? slotCandidates.get(slot) : null;
            if (candidates == null) {
                continue;
            }
            ItemKey best = null;
            long bestRemaining = 0L;
            for (ItemKey key : candidates) {
                long left = remaining.getOrDefault(key, 0L);
                if (left > bestRemaining) {
                    best = key;
                    bestRemaining = left;
                }
            }
            if (best == null) {
                missingSlots.add(slot);
                continue;
            }
            chosen[slot] = best;
            remaining.put(best, bestRemaining - 1L);
            uses.merge(best, 1, Integer::sum);
        }

        if (!missingSlots.isEmpty() || uses.isEmpty()) {
            return new Plan(chosen, missingSlots, 0);
        }

        long crafts = Math.max(1, requestedCrafts);
        for (Map.Entry<ItemKey, Integer> use : uses.entrySet()) {
            ItemKey key = use.getKey();
            crafts = Math.min(crafts, availability.getCount(key) / use.getValue());
            crafts = Math.min(crafts, key.toSingleStack().getMaxStackSize());
        }
        return new Plan(chosen, missingSlots, (int) Math.max(1L, crafts));
    }
}
