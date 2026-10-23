package com.soulsvault.core;

import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import javax.annotation.Nullable;

/**
 * Casket Attunement: with {@code vault.requireAttunement} on, a player's Casket stays sealed (no storage, Rest &amp;
 * Replenish, Build Echo swaps, pickup overflow or Spectral Caskets) until they attune with a Casket Sigil or a
 * {@code /casket attune} quest reward. The Casket screen itself still opens, showing the sealed Vault.
 */
public final class Attunement {

    private Attunement() {
    }

    public static boolean isRequired() {
        return SoulsVaultConfig.vault().requireAttunement();
    }

    /**
     * Whether the player may use their Casket: always when attunement isn't required, and in creative mode.
     */
    public static boolean isAttuned(ServerPlayer player) {
        return !isRequired() || player.isCreative() || Services.PLATFORM.getPlayerVaultData(player).isAttuned();
    }

    @Nullable
    public static Component getDenialReason(ServerPlayer player) {
        return isAttuned(player) ? null : Component.translatable("gui.soulsvault.notice.not_attuned");
    }

    /**
     * Attunes (or, with {@code attuned = false}, unseals the attunement of) a player.
     *
     * @param ceremony play the Souls-style title, sound and particles
     * @return {@code false} when the player was already in that state
     */
    public static boolean setAttuned(ServerPlayer player, boolean attuned, boolean ceremony) {
        PlayerVaultData data = Services.PLATFORM.getPlayerVaultData(player);
        if (data.isAttuned() == attuned) {
            return false;
        }
        data.setAttuned(attuned);
        if (attuned) {
            Milestones.award(player, Milestones.ATTUNED);
            if (ceremony) {
                playCeremony(player);
            }
        }
        VaultSync.resync(player);
        return true;
    }

    private static void playCeremony(ServerPlayer player) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 25));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("gui.soulsvault.attuned.title").withStyle(ChatFormatting.GOLD)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.translatable("gui.soulsvault.attuned.subtitle").withStyle(ChatFormatting.GRAY)));
        player.level().playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 0.9F, 0.8F);
        player.level().playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 0.9F);
        player.serverLevel().sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0D, player.getZ(),
                40, 0.6D, 0.8D, 0.6D, 0.02D);
    }
}
