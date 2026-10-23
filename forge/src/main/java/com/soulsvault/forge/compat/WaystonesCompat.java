package com.soulsvault.forge.compat;

import com.soulsvault.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Soft-dependency integration for Waystones ({@code waystones}) inventory teleport button.
 */
public final class WaystonesCompat {
    public static final String MOD_ID = "waystones";

    private WaystonesCompat() {
    }

    public static boolean isLoaded() {
        return ModList.get() != null && ModList.get().isLoaded(MOD_ID);
    }

    /**
     * Triggers the Waystones inventory button action (either by clicking an attached Waystones widget
     * on the active screen or invoking Waystones' inventory button handler).
     */
    public static boolean triggerInventoryWaystoneAction() {
        if (!isLoaded()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }

        // 1. Check if Waystones attached a button widget to the current screen
        Screen current = mc.screen;
        if (current != null) {
            for (GuiEventListener child : current.children()) {
                String clsName = child.getClass().getName();
                if (clsName.contains("waystones") && child instanceof Button btn) {
                    btn.onPress();
                    return true;
                }
            }
        }

        // 2. Fallback: temporarily create an InventoryScreen or invoke Waystones' InventoryButtonGui / packet
        try {
            InventoryScreen tempInv = new InventoryScreen(mc.player);
            tempInv.init(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
            for (GuiEventListener child : tempInv.children()) {
                String clsName = child.getClass().getName();
                if (clsName.contains("waystones") && child instanceof Button btn) {
                    btn.onPress();
                    return true;
                }
                if (clsName.contains("waystones") && child instanceof AbstractWidget widget) {
                    widget.onClick(widget.getX() + 2, widget.getY() + 2);
                    return true;
                }
            }
        } catch (Throwable t) {
            Constants.LOG.debug("Waystones inventory button trigger fallback failed: {}", t.getMessage());
        }

        // 3. Direct reflection fallback for Balm/Waystones network message
        try {
            Class<?> balmClass = Class.forName("net.blay09.mods.balm.api.Balm");
            Method getNetworking = balmClass.getMethod("getNetworking");
            Object networking = getNetworking.invoke(null);
            Class<?> msgClass = Class.forName("net.blay09.mods.waystones.network.message.InventoryButtonMessage");
            Constructor<?> ctor = msgClass.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object msg = ctor.newInstance();
            for (Method m : networking.getClass().getMethods()) {
                if ("sendToServer".equals(m.getName()) && m.getParameterCount() == 1) {
                    m.invoke(networking, msg);
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
    }
}
