package com.soulsvault.core;

import com.soulsvault.config.SoulsVaultConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Item counts held by one Casket: a player's private Casket or a party's shared Casket.
 * <p>
 * Holds no per-player preferences (those live in {@link PlayerVaultData}), so several players can be bound to the
 * same storage. Capacity comes from the server config plus Reliquary Shard expansions applied to this storage.
 * A party's shared Covenant Casket also carries a {@link CovenantLedger} recording who owns what.
 */
public final class VaultStorage {
    private static final String NBT_ITEMS = "Items";
    private static final String NBT_KEY = "Key";
    private static final String NBT_COUNT = "Count";
    private static final String NBT_UPDATED = "Updated";
    private static final String NBT_EXPANSIONS = "Expansions";
    private static final String NBT_LEDGER = "Ledger";

    private final String id;
    private final Map<ItemKey, Stored> items = new LinkedHashMap<>();
    private int expansions;
    /** Sum of all counts, or {@code -1} until recomputed after a change (the party HUD asks every few ticks). */
    private long totalCache = -1L;
    @Nullable
    private final CovenantLedger ledger;
    private Runnable dirtyListener = () -> {
    };

    private static final class Stored {
        long count;
        long updated;

        Stored(long count, long updated) {
            this.count = count;
            this.updated = updated;
        }
    }

    /**
     * @param id stable identity, e.g. {@code player:<uuid>} or {@code party:<key>}; used to find everyone sharing it
     */
    public VaultStorage(String id) {
        this(id, false);
    }

    /**
     * @param covenant whether this is a party's shared Casket (tracks members and item ownership)
     */
    public VaultStorage(String id, boolean covenant) {
        this.id = id;
        this.ledger = covenant ? new CovenantLedger() : null;
    }

    /**
     * The Covenant bookkeeping, or {@code null} for a private Casket.
     */
    @Nullable
    public CovenantLedger getLedger() {
        return this.ledger;
    }

    public String getId() {
        return this.id;
    }

    /**
     * Called on every change, so the owning saved data is written on the next save.
     */
    public void setDirtyListener(Runnable listener) {
        this.dirtyListener = listener != null ? listener : () -> {
        };
        if (this.ledger != null) {
            this.ledger.setDirtyListener(this.dirtyListener);
        }
    }

    private void markDirty() {
        this.dirtyListener.run();
    }

    /**
     * How many of {@code key} are stored (0 when none).
     */
    public long getCount(ItemKey key) {
        Stored stored = key == null ? null : this.items.get(key);
        return stored != null ? stored.count : 0L;
    }

    public long getLastUpdated(ItemKey key) {
        Stored stored = key == null ? null : this.items.get(key);
        return stored != null ? stored.updated : 0L;
    }

    /**
     * Every stored kind of item (read-only view).
     */
    public Set<ItemKey> keys() {
        return Collections.unmodifiableSet(this.items.keySet());
    }

    /**
     * Number of distinct items stored.
     */
    public int uniqueEntries() {
        return this.items.size();
    }

