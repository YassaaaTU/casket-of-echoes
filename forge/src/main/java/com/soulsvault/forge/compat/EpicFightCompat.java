package com.soulsvault.forge.compat;

import com.google.common.collect.Multimap;
import com.soulsvault.Constants;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Soft-dependency integration adapter for Epic Fight ({@code epicfight}) and Weapons of Miracle ({@code wom}).
 * Detects Epic Fight weapons and extracts Weight, Impact, Armor Negation, Max Strikes, and Innate Skill stats.
 */
public final class EpicFightCompat {
    public static final String MOD_ID = "epicfight";

    private static final ResourceLocation ATTR_WEIGHT = new ResourceLocation(MOD_ID, "weight");
    private static final ResourceLocation ATTR_IMPACT = new ResourceLocation(MOD_ID, "impact");
    private static final ResourceLocation ATTR_ARMOR_NEGATION = new ResourceLocation(MOD_ID, "armor_negation");
    private static final ResourceLocation ATTR_MAX_STRIKES = new ResourceLocation(MOD_ID, "max_strikes");

    private static boolean reflectionInitialized = false;
    private static Method getItemStackCapabilityMethod;
    private static Class<?> weaponCapabilityClass;

    private EpicFightCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    private static synchronized void initReflection() {
        if (reflectionInitialized) {
            return;
        }
        reflectionInitialized = true;
        if (!isLoaded()) {
            return;
        }
        try {
            Class<?> capsClass = Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities");
            getItemStackCapabilityMethod = capsClass.getMethod("getItemStackCapability", ItemStack.class);
            weaponCapabilityClass = Class.forName("yesman.epicfight.world.capabilities.item.WeaponCapability");
        } catch (Throwable t) {
            Constants.LOG.debug("Epic Fight reflection hook initialization skipped: {}", t.getMessage());
        }
    }

    public static boolean isWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isLoaded()) {
            return false;
        }
        if (getEpicFightAttributeValue(stack, ATTR_IMPACT) > 0.0D
                || getEpicFightAttributeValue(stack, ATTR_MAX_STRIKES) > 0.0D) {
            return true;
        }

        initReflection();
        if (getItemStackCapabilityMethod != null) {
            try {
                Object cap = getItemStackCapabilityMethod.invoke(null, stack);
                if (cap != null) {
                    if (weaponCapabilityClass != null && weaponCapabilityClass.isInstance(cap)) {
                        return true;
                    }
                    if (cap.getClass().getSimpleName().contains("Weapon")) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static List<Component> getInspectionStats(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !isLoaded()) {
            return Collections.emptyList();
        }

        List<Component> lines = new ArrayList<>();
        double weight = getEpicFightAttributeValue(stack, ATTR_WEIGHT);
        double impact = getEpicFightAttributeValue(stack, ATTR_IMPACT);
        double armorNegation = getEpicFightAttributeValue(stack, ATTR_ARMOR_NEGATION);
        double maxStrikes = getEpicFightAttributeValue(stack, ATTR_MAX_STRIKES);

        if (weight > 0.0D) {
            lines.add(Component.literal(String.format(Locale.ROOT, "  • Weight: %.1f", weight))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (impact > 0.0D) {
            lines.add(Component.literal(String.format(Locale.ROOT, "  • Impact: %.1f", impact))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (armorNegation > 0.0D) {
            lines.add(Component.literal(String.format(Locale.ROOT, "  • Armor Negation: %.1f%%", armorNegation))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (maxStrikes > 0.0D) {
            lines.add(Component.literal(String.format(Locale.ROOT, "  • Max Strikes: %.0f", maxStrikes))
                    .withStyle(ChatFormatting.AQUA));
        }

        String innateSkill = extractInnateSkillName(stack);
        if (innateSkill != null && !innateSkill.isBlank()) {
            lines.add(Component.literal("  • Innate Skill: " + innateSkill)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }

        return lines;
    }

    /**
     * Epic Fight's weight attribute for an item worn/held in {@code slot} (Equip Load), or {@code 0}.
     */
    public static double getWeight(ItemStack stack, EquipmentSlot slot) {
        if (!isLoaded() || stack.isEmpty()) {
            return 0.0D;
        }
        return getEpicFightAttributeValue(stack, ATTR_WEIGHT, slot);
    }

    private static double getEpicFightAttributeValue(ItemStack stack, ResourceLocation attrId) {
        return getEpicFightAttributeValue(stack, attrId, EquipmentSlot.MAINHAND);
    }

    private static double getEpicFightAttributeValue(ItemStack stack, ResourceLocation attrId, EquipmentSlot slot) {
        Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(attrId);
        if (attr == null) {
            return 0.0D;
        }
        Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(slot);
        Collection<AttributeModifier> attrMods = modifiers.get(attr);
        if (attrMods == null || attrMods.isEmpty()) {
            return 0.0D;
        }
        double sum = 0.0D;
        for (AttributeModifier mod : attrMods) {
            sum += mod.getAmount();
        }
        return sum;
    }

    private static String extractInnateSkillName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            if (tag.contains("epicfight:innate_skill")) {
                return tag.getString("epicfight:innate_skill");
            }
            if (tag.contains("InnateSkill")) {
                return tag.getString("InnateSkill");
            }
        }
        initReflection();
        if (getItemStackCapabilityMethod != null) {
            try {
                Object cap = getItemStackCapabilityMethod.invoke(null, stack);
                if (cap != null) {
                    for (Method m : cap.getClass().getMethods()) {
                        if ("getPassiveSkill".equals(m.getName()) && m.getParameterCount() == 0) {
                            Object skill = m.invoke(cap);
                            if (skill != null) {
                                return skill.toString();
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
