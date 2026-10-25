package com.soulsvault.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Built-in sort for the 27 main inventory slots (the hotbar, armor, offhand, crafting grid and Vault are left
 * alone). Used when no inventory-sorting mod handles it: merges partial stacks, then orders by Vault tab, item id,
 * then damage and count.
 */
public final class InventorySorter {
    /** First and last-exclusive {@link Inventory#items} index of the main inventory (the hotbar is 0-8). */
    public static final int MAIN_START = Inventory.getSelectionSize();
    public static final int MAIN_END = Inventory.INVENTORY_SIZE;

    private static final Comparator<ItemStack> ORDER = Comparator
            .comparing((ItemStack stack) -> VaultCategory.classify(stack).ordinal())
            .thenComparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
            .thenComparingInt(ItemStack::getDamageValue)
            .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());

    private InventorySorter() {
    }

    public static void sortMainInventory(Player player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> merged = new ArrayList<>();
        for (int i = MAIN_START; i < MAIN_END; i++) {
            ItemStack stack = inventory.items.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack remaining = stack.copy();
            for (ItemStack target : merged) {
                if (remaining.isEmpty()) {
                    break;
                }
                if (ItemStack.isSameItemSameTags(target, remaining) && target.getCount() < target.getMaxStackSize()) {
                    int moved = Math.min(remaining.getCount(), target.getMaxStackSize() - target.getCount());
                    target.grow(moved);
                    remaining.shrink(moved);
                }
            }
            if (!remaining.isEmpty()) {
                merged.add(remaining);
            }
        }
        merged.sort(ORDER);
        for (int i = MAIN_START; i < MAIN_END; i++) {
            int index = i - MAIN_START;
            inventory.items.set(i, index < merged.size() ? merged.get(index) : ItemStack.EMPTY);
        }
        inventory.setChanged();
    }
}
