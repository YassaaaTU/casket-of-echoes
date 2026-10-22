package com.soulsvault.forge.compat;

import com.soulsvault.Constants;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Soft-dependency integration adapter for Curios API ({@code curios}).
 * Handles Curios item detection and atomic Curios slot capture/swapping for Build Echoes.
 */
public final class CuriosCompat {
    public static final String MOD_ID = "curios";

    private static boolean reflectionInitialized = false;
    private static Method getCuriosHelperMethod;
    private static Method getCurioTagsMethod;
    private static Method getCuriosInventoryMethod;
    private static Method getCuriosMapMethod;
    private static Method getStacksMethod;

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
        } catch (Throwable t) {
            Constants.LOG.debug("Curios API reflection hook initialization skipped: {}", t.getMessage());
        }
    }

    public static boolean isCurio(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.getTags().anyMatch(tagKey -> MOD_ID.equals(tagKey.location().getNamespace()))) {
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
}
