package com.soulsvault.network;

import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.core.VaultAccess;
import com.soulsvault.core.rest.RestPointProfile;
import com.soulsvault.core.rest.RestPointProfiles;
import com.soulsvault.platform.Services;
import com.soulsvault.world.entity.SpectralCasketEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Client-to-Server request to open the player's Souls Inventory / Casket of Echoes menu.
 */
public final class PacketC2SOpenVault {
    @Nullable
    private final BlockPos restPos;
    private final boolean fromInventoryReplacement;

    public PacketC2SOpenVault(@Nullable BlockPos restPos) {
        this(restPos, false);
    }

    public PacketC2SOpenVault(@Nullable BlockPos restPos, boolean fromInventoryReplacement) {
        this.restPos = restPos;
        this.fromInventoryReplacement = fromInventoryReplacement;
    }

    public static PacketC2SOpenVault openFromInventoryKey() {
        return new PacketC2SOpenVault(null, true);
    }

    /**
     * Request sent by the "Open Casket" key mapping: opens at a nearby Rest Point / Spectral Casket,
     * or anywhere when the server has {@code fullInventoryReplacement} enabled (same rule as the inventory key).
     */
    public static PacketC2SOpenVault openFromKeybind() {
        return new PacketC2SOpenVault(null, true);
    }

    @Nullable
    public BlockPos getRestPos() {
        return this.restPos;
    }

    public boolean isFromInventoryReplacement() {
        return this.fromInventoryReplacement;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNullable(this.restPos, FriendlyByteBuf::writeBlockPos);
        buf.writeBoolean(this.fromInventoryReplacement);
    }

    public static PacketC2SOpenVault decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readNullable(FriendlyByteBuf::readBlockPos);
        boolean fromInv = buf.readableBytes() > 0 && buf.readBoolean();
        return new PacketC2SOpenVault(pos, fromInv);
    }

    public void handleServer(ServerPlayer player) {
        if (player == null || player.isSpectator()) {
            return;
        }

        RestPointProfile profile = null;
        if (hasNearbySpectralCasket(player)) {
            profile = RestPointProfiles.SPECTRAL_CASKET;
        } else if (this.restPos != null) {
            if (!player.level().isLoaded(this.restPos)) {
                return;
            }
            RestPointProfile atPos = RestPointProfiles.find(player.level().getBlockState(this.restPos));
            if (atPos != null && RestPointProfiles.inReach(player, this.restPos, atPos)) {
                profile = atPos;
            }
        } else {
            profile = RestPointProfiles.findNearby(player);
        }

        if (profile == null && !(this.fromInventoryReplacement && SoulsVaultConfig.isFullInventoryReplacement())) {
            player.displayClientMessage(
                    Component.translatable("gui.soulsvault.notice.rest_point_required").withStyle(ChatFormatting.RED),
                    true
            );
            return;
        }

        RestPointProfile.Grants grants = profile != null ? profile.grants() : RestPointProfile.Grants.NONE;
        Component denied = VaultAccess.check(player, VaultAccess.Type.OPEN, grants.any());
        if (denied != null) {
            player.displayClientMessage(denied.copy().withStyle(ChatFormatting.RED), true);
            return;
        }
        Services.PLATFORM.openVaultMenu(player, grants);
    }

    private static boolean hasNearbySpectralCasket(ServerPlayer player) {
        List<SpectralCasketEntity> caskets = player.level().getEntitiesOfClass(
                SpectralCasketEntity.class,
                player.getBoundingBox().inflate(SoulsVaultConfig.restPoints().reachBlocks()),
                Entity::isAlive
        );
        return !caskets.isEmpty();
    }
}
