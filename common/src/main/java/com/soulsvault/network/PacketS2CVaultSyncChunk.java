package com.soulsvault.network;

import com.soulsvault.Constants;
import com.soulsvault.client.ClientVaultCache;
import com.soulsvault.core.BuildEcho;
import com.soulsvault.core.VaultEntry;
import com.soulsvault.core.VaultMeta;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Server-to-Client chunked vault synchronization packet.
 * Never transmits more than {@link Constants#MAX_SYNC_CHUNK_SIZE} (100) {@link VaultEntry} records per packet; the
 * first chunk of a sync also carries the Build Echoes and replaces the client's copy, later chunks append.
 */
public final class PacketS2CVaultSyncChunk {
    private final int chunkIndex;
    private final int totalChunks;
    private final List<VaultEntry> entries;
    private final List<BuildEcho> echoes;
    private final VaultMeta meta;

    public PacketS2CVaultSyncChunk(
            int chunkIndex,
            int totalChunks,
            List<VaultEntry> entries,
            List<BuildEcho> echoes,
            VaultMeta meta
    ) {
        if (entries != null && entries.size() > Constants.MAX_SYNC_CHUNK_SIZE) {
            throw new IllegalArgumentException(
                    "PacketS2CVaultSyncChunk cannot exceed " + Constants.MAX_SYNC_CHUNK_SIZE + " entries (got " + entries.size() + ")"
            );
        }
        this.chunkIndex = chunkIndex;
        this.totalChunks = Math.max(1, totalChunks);
        this.entries = entries != null ? List.copyOf(entries) : Collections.emptyList();
        this.echoes = echoes != null ? List.copyOf(echoes) : Collections.emptyList();
        this.meta = meta != null ? meta : VaultMeta.DEFAULT;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.chunkIndex);
        buf.writeVarInt(this.totalChunks);

        buf.writeVarInt(this.entries.size());
        for (VaultEntry entry : this.entries) {
            entry.toNetwork(buf);
        }

        buf.writeVarInt(this.echoes.size());
        for (BuildEcho echo : this.echoes) {
            echo.toNetwork(buf);
        }
        this.meta.encode(buf);
    }

    public static PacketS2CVaultSyncChunk decode(FriendlyByteBuf buf) {
        int chunkIndex = buf.readVarInt();
        int totalChunks = buf.readVarInt();

        int entryCount = Math.min(Constants.MAX_SYNC_CHUNK_SIZE, Math.max(0, buf.readVarInt()));
        List<VaultEntry> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            entries.add(VaultEntry.fromNetwork(buf));
        }

        int echoCount = Math.min(Constants.ABSOLUTE_MAX_BUILD_ECHOES, Math.max(0, buf.readVarInt()));
        List<BuildEcho> echoes = new ArrayList<>(echoCount);
        for (int i = 0; i < echoCount; i++) {
            echoes.add(BuildEcho.fromNetwork(buf));
        }

        VaultMeta meta = VaultMeta.decode(buf);
        return new PacketS2CVaultSyncChunk(chunkIndex, totalChunks, entries, echoes, meta);
    }

    public void handleClient() {
        ClientVaultCache.handleSyncChunk(this.chunkIndex, this.totalChunks, this.entries, this.echoes, this.meta);
    }
}
