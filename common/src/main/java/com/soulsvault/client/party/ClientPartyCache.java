package com.soulsvault.client.party;

import com.soulsvault.client.gui.SoulsVaultScreen;
import com.soulsvault.network.PacketS2CPartyPositions;
import com.soulsvault.party.PartyInvite;
import com.soulsvault.party.PartySnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client copy of the player's party (for the party HUD and party GUI), updated by {@code PacketS2CPartySync}.
 */
public final class ClientPartyCache {
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    @Nullable
    private static volatile PartySnapshot snapshot;
    private static volatile List<PartyInvite> invites = List.of();
    private static volatile long privateCasketItems;
    private static final Map<UUID, List<ItemStack>> EQUIPMENT = new ConcurrentHashMap<>();
    private static final Map<UUID, TrackedMember> TRACKED = new ConcurrentHashMap<>();

    /**
     * A teammate's server-reported position. The same instance lives for as long as the teammate is reported (Xaero's
     * map collectors rebuild their elements whenever the object changes), updated in place and interpolated between
     * reports so markers and map icons glide instead of jumping every 250 ms.
     */
    public static final class TrackedMember {
        private static final long MIN_GLIDE_MILLIS = 50L;
        private static final long MAX_GLIDE_MILLIS = 500L;
        /** Moves longer than this (teleports, respawns) snap instead of gliding across the map. */
        private static final double SNAP_DISTANCE_SQR = 64.0D * 64.0D;

        private record Motion(ResourceKey<Level> dimension, Vec3 from, Vec3 to, long receivedAt, long glideMillis) {
            Vec3 at(long now) {
                if (this.glideMillis <= 0L) {
                    return this.to;
                }
                double t = Math.min(1.0D, Math.max(0.0D, (now - this.receivedAt) / (double) this.glideMillis));
                return this.from.lerp(this.to, t);
            }
        }

        private final UUID id;
        private volatile Motion motion;

        private TrackedMember(UUID id) {
            this.id = id;
        }

        private void report(ResourceKey<Level> dimension, Vec3 position, long now) {
            Motion previous = this.motion;
            if (previous == null || !previous.dimension().equals(dimension)
                    || previous.to().distanceToSqr(position) > SNAP_DISTANCE_SQR) {
                this.motion = new Motion(dimension, position, position, now, 0L);
                return;
            }
            // Glide over the time since the last report (250 ms while moving; reports stop while standing still)
            long glide = Math.max(MIN_GLIDE_MILLIS, Math.min(MAX_GLIDE_MILLIS, now - previous.receivedAt()));
            this.motion = new Motion(dimension, previous.at(now), position, now, glide);
        }

        public UUID id() {
            return this.id;
        }

        public ResourceKey<Level> dimension() {
            return this.motion.dimension();
        }

        /** Interpolated feet position. */
        public Vec3 position() {
            return this.motion.at(System.currentTimeMillis());
        }
    }

    private ClientPartyCache() {
    }

    public static void update(@Nullable PartySnapshot newSnapshot, List<PartyInvite> newInvites, long newPrivateItems) {
        snapshot = newSnapshot;
        invites = List.copyOf(newInvites);
        privateCasketItems = newPrivateItems;
        for (Runnable listener : LISTENERS) {
            listener.run();
        }
    }

    public static void reset() {
        EQUIPMENT.clear();
        TRACKED.clear();
        update(null, List.of(), 0L);
    }

    public static void handleEquipment(UUID member, List<ItemStack> equipment) {
        EQUIPMENT.put(member, List.copyOf(equipment));
    }

    /**
     * Last equipment the server sent for a teammate (head, chest, legs, feet, main hand, off hand), or empty.
     */
    public static List<ItemStack> getEquipment(UUID member) {
        return EQUIPMENT.getOrDefault(member, List.of());
    }

    public static void handlePositions(List<PacketS2CPartyPositions.Entry> entries) {
        long now = System.currentTimeMillis();
        Set<UUID> reported = new HashSet<>();
        for (PacketS2CPartyPositions.Entry entry : entries) {
            reported.add(entry.member());
            TRACKED.computeIfAbsent(entry.member(), TrackedMember::new).report(entry.dimension(), entry.position(), now);
        }
        TRACKED.keySet().retainAll(reported);
    }

    /**
     * Where the server last reported a teammate, or {@code null} when they are offline, spectating or not a teammate.
     */
    @Nullable
    public static TrackedMember getTracked(UUID member) {
        return TRACKED.get(member);
    }

    /**
     * Every teammate the server reports, in any dimension.
     */
    public static Collection<TrackedMember> getTrackedMembers() {
        return TRACKED.values();
    }

    @Nullable
    public static PartySnapshot getSnapshot() {
        return snapshot;
    }

    public static List<PartyInvite> getInvites() {
        return invites;
    }

    /**
     * Items in the player's private Casket (set aside while a shared party Casket is used).
     */
    public static long getPrivateCasketItems() {
        return privateCasketItems;
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    public static void openScreen(@Nullable Screen parent, boolean invitesTab) {
        Minecraft.getInstance().setScreen(new PartyScreen(parent, invitesTab));
    }

    /**
     * Opens the party GUI on the server's request, returning to the Casket afterwards if it was open.
     */
    public static void requestOpenScreen(boolean invitesTab) {
        Minecraft mc = Minecraft.getInstance();
        Screen parent = mc.screen instanceof SoulsVaultScreen ? mc.screen : null;
        openScreen(parent, invitesTab);
    }
}
