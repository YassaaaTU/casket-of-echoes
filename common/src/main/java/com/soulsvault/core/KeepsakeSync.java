package com.soulsvault.core;

import com.soulsvault.network.PacketS2CKeepsakes;
import com.soulsvault.platform.Services;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sends a player their private Keepsakes; they are personal, so there is only ever one viewer.
 */
public final class KeepsakeSync {

    private KeepsakeSync() {
    }

    public static void send(ServerPlayer player) {
        Keepsakes keepsakes = Services.PLATFORM.getPlayerVaultData(player).getKeepsakes();
        Services.PLATFORM.sendToClient(player,
                new PacketS2CKeepsakes(keepsakes.isUnlockedFlag(), keepsakes.isUpgraded(), keepsakes.getEntries()));
    }
}
