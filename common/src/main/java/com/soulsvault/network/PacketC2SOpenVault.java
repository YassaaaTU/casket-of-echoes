package com.soulsvault.network;

import com.soulsvault.Constants;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.platform.Services;
import com.soulsvault.world.entity.SpectralCasketEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Client-to-Server request to open the player's Casket of Echoes vault.
 * Validates that the player is within 6 blocks of a {@code #soulsvault:rest_points} block
 * or an active {@link SpectralCasketEntity}, then initiates chunked vault synchronization.
 */
public final class PacketC2SOpenVault {
    @Nullable
    private final BlockPos restPos;

    public PacketC2SOpenVault(@Nullable BlockPos restPos) {
        this.restPos = restPos;
    }

    @Nullable
    public BlockPos getRestPos() {
        return this.restPos;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNullable(this.restPos, FriendlyByteBuf::writeBlockPos);
    }

    public static PacketC2SOpenVault decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readNullable(FriendlyByteBuf::readBlockPos);
        return new PacketC2SOpenVault(pos);
    }

    public void handleServer(ServerPlayer player) {
        if (player == null || player.isSpectator()) {
            return;
        }
        if (this.restPos != null) {
            if (!player.level().isLoaded(this.restPos)) {
                return;
            }
            double distSqr = player.distanceToSqr(Vec3.atCenterOf(this.restPos));
            if (distSqr > Constants.REST_POINT_REACH_SQR) {
                Constants.LOG.debug("Player {} too far from rest point {} (distSqr={})",
                        player.getScoreboardName(), this.restPos, distSqr);
                return;
            }
            BlockState state = player.level().getBlockState(this.restPos);
            if (!state.is(Constants.REST_POINTS_TAG) && !hasNearbySpectralCasket(player)) {
                Constants.LOG.debug("Block {} at {} is not in #soulsvault:rest_points", state.getBlock(), this.restPos);
                return;
            }
        } else if (!hasNearbySpectralCasket(player)) {
            return;
        }

        PlayerVaultData vaultData = Services.PLATFORM.getPlayerVaultData(player);
        Services.PLATFORM.syncFullVaultToClient(player, vaultData);
    }

    private static boolean hasNearbySpectralCasket(ServerPlayer player) {
        List<SpectralCasketEntity> caskets = player.level().getEntitiesOfClass(
                SpectralCasketEntity.class,
                player.getBoundingBox().inflate(Constants.REST_POINT_REACH_BLOCKS),
                Entity::isAlive
        );
        return !caskets.isEmpty();
    }
}
