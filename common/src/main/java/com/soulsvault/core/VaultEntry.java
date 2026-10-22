package com.soulsvault.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * Stored stack record inside a player's Casket of Echoes vault.
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
    private long count;
    private boolean autoRefill;
    private int refillTarget;
    private boolean overflowDeposit;
    private boolean favorite;
    private long lastUpdatedEpoch;

    public VaultEntry(ItemKey key, long count) {
        this(
                key,
                Math.max(0L, count),
                false,
                Math.max(1, key.getItem().getMaxStackSize()),
                false,
                false,
                System.currentTimeMillis()
        );
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

    public VaultEntry copy() {
        return new VaultEntry(
                this.key,
                this.count,
                this.autoRefill,
                this.refillTarget,
                this.overflowDeposit,
                this.favorite,
                this.lastUpdatedEpoch
        );
    }

    public ItemKey getKey() {
        return this.key;
    }

    public VaultCategory getCategory() {
        return VaultCategory.classify(this.key);
    }

    public ItemStack createDisplayStack() {
        return this.key.toSingleStack();
    }

    public long getCount() {
        return this.count;
    }

    public void setCount(long count) {
        this.count = Math.max(0L, count);
        touch();
    }

    public void addCount(long delta) {
        if (delta > 0) {
            if (Long.MAX_VALUE - this.count < delta) {
                this.count = Long.MAX_VALUE;
            } else {
                this.count += delta;
            }
        } else if (delta < 0) {
            this.count = Math.max(0L, this.count + delta);
        }
        touch();
    }

    public boolean isAutoRefill() {
        return this.autoRefill;
    }

    public void setAutoRefill(boolean autoRefill) {
        this.autoRefill = autoRefill;
        touch();
    }

    public int getRefillTarget() {
        return this.refillTarget;
    }

    public void setRefillTarget(int refillTarget) {
        this.refillTarget = Math.max(1, refillTarget);
        touch();
    }

    public boolean isOverflowDeposit() {
        return this.overflowDeposit;
    }

    public void setOverflowDeposit(boolean overflowDeposit) {
        this.overflowDeposit = overflowDeposit;
        touch();
    }

    public boolean isFavorite() {
        return this.favorite;
    }

    public void setFavorite(boolean favorite) {
        this.favorite = favorite;
        touch();
    }

    public long getLastUpdatedEpoch() {
        return this.lastUpdatedEpoch;
    }

    public void setLastUpdatedEpoch(long lastUpdatedEpoch) {
        this.lastUpdatedEpoch = lastUpdatedEpoch;
    }

    public void touch() {
        this.lastUpdatedEpoch = System.currentTimeMillis();
    }

    /**
     * Returns true if this entry has pinned settings (favorite, auto-refill, or overflow deposit)
     * that should keep the entry record alive even if its stored count drops to 0.
     */
    public boolean hasPersistentSettings() {
        return this.favorite || this.autoRefill || this.overflowDeposit;
    }

    public boolean shouldRetain() {
        return this.count > 0L || hasPersistentSettings();
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.put(NBT_KEY, this.key.toTag());
        tag.putLong(NBT_COUNT, this.count);
        tag.putBoolean(NBT_AUTO_REFILL, this.autoRefill);
        tag.putInt(NBT_REFILL_TARGET, this.refillTarget);
        tag.putBoolean(NBT_OVERFLOW_DEPOSIT, this.overflowDeposit);
        tag.putBoolean(NBT_FAVORITE, this.favorite);
        tag.putLong(NBT_LAST_UPDATED, this.lastUpdatedEpoch);
        return tag;
    }

    public static VaultEntry fromTag(CompoundTag tag) {
        ItemKey key = ItemKey.fromTag(tag.getCompound(NBT_KEY));
        long count = tag.getLong(NBT_COUNT);
        boolean autoRefill = tag.getBoolean(NBT_AUTO_REFILL);
        int defaultTarget = key.isEmpty() ? 1 : Math.max(1, key.getItem().getMaxStackSize());
        int refillTarget = tag.contains(NBT_REFILL_TARGET) ? tag.getInt(NBT_REFILL_TARGET) : defaultTarget;
        boolean overflowDeposit = tag.getBoolean(NBT_OVERFLOW_DEPOSIT);
        boolean favorite = tag.getBoolean(NBT_FAVORITE);
        long lastUpdated = tag.contains(NBT_LAST_UPDATED) ? tag.getLong(NBT_LAST_UPDATED) : System.currentTimeMillis();
        return new VaultEntry(key, count, autoRefill, refillTarget, overflowDeposit, favorite, lastUpdated);
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
