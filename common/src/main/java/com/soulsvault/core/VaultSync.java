package com.soulsvault.core;

import com.soulsvault.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pushes Casket changes to every online player who sees them. A private Casket has one viewer; a party's shared
 * Casket has every online member, each receiving entries composed with their own pins and refill targets.
 * Persistence is automatic: {@link VaultStorage} and {@link PlayerVaultData} mark their saved data dirty on change.
 */
public final class VaultSync {
    /** Storage each online player was last bound to, maintained by the platform's vault lookup. */
    private static final Map<UUID, String> BINDINGS = new ConcurrentHashMap<>();

    private VaultSync() {
    }

    /**
     * Remembers which storage a player currently uses; called by the platform's vault lookup.
     */
    public static void recordBinding(UUID playerId, String storageId) {
        BINDINGS.put(playerId, storageId);
    }

    /**
     * Drops the player's binding (on logout).
     */
    public static void forget(UUID playerId) {
        BINDINGS.remove(playerId);
    }

    /**
     * Online players currently bound to {@code storageId} (always includes nobody offline).
     */
    public static List<ServerPlayer> viewersOf(MinecraftServer server, String storageId) {
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (storageId.equals(BINDINGS.get(player.getUUID()))) {
                viewers.add(player);
            }
        }
        return viewers;
    }

    /**
     * Sends single-entry deltas for {@code keys} to everyone sharing the actor's Casket storage.
     */
    public static void commit(ServerPlayer actor, Collection<ItemKey> keys) {
        if (keys.isEmpty()) {
            return;
        }
        PlayerVaultData actorVault = Services.PLATFORM.getPlayerVaultData(actor);
        List<ServerPlayer> viewers = viewersOf(actor.server, actorVault.getStorage().getId());
        if (!viewers.contains(actor)) {
            viewers.add(actor);
        }
        for (ServerPlayer viewer : viewers) {
            PlayerVaultData viewerVault = viewer == actor ? actorVault : Services.PLATFORM.getPlayerVaultData(viewer);
            for (ItemKey key : keys) {
                if (!key.isEmpty()) {
                    Services.PLATFORM.sendVaultDelta(viewer, key, viewerVault.getEntry(key));
                }
            }
        }
    }

    /**
     * Sends a delta for a preference-only change (favorite, refill target) to the acting player alone.
     */
    public static void commitPrefs(ServerPlayer actor, ItemKey key) {
        if (!key.isEmpty()) {
            Services.PLATFORM.sendVaultDelta(actor, key, Services.PLATFORM.getPlayerVaultData(actor).getEntry(key));
        }
    }

    /**
     * Full chunked resync (entries, echoes and capacity meta) for everyone sharing {@code storageId}, e.g. after
     * an expansion, a party join or leave.
     */
    public static void resyncViewers(MinecraftServer server, String storageId) {
        for (ServerPlayer viewer : viewersOf(server, storageId)) {
            resync(viewer);
        }
    }

    /**
     * Full chunked resync of one player's Casket.
     */
    public static void resync(ServerPlayer player) {
        Services.PLATFORM.syncFullVaultToClient(player, Services.PLATFORM.getPlayerVaultData(player));
    }
}
