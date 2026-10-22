package com.soulsvault.core;

import com.google.common.collect.Multimap;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.Comparator;

/**
 * Sorting comparators for vault entries within a category tab.
 * Favorite entries are always pinned to the top.
 */
public enum VaultSortMode {
    NAME("gui.soulsvault.sort.name", Comparator.comparing(VaultSortMode::getDisplayNameString, String.CASE_INSENSITIVE_ORDER)),
    COUNT("gui.soulsvault.sort.count", Comparator.comparingLong(VaultEntry::getCount).reversed()),
    ATTACK_DAMAGE("gui.soulsvault.sort.damage", Comparator.comparingDouble(VaultSortMode::getAttackDamage).reversed()),
    RARITY("gui.soulsvault.sort.rarity", Comparator.comparingInt(VaultSortMode::getRarityOrdinal).reversed()),
    RECENTLY_ACQUIRED("gui.soulsvault.sort.recent", Comparator.comparingLong(VaultEntry::getLastUpdatedEpoch).reversed()),
    MOD_ID("gui.soulsvault.sort.mod", Comparator.comparing((VaultEntry e) -> e.getKey().getNamespace(), String.CASE_INSENSITIVE_ORDER));

    private final String translationKey;
    private final Comparator<VaultEntry> comparator;

    VaultSortMode(String translationKey, Comparator<VaultEntry> primaryComparator) {
        this.translationKey = translationKey;
        this.comparator = Comparator
                .comparing(VaultEntry::isFavorite, Comparator.reverseOrder())
                .thenComparing(primaryComparator)
                .thenComparing(VaultSortMode::getDisplayNameString, String.CASE_INSENSITIVE_ORDER);
    }

    public String getTranslationKey() {
        return this.translationKey;
    }

    public Component getDisplayName() {
        return Component.translatable(this.translationKey);
    }

    public Comparator<VaultEntry> getComparator() {
        return this.comparator;
    }

    public VaultSortMode next() {
        VaultSortMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    private static String getDisplayNameString(VaultEntry entry) {
        return entry.createDisplayStack().getHoverName().getString();
    }

    private static int getRarityOrdinal(VaultEntry entry) {
        return entry.createDisplayStack().getRarity().ordinal();
    }

    private static double getAttackDamage(VaultEntry entry) {
        ItemStack stack = entry.createDisplayStack();
        Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        Collection<AttributeModifier> dmgMods = modifiers.get(Attributes.ATTACK_DAMAGE);
        if (dmgMods == null || dmgMods.isEmpty()) {
            return 0.0D;
        }
        double sum = 0.0D;
        for (AttributeModifier mod : dmgMods) {
            sum += mod.getAmount();
        }
        return sum;
    }
}
