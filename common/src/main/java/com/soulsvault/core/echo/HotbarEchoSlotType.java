package com.soulsvault.core.echo;

import com.soulsvault.Constants;
import com.soulsvault.api.EchoSlotType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Built-in Build Echo slot group for the 9 hotbar slots ("quick items"), restoring full stack sizes.
 * Off by default per Echo, since swapping the hotbar is more invasive than swapping gear.
 */
public final class HotbarEchoSlotType implements EchoSlotType {
    public static final ResourceLocation ID = new ResourceLocation(Constants.MOD_ID, "hotbar");
    public static final HotbarEchoSlotType INSTANCE = new HotbarEchoSlotType();

    private HotbarEchoSlotType() {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Component displayName() {
        return Component.translatable("gui.soulsvault.echo.type.hotbar");
    }

    @Override
    public int slotCount(ServerPlayer player) {
        return Inventory.getSelectionSize();
    }

    @Override
    public ItemStack getStack(ServerPlayer player, int slot) {
        return player.getInventory().getItem(slot);
    }

    @Override
    public void setStack(ServerPlayer player, int slot, ItemStack stack) {
        player.getInventory().setItem(slot, stack);
    }

    @Override
    public boolean enabledByDefault() {
        return false;
    }

    @Override
    public boolean restoresStackSize() {
        return true;
    }
}
