package com.soulsvault.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bookkeeping for a party's shared Covenant Casket: who has joined it, who owns which stored items, and which
 * Reliquary Shard level each member has earned.
 * <p>
 * Ownership: a deposit belongs to the depositor; a withdrawal uses the withdrawer's own items first and then takes
 * from everyone else in proportion to what they own. When a member leaves or the Covenant dissolves, each member
 * gets back what they still own. Every operation touches one item's small owner map (O(members)), so the cost is
 * negligible next to the deposit itself.
 */
public final class CovenantLedger {
    /** Owner of items nobody can claim (e.g. left behind by a member when returns on leave are off). */
    public static final UUID UNOWNED = new UUID(0L, 0L);

    private static final String NBT_SHARES = "Shares";
    private static final String NBT_KEY = "Key";
    private static final String NBT_OWNERS = "Owners";
    private static final String NBT_ID = "Id";
    private static final String NBT_COUNT = "Count";
    private static final String NBT_MEMBERS = "Members";
    private static final String NBT_SINCE = "Since";
    private static final String NBT_CREDIT = "Credit";

    private final Map<ItemKey, Map<UUID, Long>> shares = new HashMap<>();
    /** Covenant members in join order, with the time they joined (epoch millis). */
    private final Map<UUID, Long> memberSince = new LinkedHashMap<>();
    /** Highest Covenant expansion level each member earned (applied to their private Casket on leaving). */
    private final Map<UUID, Integer> credits = new HashMap<>();
    private Runnable dirtyListener = () -> {
    };

    void setDirtyListener(Runnable listener) {
        this.dirtyListener = listener != null ? listener : () -> {
        };
    }

    private void markDirty() {
        this.dirtyListener.run();
    }

    // ------------------------------------------------------------------------------------------------ membership

    public boolean isMember(UUID player) {
        return this.memberSince.containsKey(player);
    }

    public Set<UUID> members() {
        return Collections.unmodifiableSet(this.memberSince.keySet());
    }

    public void addMember(UUID player, long joinedAtMillis) {
        this.memberSince.putIfAbsent(player, joinedAtMillis);
        markDirty();
    }

    public void removeMember(UUID player) {
        this.memberSince.remove(player);
        this.credits.remove(player);
        markDirty();
    }

    public long memberSince(UUID player) {
        return this.memberSince.getOrDefault(player, Long.MAX_VALUE);
    }

    /**
     * Credits the new Covenant level to every member who has belonged long enough (no join-and-leave farming).
     */
    public void creditExpansion(int level, long nowMillis, long minMembershipMillis) {
        for (Map.Entry<UUID, Long> member : this.memberSince.entrySet()) {
            if (nowMillis - member.getValue() >= minMembershipMillis) {
                this.credits.merge(member.getKey(), level, Math::max);
            }
        }
        markDirty();
    }

    public int creditOf(UUID player) {
        return this.credits.getOrDefault(player, 0);
    }

    // ------------------------------------------------------------------------------------------------ ownership

    void onDeposit(ItemKey key, long amount, @Nullable UUID actor) {
        if (amount <= 0L) {
            return;
        }
        this.shares.computeIfAbsent(key, k -> new HashMap<>(4)).merge(actor != null ? actor : UNOWNED, amount, Long::sum);
        markDirty();
    }

    void onWithdraw(ItemKey key, long amount, @Nullable UUID actor, long storedAfter) {
        Map<UUID, Long> owners = this.shares.get(key);
        if (owners == null || amount <= 0L) {
            return;
        }
        UUID self = actor != null ? actor : UNOWNED;
        long own = owners.getOrDefault(self, 0L);
        long fromSelf = Math.min(own, amount);
        if (fromSelf > 0L) {
            owners.put(self, own - fromSelf);
        }
        if (amount > fromSelf) {
            reduceProportionally(owners, amount - fromSelf, self);
        }
        reconcile(key, owners, storedAfter);
        markDirty();
    }

    /**
     * What a member still owns, per item.
     */
    public Map<ItemKey, Long> sharesOf(UUID player) {
        Map<ItemKey, Long> owned = new LinkedHashMap<>();
        for (Map.Entry<ItemKey, Map<UUID, Long>> entry : this.shares.entrySet()) {
            long count = entry.getValue().getOrDefault(player, 0L);
            if (count > 0L) {
                owned.put(entry.getKey(), count);
            }
        }
        return owned;
    }

    /**
     * Hands a member's remaining items to the Covenant ({@link #UNOWNED}) instead of returning them.
     */
    public void transferToUnowned(UUID player) {
        for (Map<UUID, Long> owners : this.shares.values()) {
            Long count = owners.remove(player);
            if (count != null && count > 0L) {
                owners.merge(UNOWNED, count, Long::sum);
            }
        }
        markDirty();
    }

    public void clearShares() {
        this.shares.clear();
        markDirty();
    }

    public void clear() {
        this.shares.clear();
        this.memberSince.clear();
        this.credits.clear();
        markDirty();
    }

