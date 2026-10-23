package com.soulsvault.forge.api.event;

import com.soulsvault.core.VaultAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

import javax.annotation.Nullable;

/**
 * Fired on the Forge event bus (server side) before a player opens the Casket, uses the Vault, rests, or swaps a
 * Build Echo. Cancel it to deny the action; set a reason to tell the player why.
 * <p>
 * KubeJS (Forge): {@code ForgeEvents.onEvent('com.soulsvault.forge.api.event.VaultAccessEvent', e => { ... })}.
 */
@Cancelable
public class VaultAccessEvent extends PlayerEvent {
    private final VaultAccess.Type type;
    private final boolean atRestPoint;
    @Nullable
    private Component denyReason;

    public VaultAccessEvent(ServerPlayer player, VaultAccess.Type type, boolean atRestPoint) {
        super(player);
        this.type = type;
        this.atRestPoint = atRestPoint;
    }

    public VaultAccess.Type getType() {
        return this.type;
    }

    /** Whether the Casket was opened at a Rest Point, Bottomless Casket or Spectral Casket. */
    public boolean isAtRestPoint() {
        return this.atRestPoint;
    }

    @Nullable
    public Component getDenyReason() {
        return this.denyReason;
    }

    /** Cancels the event with a message shown to the player. */
    public void deny(Component reason) {
        this.denyReason = reason;
        setCanceled(true);
    }
}
