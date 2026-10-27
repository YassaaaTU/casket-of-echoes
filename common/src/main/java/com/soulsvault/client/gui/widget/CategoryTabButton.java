package com.soulsvault.client.gui.widget;

import com.soulsvault.core.VaultCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;

/**
 * Gothic category tab button for the 7 Souls-like vault categories.
 */
public final class CategoryTabButton extends AbstractWidget {
    private static final int COLOR_BG_IDLE = 0xE8121013;
    private static final int COLOR_BG_HOVER = 0xF01C181F;
    private static final int COLOR_BG_SELECTED = 0xF5262018;
    private static final int COLOR_BORDER_BRONZE = 0xFF8C7853;
    private static final int COLOR_BORDER_MUTED = 0xFF4A3F2C;
    private static final int COLOR_GOLD_GLOW = 0xFFD4AF37;

    private final VaultCategory category;
    private final ItemStack iconStack;
    private final Consumer<VaultCategory> onSelect;
    private boolean selected;
    private int entryCount;

    public CategoryTabButton(
            int x,
            int y,
            int width,
            int height,
            VaultCategory category,
            Consumer<VaultCategory> onSelect
    ) {
        super(x, y, width, height, category.getDisplayName());
        this.category = category;
        this.iconStack = createIconForCategory(category);
        this.onSelect = onSelect;
    }

    private static ItemStack createIconForCategory(VaultCategory category) {
        return switch (category) {
            case ALL -> new ItemStack(Items.ENDER_CHEST);
            case WEAPONS -> new ItemStack(Items.NETHERITE_SWORD);
            case RANGED_AMMO -> new ItemStack(Items.BOW);
            case ARMOR -> new ItemStack(Items.NETHERITE_CHESTPLATE);
            case CURIOS_RELICS -> new ItemStack(Items.HEART_OF_THE_SEA);
            case CONSUMABLES -> new ItemStack(Items.HONEY_BOTTLE);
            case MATERIALS -> new ItemStack(Items.ECHO_SHARD);
            case KEY_LORE -> new ItemStack(Items.NETHER_STAR);
        };
    }

    public VaultCategory getCategory() {
        return this.category;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public void setEntryCount(int entryCount) {
        this.entryCount = Math.max(0, entryCount);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.onSelect != null) {
            this.onSelect.accept(this.category);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int bgColor = this.selected ? COLOR_BG_SELECTED : (this.isHoveredOrFocused() ? COLOR_BG_HOVER : COLOR_BG_IDLE);
        int borderColor = this.selected ? COLOR_GOLD_GLOW : (this.isHoveredOrFocused() ? COLOR_BORDER_BRONZE : COLOR_BORDER_MUTED);

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        // Fill tab background
        graphics.fill(x, y, x + w, y + h, bgColor);

        // Top & side border
        graphics.fill(x, y, x + w, y + 1, borderColor);
        graphics.fill(x, y, x + 1, y + h, borderColor);
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor);

        // Active gold underline accent bar
        if (this.selected) {
            graphics.fill(x + 1, y + h - 2, x + w - 1, y + h, COLOR_GOLD_GLOW);
        } else {
            graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER_BRONZE);
        }

        // Render centered 16x16 icon
        int iconX = x + (w - 16) / 2;
        int iconY = y + (h - 16) / 2 - (this.entryCount > 0 ? 1 : 0);
        graphics.renderItem(this.iconStack, iconX, iconY);

        // Render compact entry count badge in bottom-right corner
        if (this.entryCount > 0) {
            Font font = Minecraft.getInstance().font;
            String countStr = this.entryCount > 99 ? "99+" : Integer.toString(this.entryCount);
            float scale = 0.65F;
            graphics.pose().pushPose();
            graphics.pose().translate(x + w - 2.0F, y + h - 3.0F, 200.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            int textWidth = font.width(countStr);
            int textColor = this.selected ? COLOR_GOLD_GLOW : 0xFFCCC2B0;
            graphics.drawString(font, countStr, -textWidth, -font.lineHeight + 2, textColor, true);
            graphics.pose().popPose();
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
