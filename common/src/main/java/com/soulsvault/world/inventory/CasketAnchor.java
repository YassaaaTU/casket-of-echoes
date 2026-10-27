package com.soulsvault.world.inventory;

import com.soulsvault.core.rest.RestPointProfile;
import com.soulsvault.core.rest.RestPointProfiles;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * What a Casket session was opened at: a Rest Point block or a Spectral Casket. The menu checks it while open, so the
 * Rest Point's powers lapse once the player walks away, the block is broken or the Spectral Casket fades, instead of
 * lasting for as long as the screen stays open.
 */
public final class CasketAnchor {
    /** Blocks a player may drift past the Rest Point's reach before the session counts as remote. */
    private static final double SLACK_BLOCKS = 2.0D;

    private final ResourceKey<Level> dimension;
    @Nullable
    private final BlockPos pos;
    @Nullable
    private final Block block;
    @Nullable
    private final UUID entityId;
    private final double maxDistanceSqr;

    private CasketAnchor(ResourceKey<Level> dimension, @Nullable BlockPos pos, @Nullable Block block,
                         @Nullable UUID entityId, double reach) {
        this.dimension = dimension;
        this.pos = pos != null ? pos.immutable() : null;
        this.block = block;
        this.entityId = entityId;
        double maxDistance = reach + SLACK_BLOCKS;
        this.maxDistanceSqr = maxDistance * maxDistance;
    }

    /**
     * A Rest Point block, valid while the same block stays there and the player stays within the profile's reach.
     */
    public static CasketAnchor atBlock(Level level, BlockPos pos, RestPointProfile profile) {
        return new CasketAnchor(level.dimension(), pos, level.getBlockState(pos).getBlock(), null,
                RestPointProfiles.reachOf(profile));
    }

    /**
     * An entity such as a Spectral Casket, valid while it exists and the player stays near.
     */
    public static CasketAnchor atEntity(Entity entity, double reach) {
        return new CasketAnchor(entity.level().dimension(), null, null, entity.getUUID(), reach);
    }

    /**
     * Whether the player can still use what the session was opened at.
     */
    public boolean isNear(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (level.dimension() != this.dimension) {
            return false;
        }
        if (this.pos != null) {
            return level.isLoaded(this.pos)
                    && player.distanceToSqr(Vec3.atCenterOf(this.pos)) <= this.maxDistanceSqr
                    && level.getBlockState(this.pos).is(this.block);
        }
        Entity entity = this.entityId != null ? level.getEntity(this.entityId) : null;
        return entity != null && entity.isAlive() && player.distanceToSqr(entity) <= this.maxDistanceSqr;
    }
}
