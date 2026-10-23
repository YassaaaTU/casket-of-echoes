package com.soulsvault.config;

import com.soulsvault.Constants;

/**
 * Platform-independent runtime configuration state for Casket of Echoes.
 * Backed by ForgeConfigSpec on Forge ({@code config/soulsvault-common.toml})
 * and toggleable in-game.
 */
public final class SoulsVaultConfig {
    private static volatile boolean fullInventoryReplacement = true;
    private static volatile boolean allowRemoteVaultInFullInventory = true;
    private static volatile boolean overflowOnFullInventory = true;
    private static volatile long maxStackCapacity = Constants.DEFAULT_MAX_STACK_CAPACITY;
    private static volatile Runnable saveHook = () -> {
    };

    private SoulsVaultConfig() {
    }

    /**
     * When {@code true}, opening the player inventory ({@code E} key in Survival/Adventure)
     * replaces the vanilla InventoryScreen with the Souls-like Casket & Inventory UI.
     * When {@code false}, {@code E} opens the vanilla inventory and the Casket UI is only
     * opened at {@code #soulsvault:rest_points}, {@code BottomlessCasketBlock}, or {@code SpectralCasketEntity}.
     */
    public static boolean isFullInventoryReplacement() {
        return fullInventoryReplacement;
    }

    public static void setFullInventoryReplacement(boolean enabled) {
        fullInventoryReplacement = enabled;
        saveHook.run();
    }

    /**
     * When {@code true} and {@link #isFullInventoryReplacement()} is enabled, the player can
     * deposit and withdraw items from their Vault anywhere (not only at Rest Points).
     */
    public static boolean isAllowRemoteVaultInFullInventory() {
        return allowRemoteVaultInFullInventory;
    }

    public static void setAllowRemoteVaultInFullInventory(boolean allowed) {
        allowRemoteVaultInFullInventory = allowed;
        saveHook.run();
    }

    public static boolean isOverflowOnFullInventory() {
        return overflowOnFullInventory;
    }

    public static void setOverflowOnFullInventory(boolean enabled) {
        overflowOnFullInventory = enabled;
        saveHook.run();
    }

    public static long getMaxStackCapacity() {
        return maxStackCapacity;
    }

    public static void setMaxStackCapacity(long capacity) {
        maxStackCapacity = Math.max(1L, capacity);
        saveHook.run();
    }

    public static void bindValues(
            boolean fullInvReplacement,
            boolean remoteVaultInFullInv,
            boolean overflowOnFull,
            long maxCapacity,
            Runnable onSave
    ) {
        fullInventoryReplacement = fullInvReplacement;
        allowRemoteVaultInFullInventory = remoteVaultInFullInv;
        overflowOnFullInventory = overflowOnFull;
        maxStackCapacity = Math.max(1L, maxCapacity);
        if (onSave != null) {
            saveHook = onSave;
        }
    }
}
