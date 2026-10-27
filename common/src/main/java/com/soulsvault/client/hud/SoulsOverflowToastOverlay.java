package com.soulsvault.client.hud;

import com.soulsvault.client.ClientVaultCache;
import com.soulsvault.config.SoulsVaultClientConfig;
import com.soulsvault.core.ItemKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Renders the sleek dark Souls-style HUD banner on the right side of the screen
 * when world pickups overflow into the Casket or pinned items replenish on Rest.
 */
public final class SoulsOverflowToastOverlay {
    private static final long FADE_IN_MS = 220L;
    private static final long FADE_OUT_MS = 550L;
    private static final int MAX_VISIBLE_TOASTS = 4;
    private static final int BANNER_WIDTH = 176;
    private static final int BANNER_HEIGHT = 26;

    private static final List<ActiveToast> ACTIVE_TOASTS = new LinkedList<>();
    private static boolean listenerRegistered = false;

    private SoulsOverflowToastOverlay() {
    }

    /**
     * Subscribes the overlay to toast notifications; safe to call more than once.
     */
    public static synchronized void init() {
        if (listenerRegistered) {
            return;
        }
        listenerRegistered = true;
        ClientVaultCache.registerToastListener(SoulsOverflowToastOverlay::onToastEvent);
    }

    private static long displayDurationMs() {
        return Math.max(500L, Math.round(SoulsVaultClientConfig.get().toastDurationSeconds() * 1000.0D));
    }

