package com.soulsvault.core;

import com.soulsvault.Constants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Platform-independent abstraction over an equipped accessory or cosmetic slot
 * (such as Curios API slots, The Aether's Pendant/Necklace, Gloves/Hands, Rings, Cape,
 * Curios cosmetic slots, or Cosmetic Armor Reworked slots).
 */
public interface AccessorySlotAccess {
    ResourceLocation ICON_NECKLACE = new ResourceLocation(Constants.MOD_ID, "textures/gui/slots/slot_necklace.png");
    ResourceLocation ICON_RING = new ResourceLocation(Constants.MOD_ID, "textures/gui/slots/slot_ring.png");
    ResourceLocation ICON_GLOVES = new ResourceLocation(Constants.MOD_ID, "textures/gui/slots/slot_gloves.png");
    ResourceLocation ICON_BELT = new ResourceLocation(Constants.MOD_ID, "textures/gui/slots/slot_belt.png");
    ResourceLocation ICON_CHARM = new ResourceLocation(Constants.MOD_ID, "textures/gui/slots/slot_charm.png");
    ResourceLocation ICON_CAPE = new ResourceLocation(Constants.MOD_ID, "textures/gui/slots/slot_cape.png");
    ResourceLocation ICON_COSMETIC = new ResourceLocation(Constants.MOD_ID, "textures/gui/slots/slot_cosmetic.png");
    ResourceLocation ICON_HEAD = new ResourceLocation(Constants.MOD_ID, "textures/gui/slots/slot_head.png");
    ResourceLocation ICON_CHEST = new ResourceLocation(Constants.MOD_ID, "textures/gui/slots/slot_chest.png");
    ResourceLocation ICON_LEGS = new ResourceLocation(Constants.MOD_ID, "textures/gui/slots/slot_legs.png");
    ResourceLocation ICON_FEET = new ResourceLocation(Constants.MOD_ID, "textures/gui/slots/slot_feet.png");
    ResourceLocation ICON_SHIELD = new ResourceLocation(Constants.MOD_ID, "textures/gui/slots/slot_offhand.png");

    /**
     * Identifier for the slot type (e.g., {@code "necklace"}, {@code "aether_gloves"}, {@code "ring"}).
     */
    String getSlotTypeId();

    /**
     * Zero-based index within the slot type handler.
     */
    int getSlotIndex();

    /**
     * Human-readable display label for tooltips.
     */
    Component getDisplayName();

    /**
     * Whether this slot is a cosmetic override slot (Curios cosmetic or Cosmetic Armor Reworked).
     */
    boolean isCosmetic();

    /**
     * Returns the 16x16 slot silhouette icon to render when empty.
     */
    default ResourceLocation getEmptyIcon() {
        return resolveIconForSlotId(getSlotTypeId(), isCosmetic());
    }

    ItemStack getStack();

    void setStack(ItemStack stack);

    boolean mayPlace(ItemStack stack);

    default boolean mayPickup(Player player) {
        return true;
    }

    static ResourceLocation resolveIconForSlotId(String slotId, boolean cosmetic) {
        if (cosmetic && (slotId == null || !slotId.startsWith("cosmetic_armor_"))) {
            return ICON_COSMETIC;
        }
        if (slotId == null) {
            return ICON_CHARM;
        }
        String lower = slotId.toLowerCase(Locale.ROOT);
        if (lower.contains("cosmetic_armor_head") || lower.equals("head")) {
            return ICON_HEAD;
        }
        if (lower.contains("cosmetic_armor_chest") || lower.equals("body")) {
            return ICON_CHEST;
        }
        if (lower.contains("cosmetic_armor_legs")) {
            return ICON_LEGS;
        }
        if (lower.contains("cosmetic_armor_feet")) {
            return ICON_FEET;
        }
        if (lower.contains("necklace") || lower.contains("pendant") || lower.contains("amulet")) {
            return ICON_NECKLACE;
        }
        if (lower.contains("ring")) {
            return ICON_RING;
        }
        if (lower.contains("hand") || lower.contains("glove") || lower.contains("bracelet")) {
            return ICON_GLOVES;
        }
        if (lower.contains("belt")) {
            return ICON_BELT;
        }
        if (lower.contains("back") || lower.contains("cape") || lower.contains("mantle")) {
            return ICON_CAPE;
        }
        if (lower.contains("shield")) {
            return ICON_SHIELD;
        }
        return ICON_CHARM;
    }
}
