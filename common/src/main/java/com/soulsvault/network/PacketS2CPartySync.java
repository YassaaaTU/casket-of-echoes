package com.soulsvault.network;

import com.soulsvault.client.party.ClientPartyCache;
import com.soulsvault.party.PartyInvite;
import com.soulsvault.party.PartySnapshot;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-to-Client party state for the party HUD and party GUI: the party (or none), pending invites, and how many
 * items the player's private Casket holds (needed for the "empty first" / "override" invite prompts).
 * Sent when anything changed, at most every 5 ticks.
 */
public final class PacketS2CPartySync {
    private static final int MAX_INVITES = 16;

    @Nullable
    private final PartySnapshot snapshot;
    private final List<PartyInvite> invites;
    private final long privateCasketItems;

    public PacketS2CPartySync(@Nullable PartySnapshot snapshot, List<PartyInvite> invites, long privateCasketItems) {
        this.snapshot = snapshot;
        this.invites = List.copyOf(invites.size() > MAX_INVITES ? invites.subList(0, MAX_INVITES) : invites);
        this.privateCasketItems = privateCasketItems;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNullable(this.snapshot, (b, s) -> s.encode(b));
        buf.writeVarInt(this.invites.size());
        for (PartyInvite invite : this.invites) {
            invite.encode(buf);
        }
        buf.writeVarLong(this.privateCasketItems);
    }

    public static PacketS2CPartySync decode(FriendlyByteBuf buf) {
        PartySnapshot snapshot = buf.readNullable(PartySnapshot::decode);
        int count = Math.min(MAX_INVITES, buf.readVarInt());
        List<PartyInvite> invites = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            invites.add(PartyInvite.decode(buf));
        }
        return new PacketS2CPartySync(snapshot, invites, buf.readVarLong());
    }

    public void handleClient() {
        ClientPartyCache.update(this.snapshot, this.invites, this.privateCasketItems);
    }
}
