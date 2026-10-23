package com.soulsvault.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * An extra group of slots a Build Echo can save and restore, beyond the built-in hands, armor and Curios.
 * <p>
 * Register implementations with {@link SoulsVaultApi#registerEchoSlotType(EchoSlotType)} during mod construction
 * (both sides). Saving records each slot's item; swapping deposits the current item into the Casket and withdraws
 * the saved one. Example: the built-in {@code soulsvault:hotbar} type; a mod could add spell-scroll pouches,
 * quiver slots, or quick-item belts.
 */
public interface EchoSlotType {

    /** Stable id, saved inside every Build Echo. */
    ResourceLocation id();

    /** Shown in the Build Echo options popover. */
    Component displayName();

    /** Number of slots the player currently has for this type. */
    int slotCount(ServerPlayer player);

    ItemStack getStack(ServerPlayer player, int slot);

    void setStack(ServerPlayer player, int slot, ItemStack stack);

    default boolean mayPlace(ServerPlayer player, int slot, ItemStack stack) {
        return true;
    }

    /** Whether new Build Echoes restore this type unless the player turns it off. */
    default boolean enabledByDefault() {
        return true;
    }

    /**
     * {@code true} to save and restore full stack sizes (quick items, ammo); {@code false} for single equipment.
     */
    default boolean restoresStackSize() {
        return false;
    }
}
