package com.soulsvault.core;

import com.soulsvault.config.SoulsVaultConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A player's private Keepsakes: a small personal stash next to the Casket for items they don't want in a shared
 * Covenant Casket (quest tokens, trade keys). It never joins a party's storage and holds a few kinds of items, each
 * up to a few stacks: {@code keepsakes.slots} kinds, {@code baseStacks} stacks each, {@code upgradedStacks} after a
 * second Keepsake Coffer. Access follows the Vault's rules (attunement, Rest Points or the remote toll, combat lock).
 */
public final class Keepsakes {
    private static final String NBT_UNLOCKED = "Unlocked";
    private static final String NBT_UPGRADED = "Upgraded";
    private static final String NBT_ITEMS = "Items";
    private static final String NBT_KEY = "Key";
    private static final String NBT_COUNT = "Count";

    public enum CofferResult {
        UNLOCKED,
        UPGRADED,
        MAXED
    }

    private boolean unlocked;
    private boolean upgraded;
    private final Map<ItemKey, Long> items = new LinkedHashMap<>();
    private Runnable dirtyListener = () -> {
    };

    public void setDirtyListener(Runnable listener) {
        this.dirtyListener = listener != null ? listener : () -> {
        };
    }

    public boolean isUnlocked() {
        return this.unlocked || !SoulsVaultConfig.keepsakes().requireUnlock();
    }

    public boolean isUpgraded() {
        return this.upgraded;
    }

    /**
     * Stacks of each item one keepsake slot holds.
     */
    public int getStacks() {
        SoulsVaultConfig.Keepsakes config = SoulsVaultConfig.keepsakes();
        return Math.max(1, this.upgraded ? Math.max(config.baseStacks(), config.upgradedStacks()) : config.baseStacks());
    }

    public int getSlots() {
        return Math.max(1, SoulsVaultConfig.keepsakes().slots());
    }

    public long getCapacity(ItemKey key) {
        return (long) Math.max(1, key.getItem().getMaxStackSize()) * getStacks();
    }

    public long getCount(ItemKey key) {
        return this.items.getOrDefault(key, 0L);
    }

    public int usedSlots() {
        return this.items.size();
    }

    public List<VaultEntry> getEntries() {
        List<VaultEntry> entries = new ArrayList<>(this.items.size());
        for (Map.Entry<ItemKey, Long> entry : this.items.entrySet()) {
            entries.add(new VaultEntry(entry.getKey(), entry.getValue()));
        }
        return entries;
    }

    /**
     * Uses one Keepsake Coffer: unlocks the Keepsakes, or raises them to {@code upgradedStacks}.
     */
    public CofferResult useCoffer() {
        if (!isUnlocked()) {
            this.unlocked = true;
            markDirty();
            return CofferResult.UNLOCKED;
        }
        SoulsVaultConfig.Keepsakes config = SoulsVaultConfig.keepsakes();
        if (!this.upgraded && config.upgradedStacks() > config.baseStacks()) {
            this.unlocked = true;
            this.upgraded = true;
            markDirty();
            return CofferResult.UPGRADED;
        }
        return CofferResult.MAXED;
    }

    /**
     * @return the number of items accepted
     */
    public long deposit(ItemKey key, long amount) {
        if (!isUnlocked() || key == null || key.isEmpty() || amount <= 0L || !VaultRules.canStore(key)) {
            return 0L;
        }
        long current = getCount(key);
        if (current == 0L && this.items.size() >= getSlots()) {
            return 0L;
        }
        long accepted = Math.min(amount, Math.max(0L, getCapacity(key) - current));
        if (accepted > 0L) {
            this.items.put(key, current + accepted);
            markDirty();
        }
        return accepted;
    }

    /**
     * @return the number of items withdrawn
     */
    public long withdraw(ItemKey key, long amount) {
        long current = getCount(key);
        long withdrawn = Math.min(current, Math.max(0L, amount));
        if (withdrawn > 0L) {
            if (current - withdrawn <= 0L) {
                this.items.remove(key);
            } else {
                this.items.put(key, current - withdrawn);
            }
            markDirty();
        }
        return withdrawn;
    }

    /**
     * Client mirror update from {@code PacketS2CKeepsakes}.
     */
    public void setFromSync(boolean unlocked, boolean upgraded, List<VaultEntry> entries) {
        this.unlocked = unlocked;
        this.upgraded = upgraded;
        this.items.clear();
        for (VaultEntry entry : entries) {
            if (!entry.getKey().isEmpty() && entry.getCount() > 0L) {
                this.items.put(entry.getKey(), entry.getCount());
            }
        }
    }

    /**
     * Raw unlock flag as stored (without the {@code requireUnlock} rule), for syncing.
     */
    public boolean isUnlockedFlag() {
        return this.unlocked;
    }

    private void markDirty() {
        this.dirtyListener.run();
    }

    public CompoundTag save(CompoundTag root) {
        root.putBoolean(NBT_UNLOCKED, this.unlocked);
        root.putBoolean(NBT_UPGRADED, this.upgraded);
        ListTag list = new ListTag();
        for (Map.Entry<ItemKey, Long> entry : this.items.entrySet()) {
            CompoundTag tag = new CompoundTag();
            tag.put(NBT_KEY, entry.getKey().toTag());
            tag.putLong(NBT_COUNT, entry.getValue());
            list.add(tag);
        }
        root.put(NBT_ITEMS, list);
        return root;
    }

    public void load(CompoundTag root) {
        this.unlocked = root.getBoolean(NBT_UNLOCKED);
        this.upgraded = root.getBoolean(NBT_UPGRADED);
        this.items.clear();
        ListTag list = root.getList(NBT_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag tag = list.getCompound(i);
            ItemKey key = ItemKey.fromTag(tag.getCompound(NBT_KEY));
            long count = tag.getLong(NBT_COUNT);
            if (!key.isEmpty() && count > 0L) {
                this.items.put(key, count);
            }
        }
    }
}
