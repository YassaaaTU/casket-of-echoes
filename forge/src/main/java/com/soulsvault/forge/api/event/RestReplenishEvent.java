package com.soulsvault.forge.api.event;

import com.soulsvault.core.ItemKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * Rest &amp; Replenish events on the Forge event bus (server side), for packs that want dynamic Estus-style rules
 * beyond the {@code soulsvault/replenish_caps} datapack files.
 */
public abstract class RestReplenishEvent extends PlayerEvent {

    protected RestReplenishEvent(ServerPlayer player) {
        super(player);
    }

    /** Before anything moves. Cancel to skip the whole rest. */
    @Cancelable
    public static class Pre extends RestReplenishEvent {
        public Pre(ServerPlayer player) {
            super(player);
        }
    }

    /**
     * Once per pinned item about to be refilled, after datapack caps were applied. Lower (or raise) the amount;
     * {@code 0} skips the item.
     */
    public static class Refill extends RestReplenishEvent {
        private final ItemKey key;
        private int amount;

        public Refill(ServerPlayer player, ItemKey key, int amount) {
            super(player);
            this.key = key;
            this.amount = amount;
        }

        public ItemStack getItem() {
            return this.key.toSingleStack();
        }

        public ItemKey getKey() {
            return this.key;
        }

        public int getAmount() {
            return this.amount;
        }

        public void setAmount(int amount) {
            this.amount = Math.max(0, amount);
        }
    }

    /** After the rest, with what moved. */
    public static class Post extends RestReplenishEvent {
        private final int sweptToVault;
        private final int refilledToPlayer;

        public Post(ServerPlayer player, int sweptToVault, int refilledToPlayer) {
            super(player);
            this.sweptToVault = sweptToVault;
            this.refilledToPlayer = refilledToPlayer;
        }

        public int getSweptToVault() {
            return this.sweptToVault;
        }

        public int getRefilledToPlayer() {
            return this.refilledToPlayer;
        }
    }
}
