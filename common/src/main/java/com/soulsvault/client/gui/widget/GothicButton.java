package com.soulsvault.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Dark gothic Souls-styled button widget with weathered bronze borders, muted gold hover glow,
 * and automatic text scaling so labels never clip or overflow.
 */
public final class GothicButton extends AbstractWidget {
    private static final int COLOR_BG_IDLE = 0xF01A161D;
    private static final int COLOR_BG_HOVER = 0xF52B2318;
    private static final int COLOR_BG_ACTIVE = 0xF5241E14;
    private static final int COLOR_BORDER_BRONZE = 0xFF8C7853;
    private static final int COLOR_BORDER_INNER = 0xFF3A3124;
    private static final int COLOR_GOLD = 0xFFD4AF37;
    private static final int COLOR_TEXT_IDLE = 0xFFEAE2D6;

    private final Consumer<GothicButton> onPress;
    private boolean highlighted = false;

    public GothicButton(
            int x,
            int y,
            int width,
            int height,
            Component message,
            Consumer<GothicButton> onPress
    ) {
        super(x, y, width, height, message);
        this.onPress = onPress;
    }

    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (this.active && this.visible && this.onPress != null) {
            this.onPress.accept(this);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        boolean hovered = isHoveredOrFocused();
        int bgColor = hovered ? COLOR_BG_HOVER : (this.highlighted ? COLOR_BG_ACTIVE : COLOR_BG_IDLE);
        int borderColor = (hovered || this.highlighted) ? COLOR_GOLD : COLOR_BORDER_BRONZE;

        graphics.fill(x, y, x + w, y + h, bgColor);
        if (w > 4 && h > 4) {
            VaultItemSlotWidget.drawBorder(graphics, x + 1, y + 1, w - 2, h - 2, COLOR_BORDER_INNER);
        }
        VaultItemSlotWidget.drawBorder(graphics, x, y, w, h, borderColor);

        Font font = Minecraft.getInstance().font;
        String text = getMessage().getString();
        int rawTextW = Math.max(1, font.width(text));
        int maxAllowedW = Math.max(12, w - 8);
        float scale = Math.min(1.0F, (float) maxAllowedW / (float) rawTextW);
        scale = Math.max(0.68F, scale);

        float scaledW = rawTextW * scale;
        float scaledH = (font.lineHeight - 1) * scale;
        float tx = x + (w - scaledW) / 2.0F;
        float ty = y + (h - scaledH) / 2.0F;

        int textColor = !this.active ? 0xFF70685E : ((hovered || this.highlighted) ? COLOR_GOLD : COLOR_TEXT_IDLE);

        graphics.pose().pushPose();
        graphics.pose().translate(tx, ty, 200.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, textColor, true);
        graphics.pose().popPose();
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        defaultButtonNarrationText(narrationElementOutput);
    }
}
