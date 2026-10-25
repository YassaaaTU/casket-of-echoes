package com.soulsvault.client.party;

import com.soulsvault.client.gui.SoulsVaultScreen;
import com.soulsvault.network.PacketS2CPartyPositions;
import com.soulsvault.party.PartyInvite;
import com.soulsvault.party.PartySnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final Map<UUID, Track> POSITIONS = new ConcurrentHashMap<>();
    /** Position updates arrive every 10 ticks; markers glide between them over this long. */
    private static final long POSITION_LERP_MILLIS = 500L;

    /** A teammate's last two reported positions, so far-away markers move smoothly. */
    private record Track(Vec3 from, Vec3 to, long receivedAt) {
        Vec3 at(long now) {
            double t = Math.min(1.0D, Math.max(0.0D, (now - this.receivedAt) / (double) POSITION_LERP_MILLIS));
            return this.from.lerp(this.to, t);
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
        POSITIONS.clear();
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
        Map<UUID, Track> next = new HashMap<>();
        for (PacketS2CPartyPositions.Entry entry : entries) {
            Track previous = POSITIONS.get(entry.member());
            Vec3 from = previous != null ? previous.at(now) : entry.position();
            next.put(entry.member(), new Track(from, entry.position(), now));
        }
        POSITIONS.clear();
        POSITIONS.putAll(next);
    }

    /**
     * Interpolated server-reported position of a teammate in the player's dimension, or {@code null}.
     */
    @Nullable
    public static Vec3 getReportedPosition(UUID member) {
        Track track = POSITIONS.get(member);
        return track != null ? track.at(System.currentTimeMillis()) : null;
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
