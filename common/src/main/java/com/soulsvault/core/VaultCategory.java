package com.soulsvault.core;

import com.google.common.collect.Multimap;
import com.soulsvault.Constants;
import com.soulsvault.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.SplashPotionItem;
import net.minecraft.world.item.LingeringPotionItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.WritableBookItem;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The 8 Souls-like vault categories (ALL + 7 specialized tabs) and cached auto-classification rules.
 */
public enum VaultCategory {
    ALL("gui.soulsvault.category.all"),
    WEAPONS("gui.soulsvault.category.weapons"),
    RANGED_AMMO("gui.soulsvault.category.ranged_ammo"),
    ARMOR("gui.soulsvault.category.armor"),
    CURIOS_RELICS("gui.soulsvault.category.curios_relics"),
    CONSUMABLES("gui.soulsvault.category.consumables"),
    MATERIALS("gui.soulsvault.category.materials"),
    KEY_LORE("gui.soulsvault.category.key_lore");

    private static final Map<ItemKey, VaultCategory> CLASSIFICATION_CACHE = new ConcurrentHashMap<>();

    private final String translationKey;

    VaultCategory(String translationKey) {
        this.translationKey = translationKey;
    }

    public String getTranslationKey() {
        return this.translationKey;
    }

    public Component getDisplayName() {
        return Component.translatable(this.translationKey);
    }

    public boolean matches(VaultCategory itemCategory) {
        return this == ALL || this == itemCategory;
    }

    public boolean matches(ItemStack stack) {
        return this == ALL || classify(stack) == this;
    }

    public boolean matches(ItemKey key) {
        return this == ALL || classify(key) == this;
    }

    public static void clearCache() {
        CLASSIFICATION_CACHE.clear();
    }

    public static VaultCategory classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return MATERIALS;
        }
        return classify(ItemKey.fromStack(stack));
    }

    public static VaultCategory classify(ItemKey key) {
        if (key == null || key.isEmpty()) {
            return MATERIALS;
        }
        return CLASSIFICATION_CACHE.computeIfAbsent(key, VaultCategory::computeCategory);
    }

    private static VaultCategory computeCategory(ItemKey key) {
        ItemStack stack = key.toSingleStack();
        Item item = key.getItem();

        // Explicit modpack override tags take top precedence if applied
        if (stack.is(Constants.KEY_ITEMS_TAG)) {
            return KEY_LORE;
        }
        if (stack.is(Constants.CONSUMABLES_TAG)) {
            return CONSUMABLES;
        }

        // 1. WEAPONS: SwordItem, AxeItem, TridentItem, positive ATTACK_DAMAGE in mainhand,
        //    #forge:tools/weapons, or EpicFightCompat.isWeapon(stack)
        if (isWeapon(stack, item)) {
            return WEAPONS;
        }

        // 2. RANGED_AMMO: ProjectileWeaponItem (BowItem, CrossbowItem), ArrowItem, #minecraft:arrows,
        //    throwable combat items
        if (isRangedOrAmmo(stack, item)) {
            return RANGED_AMMO;
        }

        // 3. ARMOR: ArmorItem, ShieldItem, Equipable (if slot is HEAD, CHEST, LEGS, FEET)
        if (isArmorOrShield(stack, item)) {
            return ARMOR;
        }

        // 4. CURIOS_RELICS: Any item matching #curios:* item tags or CuriosCompat.isCurio(stack)
        if (isCurioOrRelic(stack)) {
            return CURIOS_RELICS;
        }

        // 5. CONSUMABLES: Item#isEdible(), PotionItem, MilkBucketItem, #soulsvault:consumables
        if (isConsumable(stack, item)) {
            return CONSUMABLES;
        }

        // 6. KEY_LORE: Rarity.EPIC non-equipment, written books, boss trophies/souls, #soulsvault:key_items
        if (isKeyOrLore(stack, item)) {
            return KEY_LORE;
        }

        // 7. MATERIALS: Default fallback for ingots, smithing stones, mob drops, blocks, crafting reagents
        return MATERIALS;
    }

    private static boolean isWeapon(ItemStack stack, Item item) {
        if (item instanceof SwordItem || item instanceof AxeItem || item instanceof TridentItem) {
            return true;
        }
        if (stack.is(Constants.FORGE_WEAPONS_TAG) || Services.PLATFORM.isEpicFightWeapon(stack)) {
            return true;
        }
        // Exclude ranged weapons, ammo, armor, shields, and curios before generic attribute check
        if (item instanceof ProjectileWeaponItem
                || item instanceof ArrowItem
                || item instanceof ArmorItem
                || item instanceof ShieldItem
                || isCurioOrRelic(stack)) {
            return false;
        }
        return hasPositiveMainhandAttackDamage(stack);
    }

    private static boolean hasPositiveMainhandAttackDamage(ItemStack stack) {
        Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(EquipmentSlot.MAINHAND);
        Collection<AttributeModifier> attackModifiers = modifiers.get(Attributes.ATTACK_DAMAGE);
        if (attackModifiers == null || attackModifiers.isEmpty()) {
            return false;
        }
        double total = 0.0D;
        for (AttributeModifier modifier : attackModifiers) {
            total += modifier.getAmount();
        }
        return total > 0.0D;
    }

    private static boolean isRangedOrAmmo(ItemStack stack, Item item) {
        if (item instanceof ProjectileWeaponItem || item instanceof ArrowItem) {
            return true;
        }
        if (stack.is(ItemTags.ARROWS)) {
            return true;
        }
        return item instanceof SplashPotionItem
                || item instanceof LingeringPotionItem
                || item instanceof SnowballItem
                || item instanceof EnderpearlItem
                || item instanceof EggItem
                || item instanceof ExperienceBottleItem
                || item instanceof FireChargeItem
                || item instanceof FireworkRocketItem;
    }

    private static boolean isArmorOrShield(ItemStack stack, Item item) {
        if (item instanceof ArmorItem || item instanceof ShieldItem) {
            return true;
        }
        if (item instanceof Equipable equipable) {
            EquipmentSlot slot = equipable.getEquipmentSlot();
            return slot == EquipmentSlot.HEAD
                    || slot == EquipmentSlot.CHEST
                    || slot == EquipmentSlot.LEGS
                    || slot == EquipmentSlot.FEET
                    || slot == EquipmentSlot.OFFHAND;
        }
        return false;
    }

    private static boolean isCurioOrRelic(ItemStack stack) {
        if (Services.PLATFORM.isCurioItem(stack)) {
            return true;
        }
        return stack.getTags().anyMatch(tagKey -> "curios".equals(tagKey.location().getNamespace()));
    }

    private static boolean isConsumable(ItemStack stack, Item item) {
        if (item.isEdible() || item instanceof MilkBucketItem) {
            return true;
        }
        if (item instanceof PotionItem && !(item instanceof ThrowablePotionItem)) {
            return true;
        }
        return stack.is(Constants.CONSUMABLES_TAG);
    }

    private static boolean isKeyOrLore(ItemStack stack, Item item) {
        if (stack.is(Constants.KEY_ITEMS_TAG)) {
            return true;
        }
        if (item instanceof WrittenBookItem || item instanceof WritableBookItem) {
            return true;
        }
        Rarity rarity = stack.getRarity();
        return rarity == Rarity.EPIC && stack.getMaxStackSize() <= 64 && !stack.isDamageableItem();
    }
}