    /**
     * Keeps owner totals equal to the stored count (covers items stored before tracking or edited externally).
     */
    private static void reconcile(ItemKey key, Map<UUID, Long> owners, long stored) {
        owners.values().removeIf(count -> count <= 0L);
        long total = 0L;
        for (long count : owners.values()) {
            total += count;
        }
        if (total < stored) {
            owners.merge(UNOWNED, stored - total, Long::sum);
        } else if (total > stored) {
            long excess = total - stored;
            long unowned = owners.getOrDefault(UNOWNED, 0L);
            long fromUnowned = Math.min(unowned, excess);
            if (fromUnowned > 0L) {
                owners.put(UNOWNED, unowned - fromUnowned);
            }
            if (excess > fromUnowned) {
                reduceProportionally(owners, excess - fromUnowned, null);
            }
            owners.values().removeIf(count -> count <= 0L);
        }
    }

    /**
     * Removes {@code amount} from everyone but {@code skip}, proportionally to what they own (largest remainder).
     */
    private static void reduceProportionally(Map<UUID, Long> owners, long amount, @Nullable UUID skip) {
        List<Map.Entry<UUID, Long>> others = new ArrayList<>();
        long total = 0L;
        for (Map.Entry<UUID, Long> entry : owners.entrySet()) {
            if (!entry.getKey().equals(skip) && entry.getValue() > 0L) {
                others.add(entry);
                total += entry.getValue();
            }
        }
        if (total <= 0L) {
            return;
        }
        long toCut = Math.min(amount, total);
        others.sort(Comparator.<Map.Entry<UUID, Long>>comparingLong(Map.Entry::getValue).reversed()
                .thenComparing(Map.Entry::getKey));
        long[] cuts = new long[others.size()];
        long cutSoFar = 0L;
        for (int i = 0; i < others.size(); i++) {
            cuts[i] = (long) Math.floor((double) others.get(i).getValue() * toCut / total);
            cutSoFar += cuts[i];
        }
        for (int i = 0; cutSoFar < toCut && i < others.size(); i = (i + 1) % others.size()) {
            if (others.get(i).getValue() - cuts[i] > 0L) {
                cuts[i]++;
                cutSoFar++;
            }
        }
        for (int i = 0; i < others.size(); i++) {
            owners.put(others.get(i).getKey(), others.get(i).getValue() - cuts[i]);
        }
    }

    // ------------------------------------------------------------------------------------------------ persistence

    CompoundTag save() {
        CompoundTag root = new CompoundTag();
        ListTag sharesTag = new ListTag();
        for (Map.Entry<ItemKey, Map<UUID, Long>> entry : this.shares.entrySet()) {
            CompoundTag itemTag = new CompoundTag();
            itemTag.put(NBT_KEY, entry.getKey().toTag());
            ListTag ownersTag = new ListTag();
            for (Map.Entry<UUID, Long> owner : entry.getValue().entrySet()) {
                if (owner.getValue() > 0L) {
                    CompoundTag ownerTag = new CompoundTag();
                    ownerTag.putUUID(NBT_ID, owner.getKey());
                    ownerTag.putLong(NBT_COUNT, owner.getValue());
                    ownersTag.add(ownerTag);
                }
            }
            itemTag.put(NBT_OWNERS, ownersTag);
            sharesTag.add(itemTag);
        }
        root.put(NBT_SHARES, sharesTag);

        ListTag membersTag = new ListTag();
        for (Map.Entry<UUID, Long> member : this.memberSince.entrySet()) {
            CompoundTag memberTag = new CompoundTag();
            memberTag.putUUID(NBT_ID, member.getKey());
            memberTag.putLong(NBT_SINCE, member.getValue());
            memberTag.putInt(NBT_CREDIT, creditOf(member.getKey()));
            membersTag.add(memberTag);
        }
        root.put(NBT_MEMBERS, membersTag);
        return root;
    }

    void load(CompoundTag root) {
        this.shares.clear();
        this.memberSince.clear();
        this.credits.clear();
        ListTag sharesTag = root.getList(NBT_SHARES, Tag.TAG_COMPOUND);
        for (int i = 0; i < sharesTag.size(); i++) {
            CompoundTag itemTag = sharesTag.getCompound(i);
            ItemKey key = ItemKey.fromTag(itemTag.getCompound(NBT_KEY));
            if (key.isEmpty()) {
                continue;
            }
            Map<UUID, Long> owners = new HashMap<>(4);
            ListTag ownersTag = itemTag.getList(NBT_OWNERS, Tag.TAG_COMPOUND);
            for (int j = 0; j < ownersTag.size(); j++) {
                CompoundTag ownerTag = ownersTag.getCompound(j);
                owners.put(ownerTag.getUUID(NBT_ID), ownerTag.getLong(NBT_COUNT));
            }
            this.shares.put(key, owners);
        }
        ListTag membersTag = root.getList(NBT_MEMBERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < membersTag.size(); i++) {
            CompoundTag memberTag = membersTag.getCompound(i);
            UUID id = memberTag.getUUID(NBT_ID);
            this.memberSince.put(id, memberTag.getLong(NBT_SINCE));
            if (memberTag.getInt(NBT_CREDIT) > 0) {
                this.credits.put(id, memberTag.getInt(NBT_CREDIT));
            }
        }
    }

    /**
     * Brings owner totals in line with the stored counts after loading (items stored before tracking existed
     * become {@link #UNOWNED}).
     */
    void reconcileAll(Map<ItemKey, Long> storedCounts) {
        this.shares.keySet().removeIf(key -> !storedCounts.containsKey(key));
        for (Map.Entry<ItemKey, Long> stored : storedCounts.entrySet()) {
            reconcile(stored.getKey(), this.shares.computeIfAbsent(stored.getKey(), k -> new HashMap<>(4)), stored.getValue());
        }
    }
}
