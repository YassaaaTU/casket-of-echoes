package com.soulsvault.core.rest;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;
import java.util.Set;

/**
 * What resting at one kind of Rest Point does, loaded from {@code data/<ns>/soulsvault/rest_points/<name>.json}
 * (see {@link RestPointProfiles}). The highest-priority profile matching a block state wins.
 *
 * @param requiresLit         only counts while the block's {@code lit} property is true (kindled bonfires)
 * @param reach               how close the player must be, or {@code <= 0} for {@code restPoints.reachBlocks}
 * @param grants              powers of a Casket session opened here
 * @param setSpawn            resting sets the player's respawn point where they stand
 * @param heal                resting restores full health
 * @param feed                resting restores hunger and saturation
 * @param clearHarmfulEffects resting removes harmful status effects
 */
public record RestPointProfile(
        ResourceLocation id,
        Set<Block> blocks,
        List<TagKey<Block>> tags,
        int priority,
        boolean requiresLit,
        double reach,
        Grants grants,
        boolean setSpawn,
        boolean heal,
        boolean feed,
        boolean clearHarmfulEffects
) {

    /**
     * Powers of a Casket session, decided by where it was opened.
     *
     * @param vault     deposits / withdrawals count as "at a Rest Point" (no remote rules or costs)
     * @param replenish Rest &amp; Replenish counts as "at a Rest Point"
     * @param echoSwap  Build Echo swaps count as "at a Rest Point"
     */
    public record Grants(boolean vault, boolean replenish, boolean echoSwap) {
        public static final Grants NONE = new Grants(false, false, false);
        public static final Grants ALL = new Grants(true, true, true);

        /**
         * Whether any power applies (the session counts as opened at a Rest Point).
         */
        public boolean any() {
            return this.vault || this.replenish || this.echoSwap;
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByte((this.vault ? 1 : 0) | (this.replenish ? 2 : 0) | (this.echoSwap ? 4 : 0));
        }

        public static Grants decode(FriendlyByteBuf buf) {
            int bits = buf.readByte();
            return new Grants((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0);
        }
    }

    /**
     * Whether this profile describes {@code state}'s block (listed directly or through a tag), lit or not.
     */
    public boolean covers(BlockState state) {
        if (this.blocks.contains(state.getBlock())) {
            return true;
        }
        for (TagKey<Block> tag : this.tags) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code state} works as this profile's Rest Point right now: covered and, for {@code requiresLit}, lit.
     */
    public boolean matches(BlockState state) {
        return covers(state) && (!this.requiresLit || !state.hasProperty(BlockStateProperties.LIT)
                || state.getValue(BlockStateProperties.LIT));
    }
}
