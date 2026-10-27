package com.soulsvault.network;

import com.soulsvault.client.ClientVaultCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

/**
 * Server-to-Client notification packet triggering the dark Souls-style HUD banner
 * when items overflow into the Vault on pickup or replenish on Rest.
 */
public final class PacketS2COverflowToast {
    private final ItemStack displayStack;
    private final int countSentToVault;
    private final boolean isReplenish;

    public PacketS2COverflowToast(ItemStack displayStack, int countSentToVault, boolean isReplenish) {
        this.displayStack = displayStack != null ? displayStack.copyWithCount(1) : ItemStack.EMPTY;
        this.countSentToVault = Math.max(0, countSentToVault);
        this.isReplenish = isReplenish;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeItem(this.displayStack);
        buf.writeVarInt(this.countSentToVault);
        buf.writeBoolean(this.isReplenish);
    }

    public static PacketS2COverflowToast decode(FriendlyByteBuf buf) {
        ItemStack stack = buf.readItem();
        int count = buf.readVarInt();
        boolean isReplenish = buf.readBoolean();
        return new PacketS2COverflowToast(stack, count, isReplenish);
    }

    public void handleClient() {
        ClientVaultCache.handleOverflowToast(this.displayStack, this.countSentToVault, this.isReplenish);
    }
}
