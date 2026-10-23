package com.soulsvault.forge.compat;

import com.soulsvault.Constants;
import com.soulsvault.core.AccessorySlotAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Soft-dependency adapter for Cosmetic Armor Reworked ({@code cosmeticarmorreworked}).
 * Exposes the 4 Cosmetic Armor slots (Head, Chest, Legs, Feet) directly inside the Souls UI.
 */
public final class CosmeticArmorCompat {
    public static final String MOD_ID = "cosmeticarmorreworked";

    private static boolean reflectionInitialized = false;
    private static Method getCAStacksMethod;
    private static Method getCAStacksClientMethod;

    private static final EquipmentSlot[] ARMOR_ORDER = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private CosmeticArmorCompat() {
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
            Class<?> apiClass = Class.forName("lain.mods.cos.api.CosArmorAPI");
            getCAStacksMethod = apiClass.getMethod("getCAStacks", UUID.class);
            getCAStacksClientMethod = apiClass.getMethod("getCAStacksClient", UUID.class);
        } catch (Throwable t) {
            Constants.LOG.debug("Cosmetic Armor Reworked reflection hook skipped: {}", t.getMessage());
        }
    }

    public static List<AccessorySlotAccess> getCosmeticArmorSlots(Player player) {
        if (player == null || !isLoaded()) {
            return Collections.emptyList();
        }
        initReflection();
        Method targetMethod = player.level().isClientSide ? getCAStacksClientMethod : getCAStacksMethod;
        if (targetMethod == null) {
            return Collections.emptyList();
        }

        try {
            Object invObj = targetMethod.invoke(null, player.getUUID());
            if (invObj instanceof Container container && container.getContainerSize() >= 4) {
                List<AccessorySlotAccess> slots = new ArrayList<>(4);
                // In Cosmetic Armor Reworked: index 3 = Head, 2 = Chest, 1 = Legs, 0 = Feet
                for (EquipmentSlot armorSlot : ARMOR_ORDER) {
                    int caIndex = armorSlot.getIndex();
                    slots.add(new CosmeticArmorSlotAccess(container, caIndex, armorSlot));
                }
                return slots;
            }
        } catch (Throwable t) {
            Constants.LOG.debug("Failed to retrieve Cosmetic Armor slots for {}: {}", player.getScoreboardName(), t.getMessage());
        }
        return Collections.emptyList();
    }

    private static final class CosmeticArmorSlotAccess implements AccessorySlotAccess {
        private final Container container;
        private final int containerIndex;
        private final EquipmentSlot armorSlot;

        CosmeticArmorSlotAccess(Container container, int containerIndex, EquipmentSlot armorSlot) {
            this.container = container;
            this.containerIndex = containerIndex;
            this.armorSlot = armorSlot;
        }

        @Override
        public String getSlotTypeId() {
            return "cosmetic_armor_" + this.armorSlot.getName();
        }

        @Override
        public int getSlotIndex() {
            return this.containerIndex;
        }

        @Override
        public Component getDisplayName() {
            String part = switch (this.armorSlot) {
                case HEAD -> "Helmet";
                case CHEST -> "Chestplate";
                case LEGS -> "Leggings";
                case FEET -> "Boots";
                default -> "Armor";
            };
            return Component.literal("Cosmetic " + part);
        }

        @Override
        public boolean isCosmetic() {
            return true;
        }

        @Override
        public ItemStack getStack() {
            return this.container.getItem(this.containerIndex);
        }

        @Override
        public void setStack(ItemStack stack) {
            this.container.setItem(this.containerIndex, stack == null ? ItemStack.EMPTY : stack);
            this.container.setChanged();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            return Mob.getEquipmentSlotForItem(stack) == this.armorSlot
                    || this.container.canPlaceItem(this.containerIndex, stack);
        }
    }
}