    private static synchronized void onToastEvent(ClientVaultCache.OverflowToastEvent event) {
        if (event == null || event.displayStack().isEmpty() || event.count() <= 0 || !SoulsVaultClientConfig.get().toastsEnabled()) {
            return;
        }
        if (SoulsVaultClientConfig.get().toastSound()) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                    event.isReplenish() ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.BUNDLE_INSERT, 1.0F, 0.6F));
        }
        ItemKey key = ItemKey.fromStack(event.displayStack());
        long now = System.currentTimeMillis();

        // Coalesce with an existing active toast of the same ItemKey and replenish mode
        for (ActiveToast toast : ACTIVE_TOASTS) {
            if (toast.key.equals(key) && toast.isReplenish == event.isReplenish()) {
                toast.count += event.count();
                toast.updatedAtMs = now;
                return;
            }
        }

        while (ACTIVE_TOASTS.size() >= MAX_VISIBLE_TOASTS) {
            ACTIVE_TOASTS.remove(0);
        }
        ACTIVE_TOASTS.add(new ActiveToast(key, event.displayStack().copyWithCount(1), event.count(), event.isReplenish(), now));
    }

    /**
     * Draws the active banners and drops expired ones; registered as a HUD layer by the loader.
     */
    public static synchronized void render(GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        if (ACTIVE_TOASTS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        Font font = mc.font;
        long now = System.currentTimeMillis();
        SoulsVaultClientConfig.ToastPosition position = SoulsVaultClientConfig.get().toastPosition();
        boolean leftSide = position == SoulsVaultClientConfig.ToastPosition.TOP_LEFT
                || position == SoulsVaultClientConfig.ToastPosition.BOTTOM_LEFT;
        boolean top = position == SoulsVaultClientConfig.ToastPosition.TOP_LEFT
                || position == SoulsVaultClientConfig.ToastPosition.TOP_RIGHT;
        int baseX = leftSide ? 8 : screenWidth - BANNER_WIDTH - 8;
        int baseY = switch (position) {
            case TOP_LEFT, TOP_RIGHT -> 8;
            case MIDDLE_RIGHT -> (int) (screenHeight * 0.58F);
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - BANNER_HEIGHT - 48;
        };
        long durationMs = displayDurationMs();

        int index = 0;
        Iterator<ActiveToast> iterator = ACTIVE_TOASTS.iterator();
        while (iterator.hasNext()) {
            ActiveToast toast = iterator.next();
            long elapsed = now - toast.updatedAtMs;
            if (elapsed >= durationMs) {
                iterator.remove();
                continue;
            }

            float alpha = computeAlpha(elapsed, durationMs);
            int y = top ? baseY + index * (BANNER_HEIGHT + 4) : baseY - index * (BANNER_HEIGHT + 4);
            renderSingleBanner(graphics, font, toast, baseX, y, alpha);
            index++;
        }
    }

    private static float computeAlpha(long elapsedMs, long durationMs) {
        if (elapsedMs < FADE_IN_MS) {
            return Mth.clamp((float) elapsedMs / (float) FADE_IN_MS, 0.05F, 1.0F);
        }
        long remaining = durationMs - elapsedMs;
        if (remaining < FADE_OUT_MS) {
            return Mth.clamp((float) remaining / (float) FADE_OUT_MS, 0.05F, 1.0F);
        }
        return 1.0F;
    }

    private static void renderSingleBanner(
            GuiGraphics graphics,
            Font font,
            ActiveToast toast,
            int x,
            int y,
            float alpha
    ) {
        int bgAlpha = Math.round(225.0F * alpha);
        int borderAlpha = Math.round(240.0F * alpha);
        int textAlpha = Math.max(8, Math.round(255.0F * alpha));

        int bgSolid = (bgAlpha << 24) | 0x121013;
        int bgFade = (Math.round(90.0F * alpha) << 24) | 0x121013;
        int bronzeColor = (borderAlpha << 24) | 0x8C7853;
        int goldAccent = (borderAlpha << 24) | (toast.isReplenish ? 0xD4AF37 : 0x4FA8B8);

        // Sleek dark horizontal banner body + left fade strip
        graphics.fill(x, y, x + 18, y + BANNER_HEIGHT, bgFade);
        graphics.fill(x + 18, y, x + BANNER_WIDTH, y + BANNER_HEIGHT, bgSolid);

        // Top and bottom weathered bronze trim rules + right gold/soul-cyan accent bar
        graphics.fill(x + 10, y, x + BANNER_WIDTH, y + 1, bronzeColor);
        graphics.fill(x + 10, y + BANNER_HEIGHT - 1, x + BANNER_WIDTH, y + BANNER_HEIGHT, bronzeColor);
        graphics.fill(x + BANNER_WIDTH - 2, y, x + BANNER_WIDTH, y + BANNER_HEIGHT, goldAccent);

        // Render 16x16 item icon
        int iconX = x + 16;
        int iconY = y + (BANNER_HEIGHT - 16) / 2;
        graphics.renderItem(toast.displayStack, iconX, iconY);

        // Item hover name + status subtitle
        int textX = iconX + 20;
        int maxTextW = BANNER_WIDTH - 40;
        String itemName = font.plainSubstrByWidth(toast.displayStack.getHoverName().getString(), maxTextW);
        int nameColor = (textAlpha << 24) | 0xEAE2D6;
        graphics.drawString(font, itemName, textX, y + 4, nameColor, true);

        String subKey = toast.isReplenish ? "gui.soulsvault.toast.replenished" : "gui.soulsvault.toast.overflow";
        String subText = Component.translatable(subKey, toast.count).getString();
        int subColor = (textAlpha << 24) | (toast.isReplenish ? 0xD4AF37 : 0x76C7D6);

        graphics.pose().pushPose();
        graphics.pose().translate(textX, y + 15, 0.0F);
        graphics.pose().scale(0.85F, 0.85F, 1.0F);
        graphics.drawString(font, subText, 0, 0, subColor, true);
        graphics.pose().popPose();
    }

    private static final class ActiveToast {
        final ItemKey key;
        final ItemStack displayStack;
        int count;
        final boolean isReplenish;
        long updatedAtMs;

        ActiveToast(ItemKey key, ItemStack displayStack, int count, boolean isReplenish, long updatedAtMs) {
            this.key = key;
            this.displayStack = displayStack;
            this.count = count;
            this.isReplenish = isReplenish;
            this.updatedAtMs = updatedAtMs;
        }
    }
}
