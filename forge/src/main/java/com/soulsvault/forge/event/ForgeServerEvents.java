package com.soulsvault.forge.event;

import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.core.AttunementCommands;
import com.soulsvault.core.EquipLoad;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.ReplenishCaps;
import com.soulsvault.core.VaultAccess;
import com.soulsvault.core.VaultCategory;
import com.soulsvault.core.VaultSync;
import com.soulsvault.core.rest.RestPointProfile;
import com.soulsvault.core.rest.RestPointProfiles;
import com.soulsvault.forge.network.ForgeNetworkHandler;
import com.soulsvault.network.RequestThrottle;
import com.soulsvault.party.CovenantService;
import com.soulsvault.party.PartyCommands;
import com.soulsvault.party.PartyService;
import com.soulsvault.party.ProgressionSync;
import com.soulsvault.platform.Services;
import com.soulsvault.world.block.BottomlessCasketBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import javax.annotation.Nullable;

/**
 * Server/gameplay event listeners: Rest Point access, Rest &amp; Replenish, world pickup overflow, the combat lock,
 * Equip Load, party friendly fire / advancement sharing / HUD sync, datapack loaders and {@code /casket} commands.
 */
public final class ForgeServerEvents {
    private static final int EQUIP_LOAD_REFRESH_TICKS = 20;

