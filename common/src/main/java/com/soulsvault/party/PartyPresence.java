package com.soulsvault.party;

import com.soulsvault.core.ItemKey;
import com.soulsvault.network.PacketS2CPartyEquipment;
import com.soulsvault.network.PacketS2CPartyPositions;
import com.soulsvault.platform.Services;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where teammates are and what they wear, for the party HUD portraits, the in-world party markers and Xaero's maps.
 * Equipment is sent per viewer only when a teammate's gear changes (durability ticks excluded). Positions of all
 * online teammates (any dimension, spectators left out) are checked every 250 ms, like Xaero's own tracker, and sent
 * only when they differ from what the viewer last received.
 */
public final class PartyPresence {
    private static final int EQUIPMENT_INTERVAL_TICKS = 5;
    private static final int POSITION_INTERVAL_TICKS = 5;
    /** Stacks whose NBT is larger than this (as SNBT characters) are sent without it; the portrait only needs the item. */
    private static final int MAX_SENT_NBT_CHARS = 16384;
    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    /** viewer -> teammate -> equipment signature last sent. */
    private static final Map<UUID, Map<UUID, Integer>> SENT_EQUIPMENT = new ConcurrentHashMap<>();
    /** viewer -> positions last sent. */
    private static final Map<UUID, List<PacketS2CPartyPositions.Entry>> SENT_POSITIONS = new ConcurrentHashMap<>();

    private PartyPresence() {
    }

    public static void forget(UUID viewer) {
        SENT_EQUIPMENT.remove(viewer);
        SENT_POSITIONS.remove(viewer);
    }

    public static void tick(MinecraftServer server) {
        int tick = server.getTickCount();
        boolean equipment = tick % EQUIPMENT_INTERVAL_TICKS == 0;
        boolean positions = tick % POSITION_INTERVAL_TICKS == 0;
        if (!equipment && !positions) {
            return;
        }
        Map<UUID, Integer> signatures = new HashMap<>();
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            PartyView party = PartyService.getParty(server, viewer.getUUID());
            if (party == null) {
                if (positions) {
                    sendPositions(viewer, List.of());
                }
                continue;
            }
            List<PacketS2CPartyPositions.Entry> tracked = new ArrayList<>();
            for (UUID memberId : party.members()) {
                if (memberId.equals(viewer.getUUID())) {
                    continue;
                }
                ServerPlayer member = server.getPlayerList().getPlayer(memberId);
                if (member == null) {
                    continue;
                }
                if (equipment) {
                    int signature = signatures.computeIfAbsent(memberId, id -> signature(member));
                    Map<UUID, Integer> sent = SENT_EQUIPMENT.computeIfAbsent(viewer.getUUID(), id -> new ConcurrentHashMap<>());
                    Integer previous = sent.get(memberId);
                    if (previous == null || previous != signature) {
                        sent.put(memberId, signature);
                        Services.PLATFORM.sendToClient(viewer, new PacketS2CPartyEquipment(memberId, equipmentOf(member)));
                    }
                }
                if (positions && !member.isSpectator()) {
                    tracked.add(new PacketS2CPartyPositions.Entry(memberId, member.level().dimension(), member.position()));
                }
            }
            if (positions) {
                sendPositions(viewer, tracked);
            }
        }
    }

    /**
     * Sends the viewer's teammate positions unless they are exactly what was sent last time (nobody moved), so idle
     * parties cost nothing. An empty list is sent once to clear what the client tracks.
     */
    private static void sendPositions(ServerPlayer viewer, List<PacketS2CPartyPositions.Entry> entries) {
        List<PacketS2CPartyPositions.Entry> previous = SENT_POSITIONS.get(viewer.getUUID());
        if (previous == null ? entries.isEmpty() : previous.equals(entries)) {
            return;
        }
        SENT_POSITIONS.put(viewer.getUUID(), List.copyOf(entries));
        Services.PLATFORM.sendToClient(viewer, new PacketS2CPartyPositions(entries));
    }

    private static int signature(ServerPlayer member) {
        int hash = 1;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = member.getItemBySlot(slot).copy();
            if (stack.isDamageableItem()) {
                stack.setDamageValue(0);
            }
            hash = 31 * hash + ItemKey.fromStack(stack).hashCode();
        }
        return hash;
    }

    private static List<ItemStack> equipmentOf(ServerPlayer member) {
        List<ItemStack> stacks = new ArrayList<>(SLOTS.length);
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = member.getItemBySlot(slot).copyWithCount(1);
            if (stack.hasTag() && stack.getTag().toString().length() > MAX_SENT_NBT_CHARS) {
                stack.setTag(null);
            }
            stacks.add(stack);
        }
        return stacks;
    }
}
