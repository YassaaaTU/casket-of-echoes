package com.soulsvault.network;

import com.soulsvault.client.ClientVaultCache;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.VaultEntry;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-Client: the player's whole private Keepsakes (a handful of entries), sent when the Casket opens and
 * after every change.
 */
public final class PacketS2CKeepsakes {
    private final boolean unlocked;
    private final boolean upgraded;
    private final List<VaultEntry> entries;

    public PacketS2CKeepsakes(boolean unlocked, boolean upgraded, List<VaultEntry> entries) {
        this.unlocked = unlocked;
        this.upgraded = upgraded;
        this.entries = entries;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.unlocked);
        buf.writeBoolean(this.upgraded);
        buf.writeVarInt(this.entries.size());
        for (VaultEntry entry : this.entries) {
            entry.getKey().toNetwork(buf);
            buf.writeVarLong(entry.getCount());
        }
    }

    public static PacketS2CKeepsakes decode(FriendlyByteBuf buf) {
        boolean unlocked = buf.readBoolean();
        boolean upgraded = buf.readBoolean();
        int count = Math.min(256, buf.readVarInt());
        List<VaultEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new VaultEntry(ItemKey.fromNetwork(buf), buf.readVarLong()));
        }
        return new PacketS2CKeepsakes(unlocked, upgraded, entries);
    }

    public void handleClient() {
        ClientVaultCache.handleKeepsakes(this.unlocked, this.upgraded, this.entries);
    }
}
