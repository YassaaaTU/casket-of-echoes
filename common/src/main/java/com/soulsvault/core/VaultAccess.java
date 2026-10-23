package com.soulsvault.core;

import com.soulsvault.Constants;
import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side gate for Casket actions: the bonfire-only rules, the Souls "no resting with enemies nearby" combat
 * lock, and the loader's cancelable access event (so packs and KubeJS can add their own rules, e.g. no Casket
 * inside a boss arena).
 */
public final class VaultAccess {

    public enum Type {
        /** Opening the Casket screen. Never combat-locked, so the inventory stays usable mid-fight. */
        OPEN,
        /** Depositing into / withdrawing from the Vault. */
        VAULT,
        /** Rest &amp; Replenish. */
        REST,
        /** Swapping to a Build Echo. */
        ECHO_SWAP
    }

    private static final Map<UUID, Integer> LAST_COMBAT_TICK = new ConcurrentHashMap<>();

    private VaultAccess() {
    }

    /**
     * Records that the player dealt or took damage (combat lock timer).
     */
    public static void markCombat(ServerPlayer player) {
        LAST_COMBAT_TICK.put(player.getUUID(), player.server.getTickCount());
    }

    public static void forget(UUID playerId) {
        LAST_COMBAT_TICK.remove(playerId);
    }

    /**
     * Returns why the action is denied, or {@code null} when allowed.
     *
     * @param atRestPoint whether the Casket was opened at a Rest Point, Bottomless Casket or Spectral Casket
     */
    @Nullable
    public static Component check(ServerPlayer player, Type type, boolean atRestPoint) {
        SoulsVaultConfig.RestPoints restPoints = SoulsVaultConfig.restPoints();
        if (type == Type.REST && restPoints.replenishRequiresRestPoint() && !atRestPoint) {
            return Component.translatable("gui.soulsvault.notice.rest_point_required");
        }
        if (type == Type.ECHO_SWAP && restPoints.echoSwapRequiresRestPoint() && !atRestPoint) {
            return Component.translatable("gui.soulsvault.notice.echo_rest_point_required");
        }
        if (type != Type.OPEN) {
            Component combat = getCombatReason(player);
            if (combat != null) {
                return combat;
            }
        }
        return Services.PLATFORM.postVaultAccessEvent(player, type, atRestPoint);
    }

    /**
     * Returns why the player counts as "in combat", or {@code null}.
     */
    @Nullable
    public static Component getCombatReason(ServerPlayer player) {
        SoulsVaultConfig.Combat combat = SoulsVaultConfig.combat();
        if (!combat.isEnabled() || player.isCreative() || player.isSpectator()) {
            return null;
        }
        if (combat.lockSecondsAfterDamage() > 0) {
            Integer last = LAST_COMBAT_TICK.get(player.getUUID());
            if (last != null && player.server.getTickCount() - last < combat.lockSecondsAfterDamage() * 20) {
                return Component.translatable("gui.soulsvault.notice.combat_recent");
            }
        }
        if (combat.hostileRadius() > 0.0D) {
            boolean hostileNearby = !player.level().getEntitiesOfClass(
                    Mob.class,
                    player.getBoundingBox().inflate(combat.hostileRadius()),
                    mob -> mob.isAlive()
                            && mob instanceof Enemy
                            && !(mob instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null)
            ).isEmpty();
            if (hostileNearby) {
                return Component.translatable("gui.soulsvault.notice.combat_enemies");
            }
        }
        if (combat.bossRadius() > 0.0D) {
            boolean bossNearby = !player.level().getEntitiesOfClass(
                    LivingEntity.class,
                    player.getBoundingBox().inflate(combat.bossRadius()),
                    entity -> entity.isAlive() && entity.getType().is(Constants.BOSSES_TAG)
            ).isEmpty();
            if (bossNearby) {
                return Component.translatable("gui.soulsvault.notice.combat_boss");
            }
        }
        return null;
    }
}
