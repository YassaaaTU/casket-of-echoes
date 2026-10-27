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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * What a Casket session was opened at: a Rest Point block or a Spectral Casket. The menu checks it while open, so the
 * Rest Point's powers lapse once the player walks away, the block stops being a Rest Point (broken, or a campfire put
 * out) or the Spectral Casket fades, instead of lasting for as long as the screen stays open.
 */
public final class CasketAnchor {
    /** Blocks a player may drift past the Rest Point's reach before the session counts as remote. */
    private static final double SLACK_BLOCKS = 2.0D;

    private final ResourceKey<Level> dimension;
    @Nullable
    private final BlockPos pos;
    @Nullable
    private final Block block;
    /**
     * Whether the block was a Rest Point (profile or tag) when the session opened. The Bottomless Casket always opens
     * as one, even when a pack removes it from both; such a session only needs the block to stay.
     */
    private final boolean restPointAtOpen;
    @Nullable
    private final UUID entityId;
    private final double maxDistanceSqr;

    private CasketAnchor(ResourceKey<Level> dimension, @Nullable BlockPos pos, @Nullable Block block,
                         boolean restPointAtOpen, @Nullable UUID entityId, double reach) {
        this.dimension = dimension;
        this.pos = pos != null ? pos.immutable() : null;
        this.block = block;
        this.restPointAtOpen = restPointAtOpen;
        this.entityId = entityId;
        double maxDistance = reach + SLACK_BLOCKS;
        this.maxDistanceSqr = maxDistance * maxDistance;
    }

    /**
     * A Rest Point block, valid while it is still a Rest Point and the player stays within the profile's reach.
     */
    public static CasketAnchor atBlock(Level level, BlockPos pos, RestPointProfile profile) {
        BlockState state = level.getBlockState(pos);
        return new CasketAnchor(level.dimension(), pos, state.getBlock(), RestPointProfiles.find(state) != null, null,
                RestPointProfiles.reachOf(profile));
    }

    /**
     * An entity such as a Spectral Casket, valid while it exists and the player stays near.
     */
    public static CasketAnchor atEntity(Entity entity, double reach) {
        return new CasketAnchor(entity.level().dimension(), null, null, false, entity.getUUID(), reach);
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
            if (!level.isLoaded(this.pos) || player.distanceToSqr(Vec3.atCenterOf(this.pos)) > this.maxDistanceSqr) {
                return false;
            }
            BlockState state = level.getBlockState(this.pos);
            return state.is(this.block) && (!this.restPointAtOpen || RestPointProfiles.find(state) != null);
        }
        Entity entity = this.entityId != null ? level.getEntity(this.entityId) : null;
        return entity != null && entity.isAlive() && player.distanceToSqr(entity) <= this.maxDistanceSqr;
    }
}
