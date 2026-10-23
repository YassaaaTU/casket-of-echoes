package com.soulsvault.network;

import com.soulsvault.client.party.ClientPartyCache;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server-to-Client request to open the party GUI (from {@code /casket party gui} or a clicked invite message).
 */
public final class PacketS2COpenPartyScreen {
    private final boolean invitesTab;

    public PacketS2COpenPartyScreen(boolean invitesTab) {
        this.invitesTab = invitesTab;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.invitesTab);
    }

    public static PacketS2COpenPartyScreen decode(FriendlyByteBuf buf) {
        return new PacketS2COpenPartyScreen(buf.readBoolean());
    }

    public void handleClient() {
        ClientPartyCache.requestOpenScreen(this.invitesTab);
    }
}
