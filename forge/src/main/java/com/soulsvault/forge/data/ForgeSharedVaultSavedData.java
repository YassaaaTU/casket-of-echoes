package com.soulsvault.forge.data;

import com.soulsvault.Constants;
import com.soulsvault.core.VaultStorage;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Locale;
import java.util.Objects;

/**
 * A party's shared Casket ("Covenant Casket") at {@code world/data/soulsvault_shared_<party>.dat}, kept apart from
 * every member's own file so no player entity ever carries vault NBT.
 */
public final class ForgeSharedVaultSavedData extends SavedData {
    private final VaultStorage storage;

    private ForgeSharedVaultSavedData(String storageId) {
        this.storage = new VaultStorage(storageId, true);
        this.storage.setDirtyListener(this::setDirty);
    }

    /**
     * A party's shared Casket, loaded or created in the Overworld data storage.
     */
    public static ForgeSharedVaultSavedData get(MinecraftServer server, String storageId) {
        ServerLevel overworld = Objects.requireNonNull(server.overworld(), "Server overworld cannot be null");
        return overworld.getDataStorage().computeIfAbsent(
                tag -> load(tag, storageId),
                () -> new ForgeSharedVaultSavedData(storageId),
                fileId(storageId)
        );
    }

    private static ForgeSharedVaultSavedData load(CompoundTag tag, String storageId) {
        ForgeSharedVaultSavedData data = new ForgeSharedVaultSavedData(storageId);
        data.storage.load(tag);
        return data;
    }

    /**
     * File-safe name: sanitized id plus a hash, since vanilla team names may contain any character.
     */
    private static String fileId(String storageId) {
        String safe = storageId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (safe.length() > 80) {
            safe = safe.substring(0, 80);
        }
        return Constants.MOD_ID + "_shared_" + safe + "_" + Integer.toHexString(storageId.hashCode());
    }

    public VaultStorage getStorage() {
        return this.storage;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        return this.storage.save(tag);
    }
}
