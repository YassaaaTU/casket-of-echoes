package com.soulsvault.core;

import net.minecraft.network.chat.Component;

/**
 * Sort orders offered by the Casket screen's sort button. Favorites always come first; the comparators themselves
 * live in the client's grid view, which caches the per-item data they need.
 */
public enum VaultSortMode {
    NAME("gui.soulsvault.sort.name"),
    COUNT("gui.soulsvault.sort.count"),
    ATTACK_DAMAGE("gui.soulsvault.sort.damage"),
    RARITY("gui.soulsvault.sort.rarity"),
    RECENTLY_ACQUIRED("gui.soulsvault.sort.recent"),
    MOD_ID("gui.soulsvault.sort.mod");

    private final String translationKey;

    VaultSortMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component getDisplayName() {
        return Component.translatable(this.translationKey);
    }

    /**
     * The next mode in button order, wrapping around.
     */
    public VaultSortMode next() {
        VaultSortMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
