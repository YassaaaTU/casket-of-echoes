package com.soulsvault.world.item;

import com.soulsvault.core.Attunement;
import com.soulsvault.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Casket Sigil: consumed to attune the player to their Casket (see {@link Attunement}). Packs give it as a quest
 * reward or gate its recipe behind their own progression.
 */
public class CasketSigilItem extends Item {

    public CasketSigilItem() {
        super(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (Services.PLATFORM.getPlayerVaultData(serverPlayer).isAttuned()) {
            serverPlayer.displayClientMessage(Component.translatable("item.soulsvault.casket_sigil.already")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        Attunement.setAttuned(serverPlayer, true, true);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        serverPlayer.getCooldowns().addCooldown(this, 20);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.soulsvault.casket_sigil.desc").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.soulsvault.casket_sigil.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
