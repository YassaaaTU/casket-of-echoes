package com.soulsvault.client.party;

import com.soulsvault.client.gui.widget.VaultItemSlotWidget;
import com.soulsvault.config.SoulsVaultClientConfig;
import com.soulsvault.config.SoulsVaultClientConfig.HudAnchor;
import com.soulsvault.config.SoulsVaultClientConfig.PartyHudStyle;
import com.soulsvault.party.PartySnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * MMO / Souls co-op style party frames: teammates' names and health on the screen edge.
 * <p>
 * Styles: {@code PORTRAIT} (head-and-shoulders model when the teammate is nearby, face otherwise), {@code BARS}
 * and {@code MINIMAL} (name + low-health / taking-damage indicator). Portrait and bars also show hunger, armor and
 * status effect icons. The first {@code partyHudFullRows} teammates
 * use the chosen style; extra members of large parties (FTB teams, configs above 4) fall back to compact one-line
 * rows, capped with a "+N more" line so the frames never cover the screen. Players hide it with the toggle key or
 * the party GUI.
 */
public final class PartyHudOverlay {
    private static final int MAX_ROWS = 12;
    private static final int PANEL_WIDTH = 108;
    private static final int COLOR_PANEL = 0x99121013;
    private static final int COLOR_BRONZE = 0xFF8C7853;
    private static final int COLOR_GOLD = 0xFFD4AF37;
    private static final int COLOR_TEXT = 0xFFEAE2D6;
    private static final int COLOR_SUB = 0xFFA89E8E;
    private static final int COLOR_OFFLINE = 0xFF6B6257;
    private static final int COLOR_HEALTH = 0xFFA8302A;
    private static final int COLOR_HEALTH_LOW = 0xFFE05050;
    private static final int COLOR_ABSORPTION = 0xFFD4AF37;
    private static final int COLOR_HUNGER = 0xFFB0763A;
    private static final int COLOR_HUNGER_LOW = 0xFFE0904F;
    private static final ResourceLocation GUI_ICONS = new ResourceLocation("textures/gui/icons.png");

    private PartyHudOverlay() {
    }

