package com.soulsvault.network;

import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player cooldowns for client requests that make the server do far more work than the request costs to send
 * (a block scan, a full chunked Vault resync) or that reach other players (invites), so a modified client cannot flood
 * the server, its upload or other players' chat with them. The cooldowns are shorter than a player can repeat each
 * request by hand, so only automated spam is dropped.
 */
public final class RequestThrottle {

    /**
     * A throttled kind of request and its cooldown in server ticks.
     */
    public enum Kind {
        /** Opening the Casket: a Rest Point scan plus a full Vault sync. */
        OPEN_VAULT(3),
        /** Build Echo saves and edits, each answered with a full Vault sync. */
        ECHO_UPDATE(4),
        /** Party invites, each a chat message to another player. */
        PARTY_INVITE(20);

        private final int cooldownTicks;

        Kind(int cooldownTicks) {
            this.cooldownTicks = cooldownTicks;
        }
    }

    private static final Map<UUID, int[]> LAST_ACCEPTED = new ConcurrentHashMap<>();

    private RequestThrottle() {
    }

    /**
     * Records the request and returns {@code true}, or returns {@code false} when the player's previous request of
     * this kind was accepted too recently.
     */
    public static boolean tryAcquire(ServerPlayer player, Kind kind) {
        int now = player.server.getTickCount();
        int[] last = LAST_ACCEPTED.computeIfAbsent(player.getUUID(), id -> newHistory());
        int elapsed = now - last[kind.ordinal()];
        // A negative gap means the tick counter restarted (a new singleplayer world)
        if (elapsed >= 0 && elapsed < kind.cooldownTicks) {
            return false;
        }
        last[kind.ordinal()] = now;
        return true;
    }

    public static void forget(UUID playerId) {
        LAST_ACCEPTED.remove(playerId);
    }

    private static int[] newHistory() {
        int[] history = new int[Kind.values().length];
        for (Kind kind : Kind.values()) {
            history[kind.ordinal()] = -kind.cooldownTicks;
        }
        return history;
    }
}
