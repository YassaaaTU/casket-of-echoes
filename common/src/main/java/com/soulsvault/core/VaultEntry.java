package com.soulsvault.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * One stored item as a player sees it: the count from the Casket's {@link VaultStorage} combined with that
 * player's own {@link ItemPrefs} (pins, refill target, overflow).
 * <p>
 * An immutable snapshot used for syncing and display; the authoritative data lives in {@link VaultStorage} and
 * {@link PlayerVaultData}.
 */
public final class VaultEntry {
    private static final String NBT_KEY = "Key";
    private static final String NBT_COUNT = "Count";
    private static final String NBT_AUTO_REFILL = "AutoRefill";
    private static final String NBT_REFILL_TARGET = "RefillTarget";
    private static final String NBT_OVERFLOW_DEPOSIT = "OverflowDeposit";
    private static final String NBT_FAVORITE = "Favorite";
    private static final String NBT_LAST_UPDATED = "LastUpdatedEpoch";

    private final ItemKey key;
    private final long count;
    private final boolean autoRefill;
    private final int refillTarget;
    private final boolean overflowDeposit;
    private final boolean favorite;
    private final long lastUpdatedEpoch;

    /**
     * An entry with default preferences (used for Keepsakes, which have no pins).
     */
    public VaultEntry(ItemKey key, long count) {
        this(key, count, false, key.getMaxStackSize(), false, false, System.currentTimeMillis());
    }

    public VaultEntry(
            ItemKey key,
            long count,
            boolean autoRefill,
            int refillTarget,
            boolean overflowDeposit,
            boolean favorite,
            long lastUpdatedEpoch
    ) {
        this.key = Objects.requireNonNull(key, "key cannot be null");
        this.count = Math.max(0L, count);
        this.autoRefill = autoRefill;
        this.refillTarget = Math.max(1, refillTarget);
        this.overflowDeposit = overflowDeposit;
        this.favorite = favorite;
        this.lastUpdatedEpoch = lastUpdatedEpoch;
    }

    public ItemKey getKey() {
        return this.key;
    }

    public VaultCategory getCategory() {
        return VaultCategory.classify(this.key);
    }

    /**
     * A fresh one-item stack for rendering and tooltips.
     */
    public ItemStack createDisplayStack() {
        return this.key.toSingleStack();
    }

    public long getCount() {
        return this.count;
    }

    public boolean isAutoRefill() {
        return this.autoRefill;
    }

    public int getRefillTarget() {
        return this.refillTarget;
    }

    public boolean isOverflowDeposit() {
        return this.overflowDeposit;
    }

    public boolean isFavorite() {
        return this.favorite;
    }

    public long getLastUpdatedEpoch() {
        return this.lastUpdatedEpoch;
    }

    /**
     * Whether a client should keep showing this entry: something is stored, or a pin keeps the empty entry visible.
     */
    public boolean shouldRetain() {
        return this.count > 0L || this.favorite || this.autoRefill || this.overflowDeposit;
    }

    /**
     * Reads an entry from the pre-party save format, where counts and preferences were stored together. Only used
     * to migrate old worlds; current saves go through {@link VaultStorage} and {@link ItemPrefs}.
     */
    public static VaultEntry fromLegacyTag(CompoundTag tag) {
        ItemKey key = ItemKey.fromTag(tag.getCompound(NBT_KEY));
        int refillTarget = tag.contains(NBT_REFILL_TARGET) ? tag.getInt(NBT_REFILL_TARGET) : key.getMaxStackSize();
        long lastUpdated = tag.contains(NBT_LAST_UPDATED) ? tag.getLong(NBT_LAST_UPDATED) : System.currentTimeMillis();
        return new VaultEntry(
                key,
                tag.getLong(NBT_COUNT),
                tag.getBoolean(NBT_AUTO_REFILL),
                refillTarget,
                tag.getBoolean(NBT_OVERFLOW_DEPOSIT),
                tag.getBoolean(NBT_FAVORITE),
                lastUpdated
        );
    }

    public void toNetwork(FriendlyByteBuf buf) {
        this.key.toNetwork(buf);
        buf.writeVarLong(this.count);
        buf.writeBoolean(this.autoRefill);
        buf.writeVarInt(this.refillTarget);
        buf.writeBoolean(this.overflowDeposit);
        buf.writeBoolean(this.favorite);
        buf.writeLong(this.lastUpdatedEpoch);
    }

    public static VaultEntry fromNetwork(FriendlyByteBuf buf) {
        ItemKey key = ItemKey.fromNetwork(buf);
        long count = buf.readVarLong();
        boolean autoRefill = buf.readBoolean();
        int refillTarget = buf.readVarInt();
        boolean overflowDeposit = buf.readBoolean();
        boolean favorite = buf.readBoolean();
        long lastUpdatedEpoch = buf.readLong();
        return new VaultEntry(key, count, autoRefill, refillTarget, overflowDeposit, favorite, lastUpdatedEpoch);
    }
}
