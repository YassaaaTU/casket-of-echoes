package com.soulsvault.client.gui.widget;

import com.soulsvault.core.VaultEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Virtualized dark gothic item slot renderer supporting stack counts > 64 with compact formatting
 * (e.g., 999, 4.2k, 10k) and Favorite / Auto-Refill / Overflow badges.
 */
public final class VaultItemSlotWidget {
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

    private final int x;
    private final int y;
    private final int size;
    @Nullable
    private VaultEntry entry;
    private ItemStack displayStack = ItemStack.EMPTY;
    private long count;
    private boolean selected;

    public VaultItemSlotWidget(int x, int y, int size) {
        this.x = x;
        this.y = y;
        this.size = size;
    }

    public void bindVaultEntry(@Nullable VaultEntry entry, boolean selected) {
        this.entry = entry;
        if (entry != null && !entry.getKey().isEmpty()) {
            this.displayStack = entry.createDisplayStack();
            this.count = entry.getCount();
        } else {
            this.displayStack = ItemStack.EMPTY;
            this.count = 0L;
        }
        this.selected = selected;
    }

    public void bindItemStack(ItemStack stack, boolean selected) {
        this.entry = null;
        if (stack != null && !stack.isEmpty()) {
            this.displayStack = stack;
            this.count = stack.getCount();
        } else {
            this.displayStack = ItemStack.EMPTY;
            this.count = 0L;
        }
        this.selected = selected;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public int getSize() {
        return this.size;
    }

    @Nullable
    public VaultEntry getEntry() {
        return this.entry;
    }

    public ItemStack getDisplayStack() {
        return this.displayStack;
    }

    public long getCount() {
        return this.count;
    }

    public boolean isEmpty() {
        return this.displayStack.isEmpty();
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseX < (this.x + this.size)
                && mouseY >= this.y && mouseY < (this.y + this.size);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = isMouseOver(mouseX, mouseY);
        int bgColor = this.selected ? COLOR_SLOT_SELECTED : (hovered ? COLOR_SLOT_HOVER : COLOR_SLOT_BG);
        int borderColor = this.selected ? COLOR_BORDER_SELECTED : (hovered ? COLOR_BORDER_HOVER : COLOR_BORDER_IDLE);

        // Background & 1px gothic frame
        graphics.fill(this.x, this.y, this.x + this.size, this.y + this.size, bgColor);
        drawBorder(graphics, this.x, this.y, this.size, this.size, borderColor);

        if (this.displayStack.isEmpty()) {
            return;
        }

        int iconX = this.x + (this.size - 16) / 2;
        int iconY = this.y + (this.size - 16) / 2;
        Font font = Minecraft.getInstance().font;

        graphics.renderItem(this.displayStack, iconX, iconY);
        // Pass empty string to render durability/cooldown bars without vanilla's 2-digit stack count
        graphics.renderItemDecorations(font, this.displayStack, iconX, iconY, "");

        // Render Favorite / AutoRefill / Overflow status badges if bound to a VaultEntry
        if (this.entry != null) {
            if (this.entry.isFavorite()) {
                graphics.pose().pushPose();
                graphics.pose().translate(this.x + 2.0F, this.y + 1.5F, 250.0F);
                graphics.pose().scale(0.6F, 0.6F, 1.0F);
                graphics.drawString(font, "★", 0, 0, COLOR_FAVORITE_STAR, true);
                graphics.pose().popPose();
            }
            int pipX = this.x + this.size - 4;
            int pipY = this.y + 2;
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
            graphics.pose().translate(this.x + this.size - 2.0F, this.y + this.size - 2.0F, 250.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            int textWidth = font.width(countText);
            graphics.drawString(font, countText, -textWidth, -font.lineHeight + 2, textColor, true);
            graphics.pose().popPose();
        }
    }

    public static void drawBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

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
