package com.soulsvault.world.item;

import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.core.KeepsakeSync;
import com.soulsvault.core.Keepsakes;
import com.soulsvault.core.Milestones;
import com.soulsvault.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Keepsake Coffer: the first one unlocks the player's private Keepsakes, the second deepens them to
 * {@code keepsakes.upgradedStacks} stacks of each item.
 */
public class KeepsakeCofferItem extends Item {

    public KeepsakeCofferItem() {
        super(new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        Keepsakes keepsakes = Services.PLATFORM.getPlayerVaultData(serverPlayer).getKeepsakes();
        Keepsakes.CofferResult result = keepsakes.useCoffer();
        if (result == Keepsakes.CofferResult.MAXED) {
            serverPlayer.displayClientMessage(Component.translatable("item.soulsvault.keepsake_coffer.maxed")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        if (result == Keepsakes.CofferResult.UNLOCKED) {
            Milestones.award(serverPlayer, Milestones.KEEPSAKES_UNLOCKED);
            serverPlayer.displayClientMessage(Component.translatable("item.soulsvault.keepsake_coffer.unlocked",
                    keepsakes.getSlots(), keepsakes.getStacks()).withStyle(ChatFormatting.GOLD), true);
        } else {
            Milestones.award(serverPlayer, Milestones.KEEPSAKES_UPGRADED);
            serverPlayer.displayClientMessage(Component.translatable("item.soulsvault.keepsake_coffer.upgraded",
                    keepsakes.getStacks()).withStyle(ChatFormatting.GOLD), true);
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.CHEST_LOCKED, SoundSource.PLAYERS, 0.9F, 0.8F);
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.1F);
        KeepsakeSync.send(serverPlayer);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        SoulsVaultConfig.Keepsakes config = SoulsVaultConfig.keepsakes();
        tooltip.add(Component.translatable("item.soulsvault.keepsake_coffer.desc", config.slots(), config.baseStacks())
                .withStyle(ChatFormatting.AQUA));
        if (config.upgradedStacks() > config.baseStacks()) {
            tooltip.add(Component.translatable("item.soulsvault.keepsake_coffer.desc_upgrade", config.upgradedStacks())
                    .withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable("item.soulsvault.keepsake_coffer.lore").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
