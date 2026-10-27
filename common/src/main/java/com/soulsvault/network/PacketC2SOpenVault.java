package com.soulsvault.network;

import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.core.VaultAccess;
import com.soulsvault.core.rest.RestPointProfile;
import com.soulsvault.core.rest.RestPointProfiles;
import com.soulsvault.platform.Services;
import com.soulsvault.world.entity.SpectralCasketEntity;
import com.soulsvault.world.inventory.CasketAnchor;
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
 * <p>
 * The server finds what the player may open it at (a Spectral Casket, the given or nearest Rest Point) and falls back
 * to a remote session only when {@code fullInventoryReplacement} allows one. Nothing the client claims is trusted.
 */
public final class PacketC2SOpenVault {
    @Nullable
    private final BlockPos restPos;
    private final boolean fromInventoryReplacement;

    private PacketC2SOpenVault(@Nullable BlockPos restPos, boolean fromInventoryReplacement) {
        this.restPos = restPos;
        this.fromInventoryReplacement = fromInventoryReplacement;
    }

    /**
     * Request sent by the inventory key and the "Open Casket" key mapping: opens at a nearby Rest Point / Spectral
     * Casket, or anywhere when the server has {@code fullInventoryReplacement} enabled.
     */
    public static PacketC2SOpenVault request() {
        return new PacketC2SOpenVault(null, true);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNullable(this.restPos, FriendlyByteBuf::writeBlockPos);
        buf.writeBoolean(this.fromInventoryReplacement);
    }

    public static PacketC2SOpenVault decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readNullable(FriendlyByteBuf::readBlockPos);
        return new PacketC2SOpenVault(pos, buf.readBoolean());
    }

    public void handleServer(ServerPlayer player) {
        if (player == null || player.isSpectator() || !RequestThrottle.tryAcquire(player, RequestThrottle.Kind.OPEN_VAULT)) {
            return;
        }

        RestPointProfile profile = null;
        CasketAnchor anchor = null;
        SpectralCasketEntity casket = findNearbySpectralCasket(player);
        if (casket != null) {
            profile = RestPointProfiles.SPECTRAL_CASKET;
            anchor = CasketAnchor.atEntity(casket, RestPointProfiles.reachOf(profile));
        } else if (this.restPos != null) {
            if (!player.level().isLoaded(this.restPos)) {
                return;
            }
            RestPointProfile atPos = RestPointProfiles.find(player.level().getBlockState(this.restPos));
            if (atPos != null && RestPointProfiles.inReach(player, this.restPos, atPos)) {
                profile = atPos;
                anchor = CasketAnchor.atBlock(player.level(), this.restPos, atPos);
            }
        } else {
            RestPointProfiles.Nearby nearby = RestPointProfiles.findNearby(player);
            if (nearby != null) {
                profile = nearby.profile();
                anchor = CasketAnchor.atBlock(player.level(), nearby.pos(), nearby.profile());
            }
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
        Services.PLATFORM.openVaultMenu(player, grants, anchor);
    }

    @Nullable
    private static SpectralCasketEntity findNearbySpectralCasket(ServerPlayer player) {
        List<SpectralCasketEntity> caskets = player.level().getEntitiesOfClass(
                SpectralCasketEntity.class,
                player.getBoundingBox().inflate(RestPointProfiles.reachOf(RestPointProfiles.SPECTRAL_CASKET)),
                Entity::isAlive
        );
        return caskets.isEmpty() ? null : caskets.get(0);
    }
}
