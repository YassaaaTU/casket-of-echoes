package com.soulsvault.world.block;

import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultAccess;
import com.soulsvault.core.rest.RestPointProfile;
import com.soulsvault.core.rest.RestPointProfiles;
import com.soulsvault.platform.Services;
import com.soulsvault.world.inventory.CasketAnchor;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Craftable dark stone/iron casket block that grants direct access to a player's Casket of Echoes
 * and triggers Rest &amp; Replenish upon interaction. Also hosts the shared "open the Casket at a Rest Point"
 * flow used by Rest Point blocks and Spectral Caskets.
 */
public class BottomlessCasketBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SHAPE_NORTH_SOUTH = Block.box(1.0D, 0.0D, 2.0D, 15.0D, 14.0D, 14.0D);
    private static final VoxelShape SHAPE_EAST_WEST = Block.box(2.0D, 0.0D, 1.0D, 14.0D, 14.0D, 15.0D);

    public BottomlessCasketBlock() {
        this(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .requiresCorrectToolForDrops()
                .strength(4.0F, 1200.0F)
                .sound(SoundType.DEEPSLATE_BRICKS)
                .lightLevel(state -> 4)
                .noOcclusion());
    }

    public BottomlessCasketBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    @SuppressWarnings("deprecation") // Vanilla deprecates this for callers (use the BlockState); overriding is intended
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction dir = state.getValue(FACING);
        return (dir == Direction.NORTH || dir == Direction.SOUTH) ? SHAPE_NORTH_SOUTH : SHAPE_EAST_WEST;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    @SuppressWarnings("deprecation") // Vanilla deprecates this for callers (use the BlockState); overriding is intended
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        if (player.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            openCasketForPlayer(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Opens the Casket at a Rest Point block (or the Bottomless Casket), resting first when the block's profile allows.
     */
    public static void openCasketForPlayer(ServerPlayer serverPlayer, BlockPos restPos) {
        RestPointProfile profile = RestPointProfiles.find(serverPlayer.level().getBlockState(restPos));
        openCasketForPlayer(serverPlayer, restPos, profile != null ? profile : RestPointProfiles.TAGGED_DEFAULT);
    }

    /**
     * Opens the Casket at a Rest Point block with its profile: rests (replenish + the profile's resting effects)
     * unless combat-locked, then opens the screen with the profile's session powers.
     */
    public static void openCasketForPlayer(ServerPlayer serverPlayer, BlockPos restPos, RestPointProfile profile) {
        openCasketForPlayer(serverPlayer, restPos, profile, CasketAnchor.atBlock(serverPlayer.level(), restPos, profile));
    }

    /**
     * Same as {@link #openCasketForPlayer(ServerPlayer, BlockPos, RestPointProfile)} for any anchor, e.g. a Spectral
     * Casket entity standing at {@code restPos}.
     */
    public static void openCasketForPlayer(ServerPlayer serverPlayer, BlockPos restPos, RestPointProfile profile,
                                           CasketAnchor anchor) {
        Component denied = VaultAccess.check(serverPlayer, VaultAccess.Type.OPEN, profile.grants().any());
        if (denied != null) {
            serverPlayer.displayClientMessage(denied.copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        serverPlayer.level().playSound(
                null,
                restPos,
                SoundEvents.ENDER_CHEST_OPEN,
                SoundSource.BLOCKS,
                0.55F,
                0.65F
        );
        Component restDenied = VaultAccess.check(serverPlayer, VaultAccess.Type.REST, profile.grants().replenish());
        if (restDenied == null) {
            RestPointProfiles.applyRestEffects(serverPlayer, profile);
            if (profile.grants().replenish()) {
                PlayerVaultData vaultData = Services.PLATFORM.getPlayerVaultData(serverPlayer);
                vaultData.performRestAndReplenish(serverPlayer);
            }
        } else {
            serverPlayer.displayClientMessage(restDenied.copy().withStyle(ChatFormatting.RED), true);
        }
        Services.PLATFORM.openVaultMenu(serverPlayer, profile.grants(), anchor);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(4) == 0) {
            double x = pos.getX() + 0.2D + random.nextDouble() * 0.6D;
            double y = pos.getY() + 0.85D;
            double z = pos.getZ() + 0.2D + random.nextDouble() * 0.6D;
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 0.0D, 0.015D, 0.0D);
        }
    }
}
