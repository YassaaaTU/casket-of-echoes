package com.soulsvault.network;

import com.soulsvault.config.SoulsVaultConfig;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server-to-Client snapshot of the server-authoritative {@link SoulsVaultConfig} settings.
 * <p>
 * Sent on login and whenever the server config is reloaded (file edit, Configured), so the client never
 * intercepts the inventory key for a Souls Inventory the server would refuse, and UI hints (costs, slot counts,
 * party rules) match what the server enforces.
 */
public final class PacketS2CSyncConfig {
    private final SoulsVaultConfig.Settings settings;

    public PacketS2CSyncConfig(SoulsVaultConfig.Settings settings) {
        this.settings = settings;
    }

    /**
     * A snapshot of the settings currently in force on this server.
     */
    public static PacketS2CSyncConfig fromCurrentConfig() {
        return new PacketS2CSyncConfig(SoulsVaultConfig.get());
    }

    public void encode(FriendlyByteBuf buf) {
        this.settings.encode(buf);
    }

    public static PacketS2CSyncConfig decode(FriendlyByteBuf buf) {
        return new PacketS2CSyncConfig(SoulsVaultConfig.Settings.decode(buf));
    }

    public void handleClient() {
        SoulsVaultConfig.apply(this.settings);
    }
}
