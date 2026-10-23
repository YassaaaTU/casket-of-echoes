package com.soulsvault.config;

import com.soulsvault.Constants;

/**
 * Platform-independent, server-authoritative gameplay configuration for Casket of Echoes.
 * <p>
 * Backed by a server config on Forge ({@code <world>/serverconfig/soulsvault-server.toml}, with modpack
 * defaults shipped in {@code defaultconfigs/soulsvault-server.toml}) and synced to every client so both
 * sides always agree. These values are intentionally read-only in game: the modpack / server decides them,
 * and they can be edited through config GUI mods (Configured, Config Menus for Forge) or the file itself.
 */
public final class SoulsVaultConfig {
    private static volatile boolean fullInventoryReplacement = true;
    private static volatile boolean allowRemoteVaultInFullInventory = true;
    private static volatile boolean overflowOnFullInventory = true;
    private static volatile long maxStackCapacity = Constants.DEFAULT_MAX_STACK_CAPACITY;

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

    /**
     * When {@code true} and {@link #isFullInventoryReplacement()} is enabled, the player can
     * deposit and withdraw items from their Vault anywhere (not only at Rest Points).
     */
    public static boolean isAllowRemoteVaultInFullInventory() {
        return allowRemoteVaultInFullInventory;
    }

    /**
     * When {@code true}, world pickups that do not fit in a full inventory are routed into the Vault.
     */
    public static boolean isOverflowOnFullInventory() {
        return overflowOnFullInventory;
    }

    /**
     * Maximum count stored per unique {@link com.soulsvault.core.ItemKey} inside a Vault.
     */
    public static long getMaxStackCapacity() {
        return maxStackCapacity;
    }

    /**
     * Applies values from the loader's config backend (on load / reload) or from the server's
     * config sync packet (on clients).
     */
    public static void bindValues(
            boolean fullInvReplacement,
            boolean remoteVaultInFullInv,
            boolean overflowOnFull,
            long maxCapacity
    ) {
        fullInventoryReplacement = fullInvReplacement;
        allowRemoteVaultInFullInventory = remoteVaultInFullInv;
        overflowOnFullInventory = overflowOnFull;
        maxStackCapacity = Math.max(1L, maxCapacity);
    }
}
