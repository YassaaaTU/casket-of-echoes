package com.soulsvault.network;

import com.soulsvault.client.ClientVaultCache;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server-to-Client Equip Load meter update (sent only when the rounded value changes).
 */
public final class PacketS2CEquipLoad {
    private final double load;
    private final double capacity;

    public PacketS2CEquipLoad(double load, double capacity) {
        this.load = load;
        this.capacity = capacity;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(this.load);
        buf.writeDouble(this.capacity);
    }

    public static PacketS2CEquipLoad decode(FriendlyByteBuf buf) {
        return new PacketS2CEquipLoad(buf.readDouble(), buf.readDouble());
    }

    public void handleClient() {
        ClientVaultCache.handleEquipLoad(this.load, this.capacity);
    }
}
