package com.soulsvault.client.gui.grid;

import com.soulsvault.client.ClientVaultCache;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultCategory;
import com.soulsvault.core.VaultEntry;
import com.soulsvault.core.VaultSortMode;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The filtered, sorted list of Vault entries shown by the Casket screen, kept up to date incrementally.
 * <p>
 * Same approach as AE2's terminal repo and Refined Storage 2's resource repository: per-item display data is
 * cached, the search is parsed once with a per-item match cache, a delta only moves its own entry (binary insert
 * into the sorted list), and while the mouse is over the grid the view is "paused" so nothing re-sorts under the
 * cursor (changed entries update in place; removed ones stay as empty ghosts until the mouse leaves).
 */
public final class VaultGridView implements ClientVaultCache.ChangeListener {
    private final PlayerVaultData source;
    private final Map<ItemKey, VaultEntryInfo> infos = new HashMap<>();
    private final Map<ItemKey, Boolean> matchCache = new HashMap<>();
    private final List<VaultEntry> view = new ArrayList<>();
    private final Map<VaultCategory, Integer> categoryCounts = new EnumMap<>(VaultCategory.class);

    private VaultCategory category;
    private VaultSortMode sortMode;
    private VaultSearchQuery query = VaultSearchQuery.EMPTY;
    private Comparator<VaultEntry> comparator;
    private boolean countsDirty = true;
    private boolean paused;
    private boolean resortPending;
    private Runnable changeListener = () -> {
    };

    public VaultGridView(PlayerVaultData source, VaultCategory category, VaultSortMode sortMode) {
        this.source = source;
        this.category = category;
        this.sortMode = sortMode;
        this.comparator = createComparator(sortMode);
    }

    public void setChangeListener(Runnable listener) {
        this.changeListener = listener != null ? listener : () -> {
        };
    }

    public List<VaultEntry> getView() {
        return Collections.unmodifiableList(this.view);
    }

    public VaultCategory getCategory() {
        return this.category;
    }

    public VaultSortMode getSortMode() {
        return this.sortMode;
    }

    public void setCategory(VaultCategory newCategory) {
        if (newCategory != this.category) {
            this.category = newCategory;
            rebuild();
        }
    }

    public void setSortMode(VaultSortMode newSortMode) {
        if (newSortMode != this.sortMode) {
            this.sortMode = newSortMode;
            this.comparator = createComparator(newSortMode);
            rebuild();
        }
    }

    public void setSearch(String raw) {
        VaultSearchQuery parsed = VaultSearchQuery.parse(raw);
        if (!parsed.raw().equals(this.query.raw())) {
            this.query = parsed;
            this.matchCache.clear();
            rebuild();
        }
    }

    public boolean hasFilter() {
        return this.category != VaultCategory.ALL || !this.query.isEmpty();
    }

    /**
     * Pauses re-sorting while the player's mouse is over the grid; resuming applies pending changes.
     */
    public void setPaused(boolean newPaused) {
        if (this.paused == newPaused) {
            return;
        }
        this.paused = newPaused;
        if (!newPaused && this.resortPending) {
            rebuild();
        }
    }

    public Map<VaultCategory, Integer> getCategoryCounts() {
        if (this.countsDirty) {
            this.categoryCounts.clear();
            int total = 0;
            for (VaultEntry entry : this.source.getEntries()) {
                total++;
                this.categoryCounts.merge(entry.getCategory(), 1, Integer::sum);
            }
            this.categoryCounts.put(VaultCategory.ALL, total);
            this.countsDirty = false;
        }
        return this.categoryCounts;
    }

    /**
     * Whether an item (in the Vault or the player's inventory) passes the current category and search.
     */
    public boolean matches(ItemKey key) {
        if (key.isEmpty()) {
            return false;
        }
        if (!this.category.matches(key)) {
            return false;
        }
        if (this.query.isEmpty()) {
            return true;
        }
        return this.matchCache.computeIfAbsent(key, k -> this.query.test(info(k)));
    }

    public void rebuild() {
        this.view.clear();
        for (VaultEntry entry : this.source.getEntries()) {
            if (matches(entry.getKey())) {
                this.view.add(entry);
            }
        }
        this.view.sort(this.comparator);
        this.countsDirty = true;
        this.resortPending = false;
        this.changeListener.run();
    }

    @Override
    public void onFullSync() {
        rebuild();
    }

    @Override
    public void onDelta(ItemKey key, @Nullable VaultEntry entry) {
        this.countsDirty = true;
        int index = indexOf(key);
        boolean visible = entry != null && matches(key);
        if (this.paused) {
            if (index >= 0) {
                this.view.set(index, visible ? entry : ghostOf(this.view.get(index)));
            } else if (visible) {
                this.view.add(entry);
            }
            this.resortPending = true;
        } else {
            if (index >= 0) {
                this.view.remove(index);
            }
            if (visible) {
                int insertAt = Collections.binarySearch(this.view, entry, this.comparator);
                this.view.add(insertAt < 0 ? -insertAt - 1 : insertAt, entry);
            }
        }
        this.changeListener.run();
    }

    private int indexOf(ItemKey key) {
        for (int i = 0; i < this.view.size(); i++) {
            if (this.view.get(i).getKey().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static VaultEntry ghostOf(VaultEntry entry) {
        return new VaultEntry(entry.getKey(), 0L, false, entry.getRefillTarget(), false, false, entry.getLastUpdatedEpoch());
    }

    private VaultEntryInfo info(ItemKey key) {
        return this.infos.computeIfAbsent(key, VaultEntryInfo::new);
    }

    private Comparator<VaultEntry> createComparator(VaultSortMode mode) {
        Comparator<VaultEntry> primary = switch (mode) {
            case NAME -> Comparator.comparing(entry -> info(entry.getKey()).lowerName);
            case COUNT -> Comparator.comparingLong(VaultEntry::getCount).reversed();
            case ATTACK_DAMAGE -> Comparator.<VaultEntry>comparingDouble(entry -> info(entry.getKey()).attackDamage()).reversed();
            case RARITY -> Comparator.<VaultEntry>comparingInt(entry -> info(entry.getKey()).rarity).reversed();
            case RECENTLY_ACQUIRED -> Comparator.comparingLong(VaultEntry::getLastUpdatedEpoch).reversed();
            case MOD_ID -> Comparator.comparing(entry -> info(entry.getKey()).namespace);
        };
        // Favorites first, then the chosen order, then a stable identity order so equal entries never swap places.
        return Comparator.comparing(VaultEntry::isFavorite, Comparator.reverseOrder())
                .thenComparing(primary)
                .thenComparing(entry -> info(entry.getKey()).lowerName)
                .thenComparing(entry -> info(entry.getKey()).registryId)
                .thenComparingInt(entry -> entry.getKey().hashCode());
    }
}
