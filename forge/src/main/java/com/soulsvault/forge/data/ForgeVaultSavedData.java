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

    public ForgeVaultSavedData() {
        this.vaultData = new PlayerVaultData();
    }

    private ForgeVaultSavedData(PlayerVaultData vaultData) {
        this.vaultData = vaultData;
    }

    public static String getFileId(UUID playerUuid) {
        return Constants.MOD_ID + "_" + playerUuid.toString();
    }

    public static ForgeVaultSavedData get(ServerPlayer player) {
        return get(player.server, player.getUUID());
    }

    public static ForgeVaultSavedData get(MinecraftServer server, UUID playerUuid) {
        ServerLevel overworld = Objects.requireNonNull(server.overworld(), "Server overworld cannot be null");
        DimensionDataStorage storage = overworld.getDataStorage();
        return storage.computeIfAbsent(
                ForgeVaultSavedData::load,
                ForgeVaultSavedData::new,
                getFileId(playerUuid)
        );
    }

    public static ForgeVaultSavedData load(CompoundTag tag) {
        PlayerVaultData data = new PlayerVaultData();
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