    private ForgeServerEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onRightClickBlock);
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onEntityItemPickup);
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onPlayerWakeUp);
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onPlayerLoggedIn);
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onPlayerLoggedOut);
        // Friendly fire runs first so Epic Fight (default priority) never stuns, guards or hit-stops on an ally
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ForgeServerEvents::onPlayerAttackEntity);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ForgeServerEvents::onLivingAttack);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ForgeServerEvents::onLivingHurtFriendlyFire);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, ForgeServerEvents::onLivingDamageFriendlyFire);
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onLivingHurt);
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onChangeTarget);
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onAdvancementEarned);
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onEquipmentChange);
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onPlayerTick);
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onServerTick);
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onServerStopped);
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onAddReloadListeners);
        MinecraftForge.EVENT_BUS.addListener(ForgeServerEvents::onRegisterCommands);
    }

    /**
     * Sends the server-authoritative config to the joining client and syncs their party / Equip Load state.
     */
    private static void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ForgeNetworkHandler.sendConfigSync(serverPlayer);
            Services.PLATFORM.getPlayerVaultData(serverPlayer);
            PartyService.forceSync(serverPlayer);
            EquipLoad.forget(serverPlayer.getUUID());
            EquipLoad.update(serverPlayer);
            ProgressionSync.onLogin(serverPlayer);
        }
    }

    /**
     * Drops the player's per-session state (sync bookkeeping, combat timer, request cooldowns, party caches).
     */
    private static void onPlayerLoggedOut(final PlayerEvent.PlayerLoggedOutEvent event) {
        RequestThrottle.forget(event.getEntity().getUUID());
        VaultSync.forget(event.getEntity().getUUID());
        VaultAccess.forget(event.getEntity().getUUID());
        EquipLoad.forget(event.getEntity().getUUID());
        PartyService.forget(event.getEntity().getUUID());
        CovenantService.forget(event.getEntity().getUUID());
    }

    /**
     * Handles Shift + Right-Click on any Rest Point (a block with a Rest Point profile or in
     * {@code #soulsvault:rest_points}): rests per the block's profile (unless combat-locked) and opens the Vault UI.
     */
    private static void onRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player == null || player.isSpectator() || !player.isShiftKeyDown()) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        RestPointProfile profile = RestPointProfiles.find(state);
        if (profile == null) {
            return;
        }

        if (event.getHand() == InteractionHand.MAIN_HAND && !level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BottomlessCasketBlock.openCasketForPlayer(serverPlayer, pos, profile);
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

        PlayerVaultData vaultData = Services.PLATFORM.getPlayerVaultData(serverPlayer);
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
     * Sleeping through the night counts as resting when {@code restPoints.replenishOnWakeUp} is on.
     */
    private static void onPlayerWakeUp(final PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer) || !SoulsVaultConfig.restPoints().replenishOnWakeUp()) {
            return;
        }
        if (VaultAccess.check(serverPlayer, VaultAccess.Type.REST, true) == null) {
            Services.PLATFORM.getPlayerVaultData(serverPlayer).performRestAndReplenish(serverPlayer);
        }
    }

    /**
     * Friendly fire, melee: vanilla clicks and Epic Fight battle-mode hitboxes both end in {@code Player#attack}, so
     * cancelling here stops the whole swing on an ally (no knockback, sweep, durability loss or Epic Fight hit
     * effects; Epic Fight reports it as a miss).
     */
    private static void onPlayerAttackEntity(final AttackEntityEvent event) {
        Player attacker = event.getEntity();
        if (attacker.level().isClientSide || attacker.getServer() == null) {
            return;
        }
        if (PartyService.shouldPreventHarm(attacker.getServer(), event.getTarget(), attacker)) {
            event.setCanceled(true);
        }
    }

    /**
     * Friendly fire, everything else: projectiles, pets, spells and Epic Fight skills that hurt targets directly.
     */
    private static void onLivingAttack(final LivingAttackEvent event) {
        if (isFriendlyFire(event.getEntity(), event.getSource().getEntity())) {
            event.setCanceled(true);
        }
    }

    /**
     * Fallbacks for mods that skip {@link LivingAttackEvent} and call {@code actuallyHurt} directly.
     */
    private static void onLivingHurtFriendlyFire(final LivingHurtEvent event) {
        if (isFriendlyFire(event.getEntity(), event.getSource().getEntity())) {
            event.setCanceled(true);
        }
    }

    private static void onLivingDamageFriendlyFire(final LivingDamageEvent event) {
        if (isFriendlyFire(event.getEntity(), event.getSource().getEntity())) {
            event.setCanceled(true);
        }
    }

    private static boolean isFriendlyFire(LivingEntity victim, @Nullable Entity attacker) {
        return attacker != null && !victim.level().isClientSide && victim.getServer() != null
                && PartyService.shouldPreventHarm(victim.getServer(), victim, attacker);
    }

    /**
     * Combat lock timer and party HUD "taking damage" flash.
     */
    private static void onLivingHurt(final LivingHurtEvent event) {
        Entity attacker = event.getSource().getEntity();
        if (event.getEntity() instanceof ServerPlayer victim) {
            PartyService.markHurt(victim);
            // Only fights count for the combat lock, not falls, lava or starvation
            if (attacker instanceof LivingEntity && attacker != victim) {
                VaultAccess.markCombat(victim);
            }
        }
        if (attacker instanceof ServerPlayer attackingPlayer && event.getEntity() != attackingPlayer) {
            VaultAccess.markCombat(attackingPlayer);
        }
    }

    /**
     * Stops party members' pets and mounts from even targeting fellow members (or their pets).
     */
    private static void onChangeTarget(final LivingChangeTargetEvent event) {
        LivingEntity mob = event.getEntity();
        LivingEntity target = event.getNewTarget();
        if (target == null || mob.level().isClientSide || mob.getServer() == null) {
            return;
        }
        if (PartyService.shouldPreventHarm(mob.getServer(), target, mob)) {
            event.setCanceled(true);
        }
    }

    private static void onAdvancementEarned(final AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            PartyService.onAdvancementEarned(serverPlayer, event.getAdvancement());
        }
    }

    private static void onEquipmentChange(final LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            EquipLoad.update(serverPlayer);
        }
    }

    /**
     * Periodic Equip Load refresh: Curios changes fire no vanilla equipment event.
     */
    private static void onPlayerTick(final TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer serverPlayer
                && serverPlayer.tickCount % EQUIP_LOAD_REFRESH_TICKS == 0) {
            EquipLoad.update(serverPlayer);
        }
    }

    private static void onServerTick(final TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            PartyService.tick(event.getServer());
            CovenantService.reconcile(event.getServer());
        }
    }

    /**
     * Static caches outlive an integrated server; clear them so the next singleplayer world starts fresh.
     */
    private static void onServerStopped(final ServerStoppedEvent event) {
        PartyService.invalidate();
        VaultCategory.clearCache();
    }

    private static void onAddReloadListeners(final AddReloadListenerEvent event) {
        event.addListener(new ReplenishCaps.Loader());
        event.addListener(new RestPointProfiles.Loader());
        event.addListener(new EquipLoad.Loader());
    }

    private static void onRegisterCommands(final RegisterCommandsEvent event) {
        PartyCommands.register(event.getDispatcher());
        AttunementCommands.register(event.getDispatcher());
    }
}
