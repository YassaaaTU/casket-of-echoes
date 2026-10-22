package com.soulsvault.platform.services;

import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

public interface IPlatformHelper {

    /**
     * Gets the name of the current platform (e.g., "Forge", "NeoForge", "Fabric").
     */
    String getPlatformName();

    /**
     * Checks if a mod with the given id is loaded.
     */
    boolean isModLoaded(String modId);

    /**
     * Check if the game is currently in a development environment.
     */
    boolean isDevelopmentEnvironment();

    /**
     * Gets the name of the environment type as a string.
     */
    default String getEnvironmentName() {
        return isDevelopmentEnvironment() ? "development" : "production";
    }

    /**
     * Soft-dependency hook for Epic Fight weapon detection.
     */
    default boolean isEpicFightWeapon(ItemStack stack) {
        return false;
    }

    /**
     * Soft-dependency hook for Curios item detection.
     */
    default boolean isCurioItem(ItemStack stack) {
        return false;
    }

    /**
     * Returns additional combat/equipment stat lines for the Item Inspection Pane
     * (e.g., Epic Fight Weight, Impact, and Innate Skill).
     */
    default List<Component> getExtraInspectionStats(ItemStack stack) {
        return Collections.emptyList();
    }

    /**
     * Retrieves the server-side persisted PlayerVaultData for the given player
     * from the Overworld DimensionDataStorage.
     */
    PlayerVaultData getPlayerVaultData(ServerPlayer player);

    /**
     * Marks the player's SavedData instance dirty on the Overworld DimensionDataStorage.
     */
    void markVaultDirty(ServerPlayer player);

    /**
     * Sends a C2S packet from the client to the server.
     */
    void sendToServer(Object packet);

    /**
     * Sends an S2C packet from the server to a specific player.
     */
    void sendToClient(ServerPlayer player, Object packet);

    /**
     * Synchronizes the player's entire vault in chunks of at most 100 VaultEntry records per packet.
     */
    void syncFullVaultToClient(ServerPlayer player, PlayerVaultData vaultData);

    /**
     * Sends a single-entry delta update to the client (or null entry if removed).
     */
    void sendVaultDelta(ServerPlayer player, ItemKey key, @Nullable VaultEntry updatedEntry);

    /**
     * Sends an overflow/replenish Souls toast notification to the client.
     */
    void sendOverflowToast(ServerPlayer player, ItemStack displayStack, int countSentToVault, boolean isReplenish);
}
