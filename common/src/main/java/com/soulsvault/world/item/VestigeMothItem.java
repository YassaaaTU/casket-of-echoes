package com.soulsvault.world.item;

import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Rare consumable ("Vestige Moth") inspired by Lords of the Fallen Vestige Seeds.
 * Spawns a temporary 60-second {@link com.soulsvault.world.entity.SpectralCasketEntity} in the field,
 * costing XP levels to summon.
 */
public class VestigeMothItem extends Item {

    public VestigeMothItem() {
        this(new Item.Properties().stacksTo(16).rarity(Rarity.RARE));
    }

    public VestigeMothItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (player.isSpectator()) {
            return InteractionResultHolder.pass(stack);
        }

        boolean creative = player.getAbilities().instabuild;
        int xpCost = SoulsVaultConfig.fieldAccess().vestigeMothXpLevelCost();
        if (!creative && player.experienceLevel < xpCost) {
            if (level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("item.soulsvault.vestige_moth.no_xp", xpCost)
                                .withStyle(ChatFormatting.RED),
                        true
                );
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide && level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
            Vec3 look = player.getLookAngle();
            Vec3 horizontalForward = new Vec3(look.x, 0.0D, look.z);
            if (horizontalForward.lengthSqr() > 1.0E-4D) {
                horizontalForward = horizontalForward.normalize().scale(1.75D);
            } else {
                horizontalForward = new Vec3(0.0D, 0.0D, 1.75D);
            }
            Vec3 spawnPos = player.position().add(horizontalForward).add(0.0D, 0.25D, 0.0D);

            boolean spawned = Services.PLATFORM.spawnSpectralCasket(serverLevel, spawnPos, serverPlayer);
            if (spawned) {
                if (!creative) {
                    serverPlayer.giveExperienceLevels(-xpCost);
                    stack.shrink(1);
                }
                serverPlayer.getCooldowns().addCooldown(this, 40);
                serverLevel.playSound(
                        null,
                        serverPlayer.blockPosition(),
                        SoundEvents.BEACON_ACTIVATE,
                        SoundSource.PLAYERS,
                        0.65F,
                        1.35F
                );
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            @Nullable Level level,
            List<Component> tooltipComponents,
            TooltipFlag isAdvanced
    ) {
        tooltipComponents.add(
                Component.translatable(
                        "item.soulsvault.vestige_moth.desc",
                        SoulsVaultConfig.fieldAccess().spectralCasketLifetimeSeconds(),
                        SoulsVaultConfig.fieldAccess().vestigeMothXpLevelCost()
                )
                        .withStyle(ChatFormatting.AQUA)
        );
    }
}
