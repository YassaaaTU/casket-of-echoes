package com.soulsvault.client.gui.widget;

import com.mojang.blaze3d.platform.InputConstants;
import com.soulsvault.core.VaultEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Virtualized dark gothic item slot widget supporting stack counts > 64 with compact formatting
 * (e.g., 999, 4.2k, 10k), Favorite / Auto-Refill / Overflow badges, and native Controller / Gamepad
 * focus and cursor-snapping via {@link AbstractWidget}.
 */
public final class VaultItemSlotWidget extends AbstractWidget {
    public static final int SLOT_SIZE = 24;

    private static final int COLOR_SLOT_BG = 0xE60D0B0E;
    private static final int COLOR_SLOT_HOVER = 0xF0221C15;
    private static final int COLOR_SLOT_SELECTED = 0xF22D2418;
    private static final int COLOR_BORDER_IDLE = 0xFF3E3526;
    private static final int COLOR_BORDER_HOVER = 0xFF8C7853;
    private static final int COLOR_BORDER_SELECTED = 0xFFD4AF37;
    private static final int COLOR_FAVORITE_STAR = 0xFFD4AF37;
    private static final int COLOR_REFILL_PIP = 0xFFE06C50;
    private static final int COLOR_OVERFLOW_PIP = 0xFF4FA8B8;

    private final int size;
    @Nullable
    private VaultEntry entry;
    private ItemStack displayStack = ItemStack.EMPTY;
    private long count;
    private boolean selected;
    private final BiConsumer<VaultItemSlotWidget, Integer> clickHandler;

    /**
     * @param clickHandler receives this widget and the mouse button (0 left, 1 right, 2 middle; Enter/Space click 0)
     */
    public VaultItemSlotWidget(int x, int y, int size, BiConsumer<VaultItemSlotWidget, Integer> clickHandler) {
        super(x, y, size, size, Component.empty());
        this.size = size;
        this.clickHandler = clickHandler;
    }

    /**
     * Shows {@code entry} (or nothing for {@code null}); the display stack is built once here, not per frame.
     */
    public void bindVaultEntry(@Nullable VaultEntry entry, boolean selected) {
        this.entry = entry;
        if (entry != null && !entry.getKey().isEmpty()) {
            this.displayStack = entry.createDisplayStack();
            this.count = entry.getCount();
            this.setMessage(this.displayStack.getHoverName());
        } else {
            this.displayStack = ItemStack.EMPTY;
            this.count = 0L;
            this.setMessage(Component.empty());
        }
        this.selected = selected;
    }

    @Nullable
    public VaultEntry getEntry() {
        return this.entry;
    }

    /**
     * The bound item as a one-item stack (shared, do not modify), or empty.
     */
    public ItemStack getDisplayStack() {
        return this.displayStack;
    }

    public boolean isEmpty() {
        return this.displayStack.isEmpty();
    }

    @Override
    protected boolean isValidClickButton(int button) {
        return button == 0 || button == 1 || button == 2;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.active || !this.visible) {
            return false;
        }
        if (this.clicked(mouseX, mouseY) && isValidClickButton(button)) {
            this.clickHandler.accept(this, button);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.active || !this.visible || !this.isFocused()) {
            return false;
        }
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER || keyCode == InputConstants.KEY_SPACE) {
            this.clickHandler.accept(this, 0);
            return true;
        }
        return false;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        boolean hovered = this.isHovered();
        int bgColor = this.selected ? COLOR_SLOT_SELECTED : (hovered ? COLOR_SLOT_HOVER : COLOR_SLOT_BG);
        int borderColor = this.selected ? COLOR_BORDER_SELECTED : (hovered ? COLOR_BORDER_HOVER : COLOR_BORDER_IDLE);

        // Background & 1px gothic frame
        graphics.fill(x, y, x + this.size, y + this.size, bgColor);
        drawBorder(graphics, x, y, this.size, this.size, borderColor);

        int iconX = x + (this.size - 16) / 2;
        int iconY = y + (this.size - 16) / 2;

        if (this.displayStack.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;

        graphics.renderItem(this.displayStack, iconX, iconY);
        graphics.renderItemDecorations(font, this.displayStack, iconX, iconY, "");

        // Render Favorite / AutoRefill / Overflow status badges if bound to a VaultEntry
        if (this.entry != null) {
            if (this.entry.isFavorite()) {
                graphics.pose().pushPose();
                graphics.pose().translate(x + 2.0F, y + 1.5F, 250.0F);
                graphics.pose().scale(0.6F, 0.6F, 1.0F);
                graphics.drawString(font, "★", 0, 0, COLOR_FAVORITE_STAR, true);
                graphics.pose().popPose();
            }
            int pipX = x + this.size - 4;
            int pipY = y + 2;
            if (this.entry.isAutoRefill()) {
                graphics.fill(pipX, pipY, pipX + 2, pipY + 2, COLOR_REFILL_PIP);
                pipY += 3;
            }
            if (this.entry.isOverflowDeposit()) {
                graphics.fill(pipX, pipY, pipX + 2, pipY + 2, COLOR_OVERFLOW_PIP);
            }
        }

        // Render compact stack count in bottom-right corner
        if (this.count > 1L || (this.entry != null && this.count == 0L)) {
            String countText = formatCompactCount(this.count);
            float scale = countText.length() >= 4 ? 0.65F : 0.75F;
            int textColor = this.count == 0L ? 0xFF8A7B7B : 0xFFF2ECE1;

            graphics.pose().pushPose();
            graphics.pose().translate(x + this.size - 2.0F, y + this.size - 2.0F, 250.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            int textWidth = font.width(countText);
            graphics.drawString(font, countText, -textWidth, -font.lineHeight + 2, textColor, true);
            graphics.pose().popPose();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }

    /**
     * Draws a 1px rectangle outline (the gothic frame used across the Casket UI).
     */
    public static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    /**
     * Short stack count for slot corners: {@code 999}, {@code 4.2k}, {@code 10k}, {@code 1.5M}.
     */
    public static String formatCompactCount(long count) {
        if (count < 1000L) {
            return Long.toString(count);
        }
        if (count < 10_000L) {
            double k = count / 1000.0D;
            return String.format(Locale.ROOT, "%.1fk", Math.floor(k * 10.0D) / 10.0D);
        }
        if (count < 1_000_000L) {
            return (count / 1000L) + "k";
        }
        double m = count / 1_000_000.0D;
        return String.format(Locale.ROOT, "%.1fM", Math.floor(m * 10.0D) / 10.0D);
    }
}
