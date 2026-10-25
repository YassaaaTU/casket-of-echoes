package com.soulsvault.network;

import com.soulsvault.client.party.ClientPartyCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-to-Client: a teammate's worn armor and held items for the party HUD portrait, sent only when they change.
 * Order: head, chest, legs, feet, main hand, off hand.
 */
public final class PacketS2CPartyEquipment {
    public static final int SLOTS = 6;

    private final UUID member;
    private final List<ItemStack> equipment;

    public PacketS2CPartyEquipment(UUID member, List<ItemStack> equipment) {
        this.member = member;
        this.equipment = equipment;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.member);
        for (int i = 0; i < SLOTS; i++) {
            buf.writeItem(i < this.equipment.size() ? this.equipment.get(i) : ItemStack.EMPTY);
        }
    }

    public static PacketS2CPartyEquipment decode(FriendlyByteBuf buf) {
        UUID member = buf.readUUID();
        List<ItemStack> equipment = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) {
            equipment.add(buf.readItem());
        }
        return new PacketS2CPartyEquipment(member, equipment);
    }

    public void handleClient() {
        ClientPartyCache.handleEquipment(this.member, this.equipment);
    }
}
