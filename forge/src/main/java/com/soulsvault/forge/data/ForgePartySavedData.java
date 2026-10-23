package com.soulsvault.forge.data;

import com.soulsvault.Constants;
import com.soulsvault.party.PartyRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Objects;

/**
 * Built-in parties and per-party options at {@code world/data/soulsvault_parties.dat}.
 */
public final class ForgePartySavedData extends SavedData {
    private static final String FILE_ID = Constants.MOD_ID + "_parties";

    private final PartyRegistry registry = new PartyRegistry();

    private ForgePartySavedData() {
        this.registry.setDirtyListener(this::setDirty);
    }

    public static ForgePartySavedData get(MinecraftServer server) {
        ServerLevel overworld = Objects.requireNonNull(server.overworld(), "Server overworld cannot be null");
        return overworld.getDataStorage().computeIfAbsent(ForgePartySavedData::load, ForgePartySavedData::new, FILE_ID);
    }

    private static ForgePartySavedData load(CompoundTag tag) {
        ForgePartySavedData data = new ForgePartySavedData();
        data.registry.load(tag);
        return data;
    }

    public PartyRegistry getRegistry() {
        return this.registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        return this.registry.save(tag);
    }
}
