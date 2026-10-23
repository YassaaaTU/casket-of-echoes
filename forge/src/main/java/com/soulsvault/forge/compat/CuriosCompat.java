package com.soulsvault.forge.compat;

import com.soulsvault.Constants;
import com.soulsvault.core.AccessorySlotAccess;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Soft-dependency integration adapter for Curios API ({@code curios}) and mods built on Curios
 * (such as The Aether's Pendant/Necklace, Gloves/Hands, Rings, Cape, Shield of Repulsion, and Accessories).
 * Handles Curios item detection, live interactive slot bindings, cosmetic Curios slots, and atomic BuildEcho swaps.
 */
public final class CuriosCompat {
    public static final String MOD_ID = "curios";

    private static boolean reflectionInitialized = false;
    private static Method getCuriosHelperMethod;
    private static Method getCurioTagsMethod;
    private static Method getCuriosInventoryMethod;
    private static Method getCuriosMapMethod;
    private static Method getStacksMethod;
    private static Method getCosmeticStacksMethod;
    private static Method hasCosmeticMethod;
    private static Method isVisibleMethod;

    private CuriosCompat() {
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
            Class<?> curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            getCuriosHelperMethod = curiosApiClass.getMethod("getCuriosHelper");
            getCuriosInventoryMethod = curiosApiClass.getMethod("getCuriosInventory", LivingEntity.class);

            Class<?> helperInterface = Class.forName("top.theillusivec4.curios.api.type.util.ICuriosHelper");
            getCurioTagsMethod = helperInterface.getMethod("getCurioTags", Item.class);

            Class<?> handlerInterface = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
            getCuriosMapMethod = handlerInterface.getMethod("getCurios");

            Class<?> stacksHandlerInterface = Class.forName("top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler");
            getStacksMethod = stacksHandlerInterface.getMethod("getStacks");
            try {
                getCosmeticStacksMethod = stacksHandlerInterface.getMethod("getCosmeticStacks");
                hasCosmeticMethod = stacksHandlerInterface.getMethod("hasCosmetic");
                isVisibleMethod = stacksHandlerInterface.getMethod("isVisible");
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            Constants.LOG.debug("Curios API reflection hook initialization skipped: {}", t.getMessage());
        }
    }

    public static boolean isCurio(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getTags().anyMatch(tagKey -> {
            String ns = tagKey.location().getNamespace();
            return MOD_ID.equals(ns) || "aether".equals(ns) && tagKey.location().getPath().contains("accessories");
        })) {
            return true;
        }
        if (!isLoaded()) {
            return false;
        }
        initReflection();
        if (getCuriosHelperMethod != null && getCurioTagsMethod != null) {
            try {
                Object helper = getCuriosHelperMethod.invoke(null);
                if (helper != null) {
                    Object tagsObj = getCurioTagsMethod.invoke(helper, stack.getItem());
                    if (tagsObj instanceof Set<?> tags && !tags.isEmpty()) {
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static boolean canEquipInCurioSlot(String slotId, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String normalized = slotId.toLowerCase(Locale.ROOT);
        boolean tagMatched = stack.getTags().anyMatch(tagKey -> {
            String ns = tagKey.location().getNamespace();
            String path = tagKey.location().getPath().toLowerCase(Locale.ROOT);
            if (MOD_ID.equals(ns) && (path.equals(normalized) || path.equals("curio"))) {
                return true;
            }
            return "aether".equals(ns) && (path.equals(normalized) || path.contains(normalized.replace("aether_", "")));
        });
        if (tagMatched) {
            return true;
        }
        if (!isLoaded()) {
            return false;
        }
        initReflection();
        if (getCuriosHelperMethod != null && getCurioTagsMethod != null) {
            try {
                Object helper = getCuriosHelperMethod.invoke(null);
                if (helper != null) {
                    Object tagsObj = getCurioTagsMethod.invoke(helper, stack.getItem());
                    if (tagsObj instanceof Set<?> tags) {
                        for (Object tag : tags) {
                            String tagStr = String.valueOf(tag).toLowerCase(Locale.ROOT);
                            if (tagStr.equals(normalized) || tagStr.equals("curio")) {
                                return true;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    /**
     * Collects all live interactive Curios & Aether accessory slots (plus cosmetic Curio slots if requested)
     * for the given player.
     */
    public static List<AccessorySlotAccess> getAccessorySlots(Player player, boolean includeCosmetics) {
        if (player == null || !isLoaded()) {
            return Collections.emptyList();
        }
        initReflection();
        if (getCuriosInventoryMethod == null || getCuriosMapMethod == null || getStacksMethod == null) {
            return Collections.emptyList();
        }

        List<AccessorySlotAccess> result = new ArrayList<>();
        try {
            Object lazyOptObj = getCuriosInventoryMethod.invoke(null, player);
            if (lazyOptObj instanceof LazyOptional<?> lazyOpt) {
                Optional<?> resolved = lazyOpt.resolve();
                if (resolved.isPresent()) {
                    Object curiosHandler = resolved.get();
                    Object mapObj = getCuriosMapMethod.invoke(curiosHandler);
                    if (mapObj instanceof Map<?, ?> rawMap) {
                        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                            String slotId = String.valueOf(entry.getKey());
                            Object stacksHandler = entry.getValue();
                            if (isVisibleMethod != null) {
                                Object vis = isVisibleMethod.invoke(stacksHandler);
                                if (vis instanceof Boolean b && !b) {
                                    continue;
                                }
                            }

                            if (includeCosmetics && hasCosmeticMethod != null && getCosmeticStacksMethod != null) {
                                Object hasCos = hasCosmeticMethod.invoke(stacksHandler);
                                if (hasCos instanceof Boolean b && b) {
                                    Object cosStacksObj = getCosmeticStacksMethod.invoke(stacksHandler);
                                    if (cosStacksObj instanceof IItemHandlerModifiable cosHandler) {
                                        for (int i = 0; i < cosHandler.getSlots(); i++) {
                                            result.add(new CurioSlotAccess(slotId, i, cosHandler, true));
                                        }
                                        continue;
                                    }
                                }
                            }

                            Object stacksObj = getStacksMethod.invoke(stacksHandler);
                            if (stacksObj instanceof IItemHandlerModifiable itemHandler) {
                                for (int i = 0; i < itemHandler.getSlots(); i++) {
                                    result.add(new CurioSlotAccess(slotId, i, itemHandler, false));
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Constants.LOG.debug("Failed to query Curios slots for {}: {}", player.getScoreboardName(), t.getMessage());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, List<ItemKey>> captureCurios(Player player) {
        if (player == null || !isLoaded()) {
            return Collections.emptyMap();
        }
        initReflection();
        if (getCuriosInventoryMethod == null || getCuriosMapMethod == null || getStacksMethod == null) {
            return Collections.emptyMap();
        }

        Map<String, List<ItemKey>> captured = new LinkedHashMap<>();
        try {
            Object lazyOptObj = getCuriosInventoryMethod.invoke(null, player);
            if (lazyOptObj instanceof LazyOptional<?> lazyOpt) {
                Optional<?> resolved = lazyOpt.resolve();
                if (resolved.isPresent()) {
                    Object curiosHandler = resolved.get();
                    Object mapObj = getCuriosMapMethod.invoke(curiosHandler);
                    if (mapObj instanceof Map<?, ?> rawMap) {
                        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                            String slotId = String.valueOf(entry.getKey());
                            Object stacksHandler = entry.getValue();
                            Object stacksObj = getStacksMethod.invoke(stacksHandler);
                            if (stacksObj instanceof IItemHandlerModifiable itemHandler) {
                                int slots = itemHandler.getSlots();
                                List<ItemKey> slotKeys = new ArrayList<>(slots);
                                for (int i = 0; i < slots; i++) {
                                    slotKeys.add(ItemKey.fromStack(itemHandler.getStackInSlot(i)));
                                }
                                captured.put(slotId, slotKeys);
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Constants.LOG.debug("Failed to capture Curios inventory for {}: {}", player.getScoreboardName(), t.getMessage());
        }
        return captured;
    }

    public static void swapCurios(
            ServerPlayer player,
            PlayerVaultData vaultData,
            Map<String, List<ItemKey>> desiredCurios,
            Set<ItemKey> modifiedKeys
    ) {
        if (player == null || desiredCurios == null || desiredCurios.isEmpty() || !isLoaded()) {
            return;
        }
        initReflection();
        if (getCuriosInventoryMethod == null || getCuriosMapMethod == null || getStacksMethod == null) {
            return;
        }

        try {
            Object lazyOptObj = getCuriosInventoryMethod.invoke(null, player);
            if (!(lazyOptObj instanceof LazyOptional<?> lazyOpt)) {
                return;
            }
            Optional<?> resolved = lazyOpt.resolve();
            if (resolved.isEmpty()) {
                return;
            }
            Object curiosHandler = resolved.get();
            Object mapObj = getCuriosMapMethod.invoke(curiosHandler);
            if (!(mapObj instanceof Map<?, ?> rawMap)) {
                return;
            }

            for (Map.Entry<String, List<ItemKey>> targetEntry : desiredCurios.entrySet()) {
                String slotId = targetEntry.getKey();
                List<ItemKey> desiredKeys = targetEntry.getValue();
                Object stacksHandler = rawMap.get(slotId);
                if (stacksHandler == null) {
                    continue;
                }
                Object stacksObj = getStacksMethod.invoke(stacksHandler);
                if (!(stacksObj instanceof IItemHandlerModifiable itemHandler)) {
                    continue;
                }

                int maxSlot = Math.min(itemHandler.getSlots(), desiredKeys.size());
                for (int i = 0; i < maxSlot; i++) {
                    ItemKey desiredKey = desiredKeys.get(i);
                    ItemStack currentStack = itemHandler.getStackInSlot(i);
                    ItemKey currentKey = ItemKey.fromStack(currentStack);
                    if (currentKey.equals(desiredKey)) {
                        continue;
                    }
                    if (!desiredKey.isEmpty() && vaultData.getCount(desiredKey) <= 0L) {
                        continue;
                    }

                    if (!currentStack.isEmpty()) {
                        long accepted = vaultData.deposit(currentKey, currentStack.getCount());
                        if (accepted < currentStack.getCount()) {
                            continue;
                        }
                        itemHandler.setStackInSlot(i, ItemStack.EMPTY);
                        modifiedKeys.add(currentKey);
                    }

                    if (!desiredKey.isEmpty()) {
                        long withdrawn = vaultData.withdraw(desiredKey, 1L);
                        if (withdrawn > 0L) {
                            itemHandler.setStackInSlot(i, desiredKey.toSingleStack());
                            modifiedKeys.add(desiredKey);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Constants.LOG.debug("Failed to swap Curios inventory for {}: {}", player.getScoreboardName(), t.getMessage());
        }
    }

    private static final class CurioSlotAccess implements AccessorySlotAccess {
        private final String slotId;
        private final int slotIndex;
        private final IItemHandlerModifiable handler;
        private final boolean cosmetic;

        CurioSlotAccess(String slotId, int slotIndex, IItemHandlerModifiable handler, boolean cosmetic) {
            this.slotId = slotId;
            this.slotIndex = slotIndex;
            this.handler = handler;
            this.cosmetic = cosmetic;
        }

        @Override
        public String getSlotTypeId() {
            return this.slotId;
        }

        @Override
        public int getSlotIndex() {
            return this.slotIndex;
        }

        @Override
        public Component getDisplayName() {
            String pretty = formatSlotName(this.slotId);
            if (this.cosmetic) {
                return Component.literal("Cosmetic " + pretty + " #" + (this.slotIndex + 1));
            }
            return Component.literal(pretty + (this.handler.getSlots() > 1 ? " #" + (this.slotIndex + 1) : ""));
        }

        @Override
        public boolean isCosmetic() {
            return this.cosmetic;
        }

        @Override
        public ItemStack getStack() {
            if (this.slotIndex >= 0 && this.slotIndex < this.handler.getSlots()) {
                return this.handler.getStackInSlot(this.slotIndex);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public void setStack(ItemStack stack) {
            if (this.slotIndex >= 0 && this.slotIndex < this.handler.getSlots()) {
                this.handler.setStackInSlot(this.slotIndex, stack == null ? ItemStack.EMPTY : stack);
            }
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return false;
            }
            if (canEquipInCurioSlot(this.slotId, stack)) {
                return true;
            }
            return this.slotIndex >= 0
                    && this.slotIndex < this.handler.getSlots()
                    && this.handler.isItemValid(this.slotIndex, stack);
        }

        private static String formatSlotName(String id) {
            String clean = id.replace("aether_", "Aether ").replace('_', ' ').trim();
            if (clean.isEmpty()) {
                return "Accessory";
            }
            return Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
        }
    }
}