    public static void render(GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        SoulsVaultClientConfig.Settings config = SoulsVaultClientConfig.get();
        PartySnapshot party = ClientPartyCache.getSnapshot();
        if (party == null || mc.player == null || mc.options.hideGui || !config.partyHudVisible()) {
            return;
        }
        List<PartySnapshot.Member> teammates = new ArrayList<>();
        for (PartySnapshot.Member member : party.members()) {
            if (!member.id().equals(mc.player.getUUID())) {
                teammates.add(member);
            }
        }
        if (teammates.isEmpty()) {
            return;
        }

        int fullRows = Math.max(0, config.partyHudFullRows());
        int shown = Math.min(teammates.size(), MAX_ROWS);
        int hidden = teammates.size() - shown;
        int totalHeight = 0;
        for (int i = 0; i < shown; i++) {
            totalHeight += rowHeight(i < fullRows ? config.partyHudStyle() : PartyHudStyle.MINIMAL) + 2;
        }
        if (hidden > 0) {
            totalHeight += 10;
        }

        float scale = (float) Mth.clamp(config.partyHudScale(), 0.5D, 2.0D);
        int scaledWidth = Math.round(screenWidth / scale);
        int scaledHeight = Math.round(screenHeight / scale);
        HudAnchor anchor = config.partyHudAnchor();
        boolean right = anchor == HudAnchor.TOP_RIGHT || anchor == HudAnchor.MIDDLE_RIGHT;
        boolean middle = anchor == HudAnchor.MIDDLE_LEFT || anchor == HudAnchor.MIDDLE_RIGHT;
        int x = right ? scaledWidth - PANEL_WIDTH - 4 : 4;
        int y = middle ? (scaledHeight - totalHeight) / 2 : 4;

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0F);
        Font font = mc.font;
        String ownDimension = mc.level != null ? mc.level.dimension().location().toString() : "";
        for (int i = 0; i < shown; i++) {
            PartySnapshot.Member member = teammates.get(i);
            PartyHudStyle style = i < fullRows ? config.partyHudStyle() : PartyHudStyle.MINIMAL;
            renderRow(graphics, font, mc, member, style, x, y, member.online() && !member.dimension().equals(ownDimension), scale);
            y += rowHeight(style) + 2;
        }
        if (hidden > 0) {
            graphics.drawString(font, Component.translatable("gui.soulsvault.party.hud.more", hidden), x + 4, y, COLOR_SUB, true);
        }
        graphics.pose().popPose();
    }

    private static int rowHeight(PartyHudStyle style) {
        return switch (style) {
            case PORTRAIT -> 40;
            case BARS -> 30;
            case MINIMAL -> 11;
        };
    }

    private static void renderRow(
            GuiGraphics graphics,
            Font font,
            Minecraft mc,
            PartySnapshot.Member member,
            PartyHudStyle style,
            int x,
            int y,
            boolean elsewhere,
            float scale
    ) {
        int height = rowHeight(style);
        boolean flash = member.recentlyHurt() && (System.currentTimeMillis() / 120L) % 2L == 0L;
        float ratio = member.maxHealth() > 0.0F ? Mth.clamp(member.health() / member.maxHealth(), 0.0F, 1.0F) : 0.0F;
        boolean low = member.online() && !member.dead() && ratio <= 0.25F;

        graphics.fill(x, y, x + PANEL_WIDTH, y + height, COLOR_PANEL);
        graphics.fill(x, y, x + 1, y + height, flash ? 0xFFFFFFFF : (low ? COLOR_HEALTH_LOW : COLOR_BRONZE));

        int textX = x + 4;
        if (style == PartyHudStyle.PORTRAIT) {
            renderPortrait(graphics, mc, member, x + 3, y + 2, 26, height - 4, scale);
            textX = x + 32;
        }

        String name = font.plainSubstrByWidth(member.name(), PANEL_WIDTH - (textX - x) - 14);
        int nameColor = !member.online() ? COLOR_OFFLINE : (member.dead() ? COLOR_HEALTH_LOW : COLOR_TEXT);
        if (style == PartyHudStyle.MINIMAL) {
            int dotColor = !member.online() ? COLOR_OFFLINE : (low || member.dead() || flash ? COLOR_HEALTH_LOW : 0xFF7FB069);
            if (low && (System.currentTimeMillis() / 400L) % 2L == 0L) {
                dotColor = 0xFF5A1A18;
            }
            graphics.fill(textX, y + 3, textX + 4, y + 7, dotColor);
            graphics.drawString(font, name, textX + 7, y + 2, nameColor, true);
            if (elsewhere) {
                graphics.drawString(font, "~", x + PANEL_WIDTH - 8, y + 2, COLOR_SUB, true);
            }
            return;
        }

        graphics.drawString(font, name, textX, y + 3, nameColor, true);
        int barY = y + (style == PartyHudStyle.PORTRAIT ? 14 : 13);
        int barWidth = PANEL_WIDTH - (textX - x) - 4;
        graphics.fill(textX, barY, textX + barWidth, barY + 4, 0xFF1B171E);
        if (member.online() && !member.dead()) {
            int filled = Math.round(barWidth * ratio);
            boolean pulse = low && (System.currentTimeMillis() / 300L) % 2L == 0L;
            graphics.fill(textX, barY, textX + filled, barY + 4, pulse ? COLOR_HEALTH_LOW : COLOR_HEALTH);
            if (member.absorption() > 0.0F && member.maxHealth() > 0.0F) {
                int absorb = Math.min(barWidth, Math.round(barWidth * member.absorption() / member.maxHealth()));
                graphics.fill(textX, barY, textX + absorb, barY + 1, COLOR_ABSORPTION);
            }
        }
        VaultItemSlotWidget.drawBorder(graphics, textX - 1, barY - 1, barWidth + 2, 6, flash ? 0xFFFFFFFF : 0xFF3A3124);

        // Hunger: a thin bar right under health
        int hungerY = barY + 5;
        graphics.fill(textX, hungerY, textX + barWidth, hungerY + 2, 0xFF1B171E);
        if (member.online() && !member.dead()) {
            int fed = Math.round(barWidth * Mth.clamp(member.food() / 20.0F, 0.0F, 1.0F));
            graphics.fill(textX, hungerY, textX + fed, hungerY + 2, member.food() <= 6 ? COLOR_HUNGER_LOW : COLOR_HUNGER);
        }

        int effectsY = hungerY + 4;
        if (style == PartyHudStyle.PORTRAIT) {
            Component status;
            if (!member.online()) {
                status = Component.translatable("gui.soulsvault.party.hud.offline");
            } else if (member.dead()) {
                status = Component.translatable("gui.soulsvault.party.hud.dead");
            } else {
                status = Component.literal(formatHealth(member.health()) + " / " + formatHealth(member.maxHealth())
                        + (elsewhere ? "  ~" : ""));
            }
            graphics.pose().pushPose();
            graphics.pose().translate(textX, hungerY + 4.0F, 0.0F);
            graphics.pose().scale(0.7F, 0.7F, 1.0F);
            graphics.drawString(font, status, 0, 0, COLOR_SUB, true);
            graphics.pose().popPose();
            if (member.online() && member.armor() > 0) {
                renderArmor(graphics, font, member.armor(), x + PANEL_WIDTH - 4, hungerY + 3);
            }
            effectsY = hungerY + 11;
        } else {
            if (elsewhere || !member.online()) {
                graphics.drawString(font, member.online() ? "~" : "×", x + PANEL_WIDTH - 8, y + 3, COLOR_SUB, true);
            } else if (member.armor() > 0) {
                renderArmor(graphics, font, member.armor(), x + PANEL_WIDTH - 4, y + 2);
            }
        }
        if (member.online() && !member.dead()) {
            renderEffects(graphics, mc, member, textX, effectsY, style == PartyHudStyle.PORTRAIT ? 8 : 7);
        }
    }

    /**
     * Armor icon plus value, right-aligned at {@code rightX}.
     */
    private static void renderArmor(GuiGraphics graphics, Font font, int armor, int rightX, int y) {
        String value = Integer.toString(armor);
        graphics.pose().pushPose();
        graphics.pose().translate(rightX, y, 0.0F);
        graphics.pose().scale(0.7F, 0.7F, 1.0F);
        int textWidth = font.width(value);
        graphics.drawString(font, value, -textWidth, 1, COLOR_TEXT, true);
        graphics.blit(GUI_ICONS, -textWidth - 10, 0, 34, 9, 9, 9);
        graphics.pose().popPose();
    }

    /**
     * Status effect icons (harmful first, underlined red); icons blink during their last 10 seconds.
     */
    private static void renderEffects(GuiGraphics graphics, Minecraft mc, PartySnapshot.Member member, int x, int y, int size) {
        boolean blinkOff = (System.currentTimeMillis() / 250L) % 2L == 0L;
        int drawn = 0;
        for (PartySnapshot.Effect effect : member.effects()) {
            ResourceLocation id = ResourceLocation.tryParse(effect.id());
            MobEffect mobEffect = id != null ? BuiltInRegistries.MOB_EFFECT.get(id) : null;
            if (mobEffect == null) {
                continue;
            }
            int iconX = x + drawn * (size + 1);
            if (!(effect.ending() && blinkOff)) {
                TextureAtlasSprite sprite = mc.getMobEffectTextures().get(mobEffect);
                graphics.blit(iconX, y, 0, size, size, sprite);
            }
            if (effect.harmful()) {
                graphics.fill(iconX, y + size, iconX + size, y + size + 1, COLOR_HEALTH_LOW);
            }
            drawn++;
        }
    }

    private static String formatHealth(float value) {
        return value == Math.floor(value) ? Integer.toString((int) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    /**
     * Head-and-shoulders model of the teammate when their entity is loaded nearby, else their skin's face.
     */
    private static void renderPortrait(
            GuiGraphics graphics,
            Minecraft mc,
            PartySnapshot.Member member,
            int x,
            int y,
            int w,
            int h,
            float scale
    ) {
        graphics.fill(x, y, x + w, y + h, 0xFF0D0B0E);
        VaultItemSlotWidget.drawBorder(graphics, x - 1, y - 1, w + 2, h + 2, member.online() ? COLOR_GOLD : COLOR_OFFLINE);
        Player entity = mc.level != null ? mc.level.getPlayerByUUID(member.id()) : null;
        if (entity != null && member.online() && !member.dead()) {
            // Scissor rectangles ignore the pose stack, so apply the HUD scale by hand.
            graphics.enableScissor(Math.round(x * scale), Math.round(y * scale), Math.round((x + w) * scale), Math.round((y + h) * scale));
            int centerX = x + w / 2;
            int feetY = y + h + 26;
            InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, centerX, feetY, 22, centerX, y - 10, entity);
            graphics.disableScissor();
            return;
        }
        int faceSize = Math.min(w, h) - 6;
        PlayerFaceRenderer.draw(graphics, skinOf(mc, member.id()), x + (w - faceSize) / 2, y + (h - faceSize) / 2, faceSize);
        if (!member.online()) {
            graphics.fill(x, y, x + w, y + h, 0x99000000);
        }
    }

    private static ResourceLocation skinOf(Minecraft mc, UUID id) {
        PlayerInfo info = mc.getConnection() != null ? mc.getConnection().getPlayerInfo(id) : null;
        return info != null ? info.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(id);
    }
}
