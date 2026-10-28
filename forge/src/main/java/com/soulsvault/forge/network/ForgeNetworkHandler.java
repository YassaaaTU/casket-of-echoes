package com.soulsvault.forge.network;

import com.soulsvault.Constants;
import com.soulsvault.core.BuildEcho;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.KeepsakeSync;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultEntry;
import com.soulsvault.core.VaultMeta;
import com.soulsvault.network.PacketC2SEchoEdit;
import com.soulsvault.network.PacketC2SFillCraftingGrid;
import com.soulsvault.network.PacketC2SOpenVault;
import com.soulsvault.network.PacketC2SPartyAction;
import com.soulsvault.network.PacketC2SVaultAction;
import com.soulsvault.network.PacketS2CEquipLoad;
import com.soulsvault.network.PacketS2CKeepsakes;
import com.soulsvault.network.PacketS2CPartyEquipment;
import com.soulsvault.network.PacketS2CPartyPositions;
import com.soulsvault.network.PacketS2COpenPartyScreen;
import com.soulsvault.network.PacketS2COverflowToast;
import com.soulsvault.network.PacketS2CPartySync;
import com.soulsvault.network.PacketS2CSyncConfig;
import com.soulsvault.network.PacketS2CVaultDelta;
import com.soulsvault.network.PacketS2CVaultSyncChunk;
import com.soulsvault.party.PartyService;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Forge {@link SimpleChannel} networking for Casket of Echoes: chunked vault synchronization (at most
 * {@link Constants#MAX_SYNC_CHUNK_SIZE} entries and well under the custom payload size limit per packet) and
 * single-entry delta updates.
 * <p>
 * Every handler runs on the game thread ({@code enqueueWork}); serverbound handlers receive the sending player and
 * validate everything themselves.
 */
public final class ForgeNetworkHandler {
    private static final String PROTOCOL_VERSION = "1";
    /**
     * Byte budget for one vault sync chunk, half of the 1 MiB custom payload limit, so stacks with large NBT (skins,
     * affixes) spread over more packets instead of disconnecting the player.
     */
    private static final int MAX_CHUNK_BYTES = 512 * 1024;
    /** Approximate wire size of a {@link VaultEntry} besides its NBT (item id, count, flags, timestamp). */
    private static final int ENTRY_OVERHEAD_BYTES = 32;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Constants.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;
    private static boolean initialized = false;

    private ForgeNetworkHandler() {
    }

    /**
     * Registers every packet once. Ids follow registration order, so the order below is part of the protocol:
     * append new packets at the end and bump {@code PROTOCOL_VERSION} when anything changes.
     */
    public static synchronized void register() {
        if (initialized) {
            return;
        }
        initialized = true;

        registerServerbound(PacketC2SOpenVault.class, PacketC2SOpenVault::encode, PacketC2SOpenVault::decode,
                PacketC2SOpenVault::handleServer);
        registerClientbound(PacketS2CVaultSyncChunk.class, PacketS2CVaultSyncChunk::encode, PacketS2CVaultSyncChunk::decode,
                PacketS2CVaultSyncChunk::handleClient);
        registerServerbound(PacketC2SVaultAction.class, PacketC2SVaultAction::encode, PacketC2SVaultAction::decode,
                PacketC2SVaultAction::handleServer);
        registerClientbound(PacketS2CVaultDelta.class, PacketS2CVaultDelta::encode, PacketS2CVaultDelta::decode,
                PacketS2CVaultDelta::handleClient);
        registerClientbound(PacketS2COverflowToast.class, PacketS2COverflowToast::encode, PacketS2COverflowToast::decode,
                PacketS2COverflowToast::handleClient);
        registerClientbound(PacketS2CSyncConfig.class, PacketS2CSyncConfig::encode, PacketS2CSyncConfig::decode,
                PacketS2CSyncConfig::handleClient);
        registerServerbound(PacketC2SFillCraftingGrid.class, PacketC2SFillCraftingGrid::encode,
                PacketC2SFillCraftingGrid::decode, PacketC2SFillCraftingGrid::handleServer);
        registerServerbound(PacketC2SEchoEdit.class, PacketC2SEchoEdit::encode, PacketC2SEchoEdit::decode,
                PacketC2SEchoEdit::handleServer);
        registerServerbound(PacketC2SPartyAction.class, PacketC2SPartyAction::encode, PacketC2SPartyAction::decode,
                PacketC2SPartyAction::handleServer);
        registerClientbound(PacketS2CPartySync.class, PacketS2CPartySync::encode, PacketS2CPartySync::decode,
                PacketS2CPartySync::handleClient);
        registerClientbound(PacketS2COpenPartyScreen.class, PacketS2COpenPartyScreen::encode, PacketS2COpenPartyScreen::decode,
                PacketS2COpenPartyScreen::handleClient);
        registerClientbound(PacketS2CEquipLoad.class, PacketS2CEquipLoad::encode, PacketS2CEquipLoad::decode,
                PacketS2CEquipLoad::handleClient);
        registerClientbound(PacketS2CPartyEquipment.class, PacketS2CPartyEquipment::encode, PacketS2CPartyEquipment::decode,
                PacketS2CPartyEquipment::handleClient);
        registerClientbound(PacketS2CPartyPositions.class, PacketS2CPartyPositions::encode, PacketS2CPartyPositions::decode,
                PacketS2CPartyPositions::handleClient);
        registerClientbound(PacketS2CKeepsakes.class, PacketS2CKeepsakes::encode, PacketS2CKeepsakes::decode,
                PacketS2CKeepsakes::handleClient);
    }

    private static <T> void registerServerbound(
            Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder,
            BiConsumer<T, ServerPlayer> handler
    ) {
        CHANNEL.registerMessage(packetId++, type, encoder, decoder, (msg, ctxSupplier) -> {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> {
                ServerPlayer sender = ctx.getSender();
                if (sender != null) {
                    handler.accept(msg, sender);
                }
            });
            ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    private static <T> void registerClientbound(
            Class<T> type,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder,
            Consumer<T> handler
    ) {
        CHANNEL.registerMessage(packetId++, type, encoder, decoder, (msg, ctxSupplier) -> {
            NetworkEvent.Context ctx = ctxSupplier.get();
            ctx.enqueueWork(() -> handler.accept(msg));
            ctx.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /**
     * Client side: sends a registered serverbound packet.
     */
    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    /**
     * Sends a registered clientbound packet to one player.
     */
    public static void sendToClient(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /**
     * Sends the server-authoritative config snapshot to one player (on login).
     */
    public static void sendConfigSync(ServerPlayer player) {
        sendToClient(player, PacketS2CSyncConfig.fromCurrentConfig());
    }

    /**
     * Pushes the server-authoritative config snapshot to every connected player (after a config reload).
     */
    public static void broadcastConfigSync() {
        CHANNEL.send(PacketDistributor.ALL.noArg(), PacketS2CSyncConfig.fromCurrentConfig());
    }

    /**
     * Sends the player's whole Casket (entries, Build Echoes, capacity meta) plus their Keepsakes. The first chunk
     * replaces the client's copy and carries the echoes; later chunks append entries.
     */
    public static void syncFullVaultToClient(ServerPlayer player, PlayerVaultData vaultData) {
        List<BuildEcho> echoes = vaultData.getBuildEchoes();
        String partyName = PartyService.sharedPartyName(player.server, player.getUUID());
        VaultMeta meta = vaultData.createMeta(partyName != null ? partyName : "");
        KeepsakeSync.send(player);

        List<List<VaultEntry>> chunks = splitIntoChunks(vaultData.getEntries(), encodedSize(echoes));
        for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex++) {
            List<BuildEcho> chunkEchoes = chunkIndex == 0 ? echoes : List.of();
            sendToClient(player, new PacketS2CVaultSyncChunk(chunkIndex, chunks.size(), chunks.get(chunkIndex), chunkEchoes, meta));
        }
    }

    /**
     * Splits entries into chunks of at most {@link Constants#MAX_SYNC_CHUNK_SIZE} entries and about
     * {@link #MAX_CHUNK_BYTES} bytes each. Always returns at least one (possibly empty) chunk.
     *
     * @param firstChunkBytes bytes the first chunk already carries (its Build Echoes)
     */
    private static List<List<VaultEntry>> splitIntoChunks(Iterable<VaultEntry> entries, int firstChunkBytes) {
        List<List<VaultEntry>> chunks = new ArrayList<>();
        List<VaultEntry> current = new ArrayList<>();
        long bytes = firstChunkBytes;
        for (VaultEntry entry : entries) {
            long size = ENTRY_OVERHEAD_BYTES + (long) entry.getKey().getNbtSizeBytes();
            boolean countFull = current.size() >= Constants.MAX_SYNC_CHUNK_SIZE;
            // An oversized first chunk may be sent empty (echoes only); later chunks always carry an entry
            boolean bytesFull = bytes + size > MAX_CHUNK_BYTES && (!current.isEmpty() || (chunks.isEmpty() && bytes > 0));
            if (countFull || bytesFull) {
                chunks.add(current);
                current = new ArrayList<>();
                bytes = 0L;
            }
            current.add(entry);
            bytes += size;
        }
        chunks.add(current);
        return chunks;
    }

    private static int encodedSize(List<BuildEcho> echoes) {
        if (echoes.isEmpty()) {
            return 0;
        }
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            for (BuildEcho echo : echoes) {
                echo.toNetwork(buf);
            }
            return buf.writerIndex();
        } finally {
            buf.release();
        }
    }

    /**
     * Sends one entry update ({@code null} removes the entry on the client).
     */
    public static void sendVaultDelta(ServerPlayer player, ItemKey key, @Nullable VaultEntry updatedEntry) {
        sendToClient(player, new PacketS2CVaultDelta(key, updatedEntry));
    }

    /**
     * Sends the overflow / replenish banner notification.
     */
    public static void sendOverflowToast(
            ServerPlayer player,
            ItemStack displayStack,
            int countSentToVault,
            boolean isReplenish
    ) {
        sendToClient(player, new PacketS2COverflowToast(displayStack, countSentToVault, isReplenish));
    }
}