    /**
     * Total items stored across every kind (saturating at {@link Long#MAX_VALUE}).
     */
    public long totalItems() {
        long total = this.totalCache;
        if (total < 0L) {
            total = 0L;
            for (Stored stored : this.items.values()) {
                total = saturatingAdd(total, stored.count);
            }
            this.totalCache = total;
        }
        return total;
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    public int getExpansions() {
        return this.expansions;
    }

    /**
     * Applies one Reliquary Shard.
     */
    public void addExpansion() {
        this.expansions++;
        markDirty();
    }

    /**
     * Raises the expansion level to at least {@code level} (Covenant upgrades credited back to a member).
     */
    public void raiseExpansionsTo(int level) {
        if (level > this.expansions) {
            this.expansions = level;
            markDirty();
        }
    }

    /**
     * How many of each item this storage holds (config plus expansions).
     */
    public long getMaxStackCapacity() {
        SoulsVaultConfig.Upgrades upgrades = SoulsVaultConfig.upgrades();
        long bonus = upgrades.capacityPerExpansion() * (long) this.expansions;
        return Math.max(1L, saturatingAdd(SoulsVaultConfig.getMaxStackCapacity(), bonus));
    }

    /**
     * Distinct items this storage may hold, or {@code 0} for unlimited.
     */
    public int getMaxUniqueEntries() {
        int base = SoulsVaultConfig.vault().maxUniqueEntries();
        if (base <= 0) {
            return 0;
        }
        long total = (long) base + (long) SoulsVaultConfig.upgrades().uniqueEntriesPerExpansion() * this.expansions;
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * Stores up to {@code amount} of {@code key}, respecting {@link VaultRules}, the per-item capacity, and the
     * distinct-item limit.
     *
     * @return the number of items accepted
     */
    public long deposit(ItemKey key, long amount) {
        return deposit(key, amount, null);
    }

    /**
     * Same as {@link #deposit(ItemKey, long)}, recording {@code actor} as the owner in a Covenant Casket.
     */
    public long deposit(ItemKey key, long amount, @Nullable UUID actor) {
        if (key == null || key.isEmpty() || amount <= 0L || !VaultRules.canStore(key)) {
            return 0L;
        }
        Stored stored = this.items.get(key);
        if (stored == null) {
            int maxUnique = getMaxUniqueEntries();
            if (maxUnique > 0 && this.items.size() >= maxUnique) {
                return 0L;
            }
        }
        long current = stored != null ? stored.count : 0L;
        long accepted = Math.min(amount, Math.max(0L, getMaxStackCapacity() - current));
        if (accepted <= 0L) {
            return 0L;
        }
        add(key, accepted);
        if (this.ledger != null) {
            this.ledger.onDeposit(key, accepted, actor);
        }
        return accepted;
    }

    /**
     * Stores {@code amount} of {@code key} ignoring every limit. Only used so items are never destroyed, e.g. when
     * a disbanded party's Casket is handed to its last leader.
     */
    public void depositUnchecked(ItemKey key, long amount) {
        depositUnchecked(key, amount, null);
    }

    /**
     * Same as {@link #depositUnchecked(ItemKey, long)}, recording {@code actor} as the owner in a Covenant Casket.
     */
    public void depositUnchecked(ItemKey key, long amount, @Nullable UUID actor) {
        if (key != null && !key.isEmpty() && amount > 0L) {
            add(key, amount);
            if (this.ledger != null) {
                this.ledger.onDeposit(key, amount, actor);
            }
        }
    }

    private void add(ItemKey key, long amount) {
        Stored stored = this.items.get(key);
        long now = System.currentTimeMillis();
        if (stored == null) {
            this.items.put(key, new Stored(amount, now));
        } else {
            stored.count = saturatingAdd(stored.count, amount);
            stored.updated = now;
        }
        this.totalCache = -1L;
        markDirty();
    }

    /**
     * @return the number of items actually withdrawn
     */
    public long withdraw(ItemKey key, long requestedAmount) {
        return withdraw(key, requestedAmount, null);
    }

    /**
     * Same as {@link #withdraw(ItemKey, long)}; in a Covenant Casket the withdrawer's own items go first.
     */
    public long withdraw(ItemKey key, long requestedAmount, @Nullable UUID actor) {
        if (key == null || key.isEmpty() || requestedAmount <= 0L) {
            return 0L;
        }
        Stored stored = this.items.get(key);
        if (stored == null || stored.count <= 0L) {
            return 0L;
        }
        long withdrawn = Math.min(stored.count, requestedAmount);
        stored.count -= withdrawn;
        stored.updated = System.currentTimeMillis();
        long remaining = stored.count;
        if (stored.count <= 0L) {
            this.items.remove(key);
        }
        this.totalCache = -1L;
        if (this.ledger != null) {
            this.ledger.onWithdraw(key, withdrawn, actor, remaining);
        }
        markDirty();
        return withdrawn;
    }

    /**
     * Deletes everything stored (a player chose to override their Casket).
     */
    public void clear() {
        if (!this.items.isEmpty()) {
            this.items.clear();
            this.totalCache = -1L;
            if (this.ledger != null) {
                this.ledger.clearShares();
            }
            markDirty();
        }
    }

    /**
     * Client-side mirror update from a sync/delta packet: sets the count without any limits.
     */
    public void setFromSync(ItemKey key, long count, long updated) {
        if (key == null || key.isEmpty()) {
            return;
        }
        if (count <= 0L) {
            this.items.remove(key);
        } else {
            this.items.put(key, new Stored(count, updated));
        }
        this.totalCache = -1L;
    }

    /**
     * Client-side mirror reset before a full sync.
     */
    public void clearFromSync() {
        this.items.clear();
        this.totalCache = -1L;
    }

    public CompoundTag save(CompoundTag root) {
        ListTag list = new ListTag();
        for (Map.Entry<ItemKey, Stored> entry : this.items.entrySet()) {
            if (entry.getValue().count <= 0L) {
                continue;
            }
            CompoundTag tag = new CompoundTag();
            tag.put(NBT_KEY, entry.getKey().toTag());
            tag.putLong(NBT_COUNT, entry.getValue().count);
            tag.putLong(NBT_UPDATED, entry.getValue().updated);
            list.add(tag);
        }
        root.put(NBT_ITEMS, list);
        root.putInt(NBT_EXPANSIONS, this.expansions);
        if (this.ledger != null) {
            root.put(NBT_LEDGER, this.ledger.save());
        }
        return root;
    }

    public void load(CompoundTag root) {
        this.items.clear();
        this.totalCache = -1L;
        this.expansions = Math.max(0, root.getInt(NBT_EXPANSIONS));
        ListTag list = root.getList(NBT_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ItemKey key = ItemKey.fromTag(tag.getCompound(NBT_KEY));
            long count = tag.getLong(NBT_COUNT);
            if (!key.isEmpty() && count > 0L) {
                this.items.put(key, new Stored(count, tag.getLong(NBT_UPDATED)));
            }
        }
        if (this.ledger != null) {
            this.ledger.load(root.getCompound(NBT_LEDGER));
            Map<ItemKey, Long> counts = new LinkedHashMap<>();
            for (Map.Entry<ItemKey, Stored> entry : this.items.entrySet()) {
                counts.put(entry.getKey(), entry.getValue().count);
            }
            this.ledger.reconcileAll(counts);
        }
    }

    private static long saturatingAdd(long a, long b) {
        long result = a + b;
        return ((a ^ result) & (b ^ result)) < 0 ? Long.MAX_VALUE : result;
    }
}
