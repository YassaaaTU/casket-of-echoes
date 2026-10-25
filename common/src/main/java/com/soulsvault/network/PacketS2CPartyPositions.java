package com.soulsvault.network;

import com.soulsvault.client.party.ClientPartyCache;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-to-Client: where each of the receiver's online teammates stands, in whatever dimension, for the in-world
 * party markers and Xaero's Minimap / World Map. Sent at most four times a second and only when something changed;
 * every packet is the full list, so a teammate missing from it is no longer tracked.
 */
public final class PacketS2CPartyPositions {
    public record Entry(UUID member, ResourceKey<Level> dimension, Vec3 position) {
    }

    private final List<Entry> entries;

    public PacketS2CPartyPositions(List<Entry> entries) {
        this.entries = entries;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entries.size());
        for (Entry entry : this.entries) {
            buf.writeUUID(entry.member());
            buf.writeResourceKey(entry.dimension());
            buf.writeDouble(entry.position().x);
            buf.writeDouble(entry.position().y);
            buf.writeDouble(entry.position().z);
        }
    }

    public static PacketS2CPartyPositions decode(FriendlyByteBuf buf) {
        int count = Math.min(256, buf.readVarInt());
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID member = buf.readUUID();
            ResourceKey<Level> dimension = buf.readResourceKey(Registries.DIMENSION);
            entries.add(new Entry(member, dimension, new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));
        }
        return new PacketS2CPartyPositions(entries);
    }

    public void handleClient() {
        ClientPartyCache.handlePositions(this.entries);
    }
}
