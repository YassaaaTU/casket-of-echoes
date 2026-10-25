package com.soulsvault.network;

import com.soulsvault.client.party.ClientPartyCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-to-Client: where the receiver's online teammates in the same dimension stand, twice a second, for the
 * in-world party markers (teammates far away are not tracked by the client otherwise).
 */
public final class PacketS2CPartyPositions {
    public record Entry(UUID member, Vec3 position) {
    }

    private final List<Entry> entries;

    public PacketS2CPartyPositions(List<Entry> entries) {
        this.entries = entries;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.entries.size());
        for (Entry entry : this.entries) {
            buf.writeUUID(entry.member());
            buf.writeDouble(entry.position().x);
            buf.writeDouble(entry.position().y);
            buf.writeDouble(entry.position().z);
        }
    }

    public static PacketS2CPartyPositions decode(FriendlyByteBuf buf) {
        int count = Math.min(256, buf.readVarInt());
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readUUID(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())));
        }
        return new PacketS2CPartyPositions(entries);
    }

    public void handleClient() {
        ClientPartyCache.handlePositions(this.entries);
    }
}
