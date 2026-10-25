package com.soulsvault.forge.compat;

import com.soulsvault.Constants;
import com.soulsvault.core.InventorySorter;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Soft Quark bridge: the Casket inventory's sort button uses Quark's own sorting (same order as Quark's sort button
 * elsewhere) when Quark's Inventory Sorting module is enabled. Reflection only, so Quark stays optional.
 */
public final class QuarkCompat {
    private static final String MOD_ID = "quark";

    private static boolean resolved;
    private static Method sortRange;
    private static Object moduleManager;
    private static Method isEnabled;
    private static Class<?> sortingModule;

    private QuarkCompat() {
    }

    /**
     * Sorts the 27 main inventory slots with Quark (exactly what Quark's player-inventory sort does: slots 9-35,
     * hotbar untouched).
     *
     * @return {@code false} when Quark is absent, its sorting module is disabled, or the call failed
     */
    public static boolean sortMainInventory(ServerPlayer player) {
        if (!ModList.get().isLoaded(MOD_ID) || !resolve()) {
            return false;
        }
        try {
            if (moduleManager != null && !(boolean) isEnabled.invoke(moduleManager, sortingModule)) {
                return false;
            }
            IItemHandler handler = new InvWrapper(player.getInventory());
            sortRange.invoke(null, handler, InventorySorter.MAIN_START, InventorySorter.MAIN_END, new int[0]);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            Constants.LOG.warn("Quark inventory sorting failed, using the built-in sort instead", e);
            sortRange = null;
            return false;
        }
    }

    private static synchronized boolean resolve() {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> handler = Class.forName("org.violetmoon.quark.base.handler.SortingHandler");
                sortRange = handler.getMethod("sortInventory", IItemHandler.class, int.class, int.class, int[].class);
                sortingModule = Class.forName("org.violetmoon.quark.content.management.module.InventorySortingModule");
                Field zeta = Class.forName("org.violetmoon.quark.base.Quark").getField("ZETA");
                Object zetaInstance = zeta.get(null);
                moduleManager = zetaInstance.getClass().getField("modules").get(zetaInstance);
                isEnabled = moduleManager.getClass().getMethod("isEnabled", Class.class);
            } catch (ReflectiveOperationException | RuntimeException e) {
                Constants.LOG.warn("Quark is installed but its sorting API was not found; using the built-in sort", e);
                sortRange = null;
            }
        }
        return sortRange != null;
    }
}
