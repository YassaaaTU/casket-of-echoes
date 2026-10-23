package com.soulsvault.core;

import net.minecraft.nbt.CompoundTag;

/**
 * One player's preferences for an item in their Casket (favorite pin, auto-refill, pickup overflow, carry cap).
 * <p>
 * Kept per player, separate from the item counts in {@link VaultStorage}, so party members sharing a Casket each
 * keep their own pins and refill targets.
 */
public record ItemPrefs(boolean favorite, boolean autoRefill, int refillTarget, boolean overflowDeposit) {
    private static final String NBT_KEY = "Key";
    private static final String NBT_FAVORITE = "Favorite";
    private static final String NBT_AUTO_REFILL = "AutoRefill";
    private static final String NBT_REFILL_TARGET = "RefillTarget";
    private static final String NBT_OVERFLOW_DEPOSIT = "OverflowDeposit";

    public ItemPrefs {
        refillTarget = Math.max(1, refillTarget);
    }

    public static ItemPrefs defaultsFor(ItemKey key) {
        return new ItemPrefs(false, false, key.isEmpty() ? 1 : Math.max(1, key.getItem().getMaxStackSize()), false);
    }

    /**
     * Whether these prefs are worth keeping (and showing an entry for) even when nothing is stored.
     */
    public boolean isPersistent() {
        return this.favorite || this.autoRefill || this.overflowDeposit;
    }

    public ItemPrefs withFavorite(boolean newFavorite) {
        return new ItemPrefs(newFavorite, this.autoRefill, this.refillTarget, this.overflowDeposit);
    }

    public ItemPrefs withRefill(boolean newAutoRefill, int newRefillTarget, boolean newOverflowDeposit) {
        return new ItemPrefs(this.favorite, newAutoRefill, newRefillTarget, newOverflowDeposit);
    }

    public CompoundTag toTag(ItemKey key) {
        CompoundTag tag = new CompoundTag();
        tag.put(NBT_KEY, key.toTag());
        tag.putBoolean(NBT_FAVORITE, this.favorite);
        tag.putBoolean(NBT_AUTO_REFILL, this.autoRefill);
        tag.putInt(NBT_REFILL_TARGET, this.refillTarget);
        tag.putBoolean(NBT_OVERFLOW_DEPOSIT, this.overflowDeposit);
        return tag;
    }

    public static ItemKey keyFromTag(CompoundTag tag) {
        return ItemKey.fromTag(tag.getCompound(NBT_KEY));
    }

    public static ItemPrefs fromTag(CompoundTag tag) {
        return new ItemPrefs(
                tag.getBoolean(NBT_FAVORITE),
                tag.getBoolean(NBT_AUTO_REFILL),
                tag.getInt(NBT_REFILL_TARGET),
                tag.getBoolean(NBT_OVERFLOW_DEPOSIT)
        );
    }
}
