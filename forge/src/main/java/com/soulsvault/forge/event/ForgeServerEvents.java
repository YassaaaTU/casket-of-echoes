package com.soulsvault.forge.event;

import com.soulsvault.Constants;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.forge.data.ForgeVaultSavedData;
import com.soulsvault.world.block.BottomlessCasketBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;

/**
 * Server/gameplay event listeners for Rest Point access, Rest & Replenish, and world pickup overflow.
 */
public final class ForgeServerEvents {

    private ForgeServerEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onEntityItemPickup);
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onPlayerWakeUp);
    }

    /**
     * Handles Shift + Right-Click on any block in {@code #soulsvault:rest_points}:
     * triggers {@link PlayerVaultData#performRestAndReplenish(ServerPlayer)} and opens the Vault UI.
     */
    private static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player == null || player.isSpectator() || !player.isShiftKeyDown()) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Constants.REST_POINTS_TAG)) {
            return;
        }

        if (event.getHand() == InteractionHand.MAIN_HAND && !level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BottomlessCasketBlock.openCasketForPlayer(serverPlayer, pos);
        }

        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        event.setCanceled(true);
    }

    /**
     * Intercepts world item pickups exceeding the player's configured {@code refillTarget}
     * (when {@code overflowDeposit} is active) or when the player's inventory is full,
     * routing excess items directly into the Vault and dispatching {@code PacketS2COverflowToast}.
     */
    private static void onEntityItemPickup(final EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer) || serverPlayer.isSpectator()) {
            return;
        }

        ItemEntity itemEntity = event.getItem();
        if (itemEntity == null || !itemEntity.isAlive() || itemEntity.hasPickUpDelay()) {
            return;
        }

        ItemStack entityStack = itemEntity.getItem();
        if (entityStack.isEmpty()) {
            return;
        }

        PlayerVaultData vaultData = ForgeVaultSavedData.get(serverPlayer).getVaultData();
        PlayerVaultData.PickupOverflowResult result = vaultData.handleWorldItemPickup(serverPlayer, entityStack);
        if (!result.handled()) {
            return;
        }

        serverPlayer.take(itemEntity, result.totalConsumed());
        serverPlayer.level().playSound(
                null,
                serverPlayer.getX(),
                serverPlayer.getY(),
                serverPlayer.getZ(),
                SoundEvents.ITEM_PICKUP,
                SoundSource.PLAYERS,
                0.2F,
                ((serverPlayer.getRandom().nextFloat() - serverPlayer.getRandom().nextFloat()) * 0.7F + 1.0F) * 2.0F
        );

        if (entityStack.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(entityStack);
        }

        event.setCanceled(true);
    }

    /**
     * Automatically triggers Rest & Replenish when a player wakes up from sleeping.
     */
    private static void onPlayerWakeUp(final PlayerWakeUpEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer && !serverPlayer.level().isClientSide) {
            PlayerVaultData vaultData = ForgeVaultSavedData.get(serverPlayer).getVaultData();
            vaultData.performRestAndReplenish(serverPlayer);
        }
    }
}
