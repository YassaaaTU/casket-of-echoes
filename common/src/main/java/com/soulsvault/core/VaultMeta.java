package com.soulsvault.core;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Capacity and sharing details of the Casket a player currently uses, synced with every full vault sync.
 *
 * @param maxPerItem       per-item storage cap (config + expansions)
 * @param maxUniqueEntries distinct-item cap, {@code 0} for unlimited
 * @param uniqueEntries    distinct items currently stored
 * @param echoSlots        Build Echo slots the player has
 * @param shared           whether this is a party's shared Casket
 * @param partyName        the sharing party's name (empty when private)
 * @param setAsideItems    items left in the player's private Casket while they use a shared one
 * @param expansions       Reliquary Shard expansions applied to this Casket
 */
public record VaultMeta(
        long maxPerItem,
        int maxUniqueEntries,
        int uniqueEntries,
        int echoSlots,
        boolean shared,
        String partyName,
        long setAsideItems,
        int expansions
) {
    public static final VaultMeta DEFAULT = new VaultMeta(0L, 0, 0, 0, false, "", 0L, 0);

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarLong(this.maxPerItem);
        buf.writeVarInt(this.maxUniqueEntries);
        buf.writeVarInt(this.uniqueEntries);
        buf.writeVarInt(this.echoSlots);
        buf.writeBoolean(this.shared);
        buf.writeUtf(this.partyName, 128);
        buf.writeVarLong(this.setAsideItems);
        buf.writeVarInt(this.expansions);
    }

    public static VaultMeta decode(FriendlyByteBuf buf) {
        return new VaultMeta(
                buf.readVarLong(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readUtf(128),
                buf.readVarLong(),
                buf.readVarInt()
        );
    }
}
