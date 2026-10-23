package com.soulsvault.network;

import com.soulsvault.config.SoulsVaultConfig;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server-to-Client snapshot of the server-authoritative {@link SoulsVaultConfig} values.
 * <p>
 * Sent on login and whenever the server config is reloaded (file edit, Configured, {@code /reload}-style
 * tooling), so the client never intercepts the inventory key for a Souls Inventory the server would refuse.
 */
public final class PacketS2CSyncConfig {
    private final boolean fullInventoryReplacement;
    private final boolean allowRemoteVaultInFullInventory;
    private final boolean overflowOnFullInventory;
    private final long maxStackCapacity;

    public PacketS2CSyncConfig(
            boolean fullInventoryReplacement,
            boolean allowRemoteVaultInFullInventory,
            boolean overflowOnFullInventory,
            long maxStackCapacity
    ) {
        this.fullInventoryReplacement = fullInventoryReplacement;
        this.allowRemoteVaultInFullInventory = allowRemoteVaultInFullInventory;
        this.overflowOnFullInventory = overflowOnFullInventory;
        this.maxStackCapacity = maxStackCapacity;
    }

    public static PacketS2CSyncConfig fromCurrentConfig() {
        return new PacketS2CSyncConfig(
                SoulsVaultConfig.isFullInventoryReplacement(),
                SoulsVaultConfig.isAllowRemoteVaultInFullInventory(),
                SoulsVaultConfig.isOverflowOnFullInventory(),
                SoulsVaultConfig.getMaxStackCapacity()
        );
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.fullInventoryReplacement);
        buf.writeBoolean(this.allowRemoteVaultInFullInventory);
        buf.writeBoolean(this.overflowOnFullInventory);
        buf.writeVarLong(this.maxStackCapacity);
    }

    public static PacketS2CSyncConfig decode(FriendlyByteBuf buf) {
        boolean fullInv = buf.readBoolean();
        boolean remote = buf.readBoolean();
        boolean overflow = buf.readBoolean();
        long capacity = buf.readVarLong();
        return new PacketS2CSyncConfig(fullInv, remote, overflow, capacity);
    }

    public void handleClient() {
        SoulsVaultConfig.bindValues(
                this.fullInventoryReplacement,
                this.allowRemoteVaultInFullInventory,
                this.overflowOnFullInventory,
                this.maxStackCapacity
        );
    }
}
