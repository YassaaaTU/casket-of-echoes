package com.soulsvault.platform.services;

import com.soulsvault.core.AccessorySlotAccess;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultAccess;
import com.soulsvault.core.VaultEntry;
import com.soulsvault.core.VaultStorage;
import com.soulsvault.core.rest.RestPointProfile;
import com.soulsvault.party.PartyRegistry;
import com.soulsvault.world.inventory.SoulsVaultMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
     * Captures all equipped Curios slot items for a BuildEcho snapshot (soft-dependency on Curios API).
     */
    default Map<String, List<ItemKey>> captureCurioSlots(Player player) {
        return Collections.emptyMap();
    }

    /**
     * Atomically swaps equipped Curios slot items with the player's Vault for a BuildEcho preset.
     */
    default void swapCurioSlots(
            ServerPlayer player,
            PlayerVaultData vaultData,
            Map<String, List<ItemKey>> desiredCurios,
            Set<ItemKey> modifiedKeys
    ) {
    }

    /**
     * Returns all live Curios & Aether accessory slots (or cosmetic Curios slots) for the given player.
     */
    default List<AccessorySlotAccess> getAccessorySlots(Player player, boolean cosmetic) {
        return Collections.emptyList();
    }

    /**
     * Returns the 4 Cosmetic Armor Reworked slots if {@code cosmeticarmorreworked} is installed.
     */
    default List<AccessorySlotAccess> getCosmeticArmorSlots(Player player) {
        return Collections.emptyList();
    }

    /**
     * Triggers the Waystones inventory button action if {@code waystones} is loaded.
     */
    default boolean triggerWaystonesButton() {
        return false;
    }

    /**
     * Returns the registered MenuType for {@link SoulsVaultMenu}.
     */
    MenuType<SoulsVaultMenu> getVaultMenuType();

    /**
     * Opens the server-synchronized {@link SoulsVaultMenu} for the player and syncs their Vault contents.
     */
    void openVaultMenu(ServerPlayer player, RestPointProfile.Grants grants);

    /**
     * Spawns a 60-second temporary SpectralCasketEntity in front of the player.
     */
    default boolean spawnSpectralCasket(ServerLevel level, Vec3 pos, ServerPlayer owner) {
        return false;
    }

    /**
     * Retrieves the server-side persisted PlayerVaultData for the given player from the Overworld
     * DimensionDataStorage, bound to their party's shared Casket storage when the party shares one.
     */
    default PlayerVaultData getPlayerVaultData(ServerPlayer player) {
        return getPlayerVaultData(player.server, player.getUUID());
    }

    /**
     * Same as {@link #getPlayerVaultData(ServerPlayer)} by id, so it also works for offline players.
     */
    PlayerVaultData getPlayerVaultData(MinecraftServer server, UUID playerId);

    /**
     * A party's shared Casket storage ({@code party:<key>}), created empty on first use and saved separately
     * from any player.
     */
    VaultStorage getSharedStorage(MinecraftServer server, String storageId);

    /**
     * Persistent built-in parties and per-party options.
     */
    PartyRegistry getPartyRegistry(MinecraftServer server);

    /**
     * Vault data marks itself dirty on change; kept for callers that mutate through other paths.
     */
    default void markVaultDirty(ServerPlayer player) {
    }

    /**
     * Fires the loader's cancelable vault access event.
     *
     * @return the denial reason when a listener cancelled it, else {@code null}
     */
    @Nullable
    default Component postVaultAccessEvent(ServerPlayer player, VaultAccess.Type type, boolean atRestPoint) {
        return null;
    }

    /**
     * Fires the loader's cancelable Rest &amp; Replenish "pre" event.
     *
     * @return {@code true} if a listener cancelled the rest
     */
    default boolean postRestReplenishPre(ServerPlayer player) {
        return false;
    }

    /**
     * Fires the loader's per-item refill event, letting listeners change how many items are refilled.
     */
    default int postReplenishRefill(ServerPlayer player, ItemKey key, int amount) {
        return amount;
    }

    default void postRestReplenishPost(ServerPlayer player, int sweptToVault, int refilledToPlayer) {
    }

    /**
     * Epic Fight weight of an item in the given slot (soft dependency), or {@code 0}.
     */
    default double getEpicFightWeight(ItemStack stack, EquipmentSlot slot) {
        return 0.0D;
    }

    /**
     * Value of the {@code soulsvault:equip_load_bonus} attribute.
     */
    default double getEquipLoadBonus(ServerPlayer player) {
        return 0.0D;
    }

    void sendEquipLoad(ServerPlayer player, double load, double capacity);

    /**
     * Gives each listed player every FTB Quests quest any of them completed (soft dependency), or does nothing.
     */
    default void syncQuestProgress(List<ServerPlayer> players) {
    }

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
