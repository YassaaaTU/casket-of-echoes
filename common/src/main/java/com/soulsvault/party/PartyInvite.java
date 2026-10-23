package com.soulsvault.party;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * A pending built-in party invitation (kept in memory only; expires after a few minutes).
 *
 * @param policy             the casket policy the inviter proposes (only relevant when the party shares a Casket)
 * @param partySharesCasket  whether joining switches the invitee to the party's shared Casket
 */
public record PartyInvite(
        UUID partyId,
        String partyName,
        UUID inviter,
        String inviterName,
        CasketJoinPolicy policy,
        long expiresAtMillis,
        boolean partySharesCasket
) {
    public static final long LIFETIME_MILLIS = 5L * 60L * 1000L;

    public boolean isExpired() {
        return System.currentTimeMillis() > this.expiresAtMillis;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(this.partyId);
        buf.writeUtf(this.partyName, 64);
        buf.writeUUID(this.inviter);
        buf.writeUtf(this.inviterName, 64);
        buf.writeEnum(this.policy);
        buf.writeLong(this.expiresAtMillis);
        buf.writeBoolean(this.partySharesCasket);
    }

    public static PartyInvite decode(FriendlyByteBuf buf) {
        return new PartyInvite(
                buf.readUUID(),
                buf.readUtf(64),
                buf.readUUID(),
                buf.readUtf(64),
                buf.readEnum(CasketJoinPolicy.class),
                buf.readLong(),
                buf.readBoolean()
        );
    }
}
