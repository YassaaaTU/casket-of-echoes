package com.soulsvault.forge.network;

import com.soulsvault.Constants;
import com.soulsvault.core.BuildEcho;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultEntry;
import com.soulsvault.network.PacketC2SFillCraftingGrid;
import com.soulsvault.network.PacketC2SOpenVault;
import com.soulsvault.network.PacketC2SVaultAction;
import com.soulsvault.network.PacketS2COverflowToast;
import com.soulsvault.network.PacketS2CVaultDelta;
import com.soulsvault.network.PacketS2CSyncConfig;
import com.soulsvault.network.PacketS2CVaultSyncChunk;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Forge {@link SimpleChannel} networking handler for Casket of Echoes.
 * Implements chunked vault synchronization (max 100 entries/packet) and single-entry delta updates.
 */
public final class ForgeNetworkHandler {
    private static final String PROTOCOL_VERSION = "2";

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

    public static synchronized void register() {
        if (initialized) {
            return;
        }
        initialized = true;

        CHANNEL.registerMessage(
                packetId++,
                PacketC2SOpenVault.class,
                PacketC2SOpenVault::encode,
                PacketC2SOpenVault::decode,
                ForgeNetworkHandler::handleOpenVault,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                packetId++,
                PacketS2CVaultSyncChunk.class,
                PacketS2CVaultSyncChunk::encode,
                PacketS2CVaultSyncChunk::decode,
                ForgeNetworkHandler::handleSyncChunk,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                PacketC2SVaultAction.class,
                PacketC2SVaultAction::encode,
                PacketC2SVaultAction::decode,
                ForgeNetworkHandler::handleVaultAction,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        CHANNEL.registerMessage(
                packetId++,
                PacketS2CVaultDelta.class,
                PacketS2CVaultDelta::encode,
                PacketS2CVaultDelta::decode,
                ForgeNetworkHandler::handleVaultDelta,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                PacketS2COverflowToast.class,
                PacketS2COverflowToast::encode,
                PacketS2COverflowToast::decode,
                ForgeNetworkHandler::handleOverflowToast,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                PacketS2CSyncConfig.class,
                PacketS2CSyncConfig::encode,
                PacketS2CSyncConfig::decode,
                ForgeNetworkHandler::handleSyncConfig,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );

        CHANNEL.registerMessage(
                packetId++,
                PacketC2SFillCraftingGrid.class,
                PacketC2SFillCraftingGrid::encode,
                PacketC2SFillCraftingGrid::decode,
                ForgeNetworkHandler::handleFillCraftingGrid,
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );
    }

    private static void handleOpenVault(PacketC2SOpenVault msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                msg.handleServer(sender);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void handleVaultAction(PacketC2SVaultAction msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                msg.handleServer(sender);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void handleFillCraftingGrid(PacketC2SFillCraftingGrid msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                msg.handleServer(sender);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static void handleSyncChunk(PacketS2CVaultSyncChunk msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(msg::handleClient);
        ctx.setPacketHandled(true);
    }

    private static void handleVaultDelta(PacketS2CVaultDelta msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(msg::handleClient);
        ctx.setPacketHandled(true);
    }

    private static void handleOverflowToast(PacketS2COverflowToast msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(msg::handleClient);
        ctx.setPacketHandled(true);
    }

    private static void handleSyncConfig(PacketS2CSyncConfig msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(msg::handleClient);
        ctx.setPacketHandled(true);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

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

    public static void syncFullVaultToClient(ServerPlayer player, PlayerVaultData vaultData) {
        List<VaultEntry> allEntries = new ArrayList<>(vaultData.getEntries());
        List<BuildEcho> echoes = vaultData.getBuildEchoes();

        if (allEntries.isEmpty()) {
            sendToClient(player, new PacketS2CVaultSyncChunk(0, 1, Collections.emptyList(), echoes));
            return;
        }

        int chunkSize = Constants.MAX_SYNC_CHUNK_SIZE;
        int totalChunks = (allEntries.size() + chunkSize - 1) / chunkSize;

        for (int chunkIndex = 0; chunkIndex < totalChunks; chunkIndex++) {
            int start = chunkIndex * chunkSize;
            int end = Math.min(start + chunkSize, allEntries.size());
            List<VaultEntry> slice = allEntries.subList(start, end);
            List<BuildEcho> chunkEchoes = (chunkIndex == 0) ? echoes : Collections.emptyList();
            sendToClient(player, new PacketS2CVaultSyncChunk(chunkIndex, totalChunks, slice, chunkEchoes));
        }
    }

    public static void sendVaultDelta(ServerPlayer player, ItemKey key, @Nullable VaultEntry updatedEntry) {
        sendToClient(player, new PacketS2CVaultDelta(key, updatedEntry));
    }

    public static void sendOverflowToast(
            ServerPlayer player,
            ItemStack displayStack,
            int countSentToVault,
            boolean isReplenish
    ) {
        sendToClient(player, new PacketS2COverflowToast(displayStack, countSentToVault, isReplenish));
    }
}
