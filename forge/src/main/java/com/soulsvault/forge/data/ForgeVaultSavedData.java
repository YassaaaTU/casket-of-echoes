package com.soulsvault.forge.data;

import com.soulsvault.Constants;
import com.soulsvault.core.PlayerVaultData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.Objects;
import java.util.UUID;

/**
 * Persists a player's {@link PlayerVaultData} inside the Overworld {@link DimensionDataStorage}
 * at {@code world/data/soulsvault_<uuid>.dat}.
 * <p>
 * Strictly obeys the Anti-Book-Ban Rule (Section 1.3): zero vault NBT is ever attached to the
 * Player entity or serialized in entity sync packets.
 */
public final class ForgeVaultSavedData extends SavedData {
    private final PlayerVaultData vaultData;

    public ForgeVaultSavedData(UUID playerUuid) {
        this(new PlayerVaultData(storageId(playerUuid), playerUuid));
    }

    private ForgeVaultSavedData(PlayerVaultData vaultData) {
        this.vaultData = vaultData;
        this.vaultData.setDirtyListener(this::setDirty);
    }

    /**
     * Storage id of a player's private Casket.
     */
    public static String storageId(UUID playerUuid) {
        return "player:" + playerUuid;
    }

    /**
     * Saved data file name ({@code soulsvault_<uuid>}) of a player's Casket.
     */
    public static String getFileId(UUID playerUuid) {
        return Constants.MOD_ID + "_" + playerUuid.toString();
    }

    /**
     * The player's Casket file, loaded or created.
     */
    public static ForgeVaultSavedData get(ServerPlayer player) {
        return get(player.server, player.getUUID());
    }

    /**
     * A player's Casket file by id (works for offline players), loaded or created.
     */
    public static ForgeVaultSavedData get(MinecraftServer server, UUID playerUuid) {
        ServerLevel overworld = Objects.requireNonNull(server.overworld(), "Server overworld cannot be null");
        DimensionDataStorage storage = overworld.getDataStorage();
        return storage.computeIfAbsent(
                tag -> load(tag, playerUuid),
                () -> new ForgeVaultSavedData(playerUuid),
                getFileId(playerUuid)
        );
    }

    public static ForgeVaultSavedData load(CompoundTag tag, UUID playerUuid) {
        PlayerVaultData data = new PlayerVaultData(storageId(playerUuid), playerUuid);
        data.load(tag);
        return new ForgeVaultSavedData(data);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        return this.vaultData.save(tag);
    }

    public PlayerVaultData getVaultData() {
        return this.vaultData;
    }
}
