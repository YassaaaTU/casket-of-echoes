package com.soulsvault.client.gui.widget;

import com.google.common.collect.Multimap;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.soulsvault.client.ClientVaultCache;
import com.soulsvault.client.SoulsVaultKeyMappings;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultCategory;
import com.soulsvault.core.VaultEntry;
import com.soulsvault.network.PacketC2SVaultAction;
import com.soulsvault.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Detailed Dark Souls / Elden Ring style item inspection pane (toggled via 'R').
 * Displays a 3D rotating item preview, durability bar, weapon/armor attributes,
 * platform compat stats (Epic Fight), scrollable lore lines, and inline Favorite / Auto-Refill controls.
 */
public final class ItemInspectionPane {
    private static final int COLOR_PANEL_BG = 0xEE0D0B0E;
    private static final int COLOR_PEDESTAL_BG = 0xF2151218;
    private static final int COLOR_BRONZE = 0xFF8C7853;
    private static final int COLOR_BRONZE_MUTED = 0xFF4A3F2C;
    private static final int COLOR_GOLD = 0xFFD4AF37;
    private static final int COLOR_TEXT_MAIN = 0xFFEAE2D6;
    private static final int COLOR_TEXT_SUB = 0xFFA89E8E;
    private static final int COLOR_BTN_BG = 0xF01B171E;
    private static final int COLOR_BTN_HOVER = 0xF52B2319;

    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private ItemKey inspectedKey = ItemKey.EMPTY;
    private int loreScrollOffset = 0;
    private final List<InlineButton> buttons = new ArrayList<>();

    public ItemInspectionPane(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        rebuildButtons();
    }

    public void setInspectedKey(@Nullable ItemKey key) {
        ItemKey normalized = key != null ? key : ItemKey.EMPTY;
        if (!this.inspectedKey.equals(normalized)) {
            this.inspectedKey = normalized;
            this.loreScrollOffset = 0;
        }
    }

    public ItemKey getInspectedKey() {
        return this.inspectedKey;
    }

    private void rebuildButtons() {
        this.buttons.clear();
        int controlsTop = this.y + this.height - 46;
        int innerX = this.x + 6;
        int innerW = this.width - 12;
        int halfW = (innerW - 4) / 2;

        // Row 1: Favorite Toggle | Auto-Refill Toggle
        this.buttons.add(new InlineButton(innerX, controlsTop, halfW, 14, this::getFavoriteLabel, this::onToggleFavorite));
        this.buttons.add(new InlineButton(innerX + halfW + 4, controlsTop, innerW - halfW - 4, 14, this::getAutoRefillLabel, this::onToggleAutoRefill));

        // Row 2: Overflow Toggle | Carry Cap (-16, -1, +1, +16, Max)
        int row2Y = controlsTop + 16;
        int overflowW = Math.min(92, innerW / 2 - 2);
        this.buttons.add(new InlineButton(innerX, row2Y, overflowW, 14, this::getOverflowLabel, this::onToggleOverflow));

        int stepAreaX = innerX + overflowW + 4;
        int stepAreaW = innerW - overflowW - 4;
        int btnW = Math.max(16, (stepAreaW - 8) / 5);
        this.buttons.add(new InlineButton(stepAreaX, row2Y, btnW, 14, () -> "-16", () -> adjustRefillTarget(-16)));
        this.buttons.add(new InlineButton(stepAreaX + (btnW + 2), row2Y, btnW, 14, () -> "-1", () -> adjustRefillTarget(-1)));
        this.buttons.add(new InlineButton(stepAreaX + (btnW + 2) * 2, row2Y, btnW, 14, () -> "+1", () -> adjustRefillTarget(1)));
        this.buttons.add(new InlineButton(stepAreaX + (btnW + 2) * 3, row2Y, btnW, 14, () -> "+16", () -> adjustRefillTarget(16)));
        this.buttons.add(new InlineButton(stepAreaX + (btnW + 2) * 4, row2Y, stepAreaW - (btnW + 2) * 4, 14, () -> "Max", this::setRefillTargetToMaxStack));
    }

    @Nullable
    private VaultEntry getCurrentEntry() {
        if (this.inspectedKey.isEmpty()) {
            return null;
        }
        return ClientVaultCache.getVaultData().getEntry(this.inspectedKey);
    }

    private String getFavoriteLabel() {
        VaultEntry entry = getCurrentEntry();
        boolean fav = entry != null && entry.isFavorite();
        return Component.translatable(fav ? "gui.soulsvault.inspect.favorite.on" : "gui.soulsvault.inspect.favorite.off").getString();
    }

    private String getAutoRefillLabel() {
        VaultEntry entry = getCurrentEntry();
        boolean refill = entry != null && entry.isAutoRefill();
        return Component.translatable(refill ? "gui.soulsvault.inspect.autorefill.on" : "gui.soulsvault.inspect.autorefill.off").getString();
    }

    private String getOverflowLabel() {
        VaultEntry entry = getCurrentEntry();
        boolean overflow = entry != null && entry.isOverflowDeposit();
        return Component.translatable(overflow ? "gui.soulsvault.inspect.overflow.on" : "gui.soulsvault.inspect.overflow.off").getString();
    }

    private void onToggleFavorite() {
        if (this.inspectedKey.isEmpty()) {
            return;
        }
        Services.PLATFORM.sendToServer(PacketC2SVaultAction.toggleFavorite(this.inspectedKey));
    }

    private void onToggleAutoRefill() {
        if (this.inspectedKey.isEmpty()) {
            return;
        }
        VaultEntry entry = getCurrentEntry();
        boolean currentRefill = entry != null && entry.isAutoRefill();
        boolean currentOverflow = entry != null && entry.isOverflowDeposit();
        int target = entry != null ? entry.getRefillTarget() : Math.max(1, this.inspectedKey.getItem().getMaxStackSize());
        Services.PLATFORM.sendToServer(PacketC2SVaultAction.setRefillTarget(
                this.inspectedKey,
                target,
                !currentRefill,
                currentOverflow
        ));
    }

    private void onToggleOverflow() {
        if (this.inspectedKey.isEmpty()) {
            return;
        }
        VaultEntry entry = getCurrentEntry();
        boolean currentRefill = entry != null && entry.isAutoRefill();
        boolean currentOverflow = entry != null && entry.isOverflowDeposit();
        int target = entry != null ? entry.getRefillTarget() : Math.max(1, this.inspectedKey.getItem().getMaxStackSize());
        Services.PLATFORM.sendToServer(PacketC2SVaultAction.setRefillTarget(
                this.inspectedKey,
                target,
                currentRefill,
                !currentOverflow
        ));
    }

    private void adjustRefillTarget(int delta) {
        if (this.inspectedKey.isEmpty()) {
            return;
        }
        VaultEntry entry = getCurrentEntry();
        boolean currentRefill = entry != null ? entry.isAutoRefill() : true;
        boolean currentOverflow = entry != null && entry.isOverflowDeposit();
        int currentTarget = entry != null ? entry.getRefillTarget() : Math.max(1, this.inspectedKey.getItem().getMaxStackSize());
        int nextTarget = Mth.clamp(currentTarget + delta, 1, 999);
        Services.PLATFORM.sendToServer(PacketC2SVaultAction.setRefillTarget(
                this.inspectedKey,
                nextTarget,
                currentRefill,
                currentOverflow
        ));
    }

    private void setRefillTargetToMaxStack() {
        if (this.inspectedKey.isEmpty()) {
            return;
        }
        VaultEntry entry = getCurrentEntry();
        boolean currentRefill = entry != null ? entry.isAutoRefill() : true;
        boolean currentOverflow = entry != null && entry.isOverflowDeposit();
        int maxStack = Math.max(1, this.inspectedKey.getItem().getMaxStackSize());
        Services.PLATFORM.sendToServer(PacketC2SVaultAction.setRefillTarget(
                this.inspectedKey,
                maxStack,
                currentRefill,
                currentOverflow
        ));
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || this.inspectedKey.isEmpty()) {
            return false;
        }
        for (InlineButton btn : this.buttons) {
            if (btn.isMouseOver(mouseX, mouseY)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                btn.onPress.run();
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height) {
            int step = delta > 0 ? -1 : 1;
            this.loreScrollOffset = Math.max(0, this.loreScrollOffset + step);
            return true;
        }
        return false;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        // Outer gothic pane frame
        graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, COLOR_PANEL_BG);
        VaultItemSlotWidget.drawBorder(graphics, this.x, this.y, this.width, this.height, COLOR_BRONZE);

        if (this.inspectedKey.isEmpty()) {
            KeyMapping inspectKey = SoulsVaultKeyMappings.INSPECT;
            Component keyName = inspectKey.isUnbound()
                    ? Component.translatable("gui.soulsvault.action.inspect_toggle")
                    : Component.literal("[").append(inspectKey.getTranslatedKeyMessage()).append("]");
            List<FormattedCharSequence> emptyLines = font.split(
                    Component.translatable("gui.soulsvault.inspect.empty", keyName),
                    this.width - 20
            );
            int startY = this.y + (this.height - emptyLines.size() * font.lineHeight) / 2;
            for (FormattedCharSequence line : emptyLines) {
                int lineW = font.width(line);
                graphics.drawString(font, line, this.x + (this.width - lineW) / 2, startY, COLOR_TEXT_SUB, false);
                startY += font.lineHeight + 2;
            }
            return;
        }

        ItemStack stack = this.inspectedKey.toSingleStack();
        VaultEntry entry = getCurrentEntry();
        long storedCount = entry != null ? entry.getCount() : 0L;
        int carriedCount = mc.player != null
                ? PlayerVaultData.countInInventory(mc.player.getInventory(), this.inspectedKey)
                : 0;
        int refillTarget = entry != null
                ? entry.getRefillTarget()
                : Math.max(1, stack.getMaxStackSize());

        // Top section: 3D Rotating Preview Pedestal on Left (62x62) + Item Header & Counts on Right
        int pedX = this.x + 6;
        int pedY = this.y + 6;
        int pedSize = 58;
        graphics.fill(pedX, pedY, pedX + pedSize, pedY + pedSize, COLOR_PEDESTAL_BG);
        VaultItemSlotWidget.drawBorder(graphics, pedX, pedY, pedSize, pedSize, COLOR_BRONZE_MUTED);
        renderRotating3DItem(graphics, stack, pedX + pedSize / 2.0F, pedY + pedSize / 2.0F);

        int infoX = pedX + pedSize + 6;
        int infoW = this.width - pedSize - 18;
        int cursorY = pedY + 1;

        // Item Title (truncated cleanly if needed)
        Integer rarityColor = stack.getRarity().color.getColor();
        int titleColor = rarityColor != null ? (0xFF000000 | rarityColor) : COLOR_GOLD;
        String title = font.plainSubstrByWidth(stack.getHoverName().getString(), infoW);
        graphics.drawString(font, title, infoX, cursorY, titleColor, true);
        cursorY += 10;

        // Category & Mod Namespace
        VaultCategory category = VaultCategory.classify(this.inspectedKey);
        String subHeader = category.getDisplayName().getString() + "  @" + this.inspectedKey.getNamespace();
        graphics.drawString(font, font.plainSubstrByWidth(subHeader, infoW), infoX, cursorY, COLOR_TEXT_SUB, false);
        cursorY += 10;

        // Stored vs Carried vs Carry Cap
        String countsLine = Component.translatable("gui.soulsvault.inspect.stored_count", storedCount).getString()
                + "  |  " + Component.translatable("gui.soulsvault.inspect.carried_count", carriedCount).getString();
        graphics.drawString(font, font.plainSubstrByWidth(countsLine, infoW), infoX, cursorY, COLOR_TEXT_MAIN, false);
        cursorY += 10;

        String capLine = Component.translatable("gui.soulsvault.inspect.refill_target", refillTarget).getString();
        graphics.drawString(font, capLine, infoX, cursorY, COLOR_GOLD, false);
        cursorY += 11;

        // Durability Bar if damageable
        if (stack.isDamageableItem()) {
            int maxDmg = stack.getMaxDamage();
            int curDur = Math.max(0, maxDmg - stack.getDamageValue());
            float ratio = maxDmg > 0 ? (float) curDur / (float) maxDmg : 1.0F;
            int barW = infoW - 4;
            graphics.fill(infoX, cursorY, infoX + barW, cursorY + 4, 0xFF221D1A);
            int fillW = Math.round(barW * ratio);
            int barColor = ratio > 0.5F ? 0xFF5FA868 : (ratio > 0.25F ? 0xFFD4AF37 : 0xFFC84B39);
            graphics.fill(infoX, cursorY, infoX + fillW, cursorY + 4, barColor);
            cursorY += 6;
            String durText = Component.translatable("gui.soulsvault.inspect.durability", curDur, maxDmg).getString();
            graphics.pose().pushPose();
            graphics.pose().translate(infoX, cursorY, 0.0F);
            graphics.pose().scale(0.75F, 0.75F, 1.0F);
            graphics.drawString(font, durText, 0, 0, COLOR_TEXT_SUB, false);
            graphics.pose().popPose();
        }

        // Divider below pedestal
        int bodyTop = pedY + pedSize + 5;
        int controlsTop = this.y + this.height - 46;
        graphics.fill(this.x + 6, bodyTop - 2, this.x + this.width - 6, bodyTop - 1, COLOR_BRONZE_MUTED);

        // Collect Attributes + Lore lines into scrollable body area
        List<Component> detailLines = collectAttributeAndLoreLines(mc, stack);
        List<FormattedCharSequence> wrappedLines = new ArrayList<>();
        int wrapWidth = this.width - 14;
        for (Component comp : detailLines) {
            wrappedLines.addAll(font.split(comp, wrapWidth));
        }

        int availableBodyH = Math.max(12, controlsTop - bodyTop - 4);
        int maxVisibleLines = Math.max(1, availableBodyH / (font.lineHeight + 1));
        int maxScroll = Math.max(0, wrappedLines.size() - maxVisibleLines);
        this.loreScrollOffset = Mth.clamp(this.loreScrollOffset, 0, maxScroll);

        int lineY = bodyTop + 1;
        for (int i = this.loreScrollOffset; i < wrappedLines.size() && i < this.loreScrollOffset + maxVisibleLines; i++) {
            graphics.drawString(font, wrappedLines.get(i), this.x + 7, lineY, COLOR_TEXT_MAIN, false);
            lineY += font.lineHeight + 1;
        }

        // Scrollbar indicator if lore overflows
        if (maxScroll > 0) {
            int trackX = this.x + this.width - 4;
            int trackH = availableBodyH;
            graphics.fill(trackX, bodyTop, trackX + 2, bodyTop + trackH, 0x802A241C);
            int thumbH = Math.max(10, (trackH * maxVisibleLines) / wrappedLines.size());
            int thumbY = bodyTop + ((trackH - thumbH) * this.loreScrollOffset) / maxScroll;
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, COLOR_GOLD);
        }

        // Divider above inline controls
        graphics.fill(this.x + 6, controlsTop - 3, this.x + this.width - 6, controlsTop - 2, COLOR_BRONZE_MUTED);

        // Render Inline Pinning & Refill Buttons
        for (InlineButton btn : this.buttons) {
            btn.render(graphics, font, mouseX, mouseY);
        }
    }

    private List<Component> collectAttributeAndLoreLines(Minecraft mc, ItemStack stack) {
        List<Component> lines = new ArrayList<>();

        // 1. Extract combat & armor attributes
        double attackDamage = getAttributeSum(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE);
        double attackSpeed = getAttributeSum(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_SPEED);
        double armor = 0.0D;
        double toughness = 0.0D;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND}) {
            armor += getAttributeSum(stack, slot, Attributes.ARMOR);
            toughness += getAttributeSum(stack, slot, Attributes.ARMOR_TOUGHNESS);
        }

        List<Component> extraCompatStats = Services.PLATFORM.getExtraInspectionStats(stack);
        if (attackDamage != 0.0D || attackSpeed != 0.0D || armor != 0.0D || toughness != 0.0D || !extraCompatStats.isEmpty()) {
            lines.add(Component.translatable("gui.soulsvault.inspect.attributes").withStyle(ChatFormatting.GOLD));
            if (attackDamage != 0.0D) {
                lines.add(Component.literal(String.format(Locale.ROOT, "  • Attack Damage: +%.1f", 1.0D + attackDamage))
                        .withStyle(ChatFormatting.GRAY));
            }
            if (attackSpeed != 0.0D) {
                lines.add(Component.literal(String.format(Locale.ROOT, "  • Attack Speed: %.2f", 4.0D + attackSpeed))
                        .withStyle(ChatFormatting.GRAY));
            }
            if (armor != 0.0D) {
                lines.add(Component.literal(String.format(Locale.ROOT, "  • Armor Defense: +%.1f", armor))
                        .withStyle(ChatFormatting.BLUE));
            }
            if (toughness != 0.0D) {
                lines.add(Component.literal(String.format(Locale.ROOT, "  • Armor Toughness: +%.1f", toughness))
                        .withStyle(ChatFormatting.BLUE));
            }
            lines.addAll(extraCompatStats);
        }

        // 2. Append full item tooltip & lore lines (skipping line 0 which is the item title)
        List<Component> tooltip = stack.getTooltipLines(mc.player, TooltipFlag.Default.NORMAL);
        if (tooltip.size() > 1) {
            lines.add(Component.translatable("gui.soulsvault.inspect.lore").withStyle(ChatFormatting.GOLD));
            for (int i = 1; i < tooltip.size(); i++) {
                Component line = tooltip.get(i);
                if (!line.getString().isBlank()) {
                    lines.add(line);
                }
            }
        }

        return lines;
    }

    private static double getAttributeSum(ItemStack stack, EquipmentSlot slot, Attribute attribute) {
        Multimap<Attribute, AttributeModifier> map = stack.getAttributeModifiers(slot);
        Collection<AttributeModifier> mods = map.get(attribute);
        if (mods == null || mods.isEmpty()) {
            return 0.0D;
        }
        double sum = 0.0D;
        for (AttributeModifier mod : mods) {
            sum += mod.getAmount();
        }
        return sum;
    }

    private void renderRotating3DItem(GuiGraphics graphics, ItemStack stack, float centerX, float centerY) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        float angle = (System.currentTimeMillis() % 7200L) / 20.0F;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 150.0F);
        pose.mulPoseMatrix(new Matrix4f().scaling(1.0F, -1.0F, 1.0F));
        pose.scale(38.0F, 38.0F, 38.0F);
        pose.mulPose(Axis.XP.rotationDegrees(16.0F));
        pose.mulPose(Axis.YP.rotationDegrees(angle));

        Lighting.setupFor3DItems();
        itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.GUI,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                pose,
                graphics.bufferSource(),
                mc.level,
                0
        );
        graphics.flush();
        Lighting.setupForFlatItems();
        pose.popPose();
    }

    private record InlineButton(
            int x,
            int y,
            int width,
            int height,
            java.util.function.Supplier<String> labelSupplier,
            Runnable onPress
    ) {
        boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= this.x && mouseX < (this.x + this.width)
                    && mouseY >= this.y && mouseY < (this.y + this.height);
        }

        void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            boolean hovered = isMouseOver(mouseX, mouseY);
            int bg = hovered ? COLOR_BTN_HOVER : COLOR_BTN_BG;
            int border = hovered ? COLOR_GOLD : COLOR_BRONZE;

            graphics.fill(this.x, this.y, this.x + this.width, this.y + this.height, bg);
            VaultItemSlotWidget.drawBorder(graphics, this.x, this.y, this.width, this.height, border);

            String text = this.labelSupplier.get();
            float scale = 0.75F;
            int textW = Math.round(font.width(text) * scale);
            float tx = this.x + (this.width - textW) / 2.0F;
            float ty = this.y + (this.height - 6) / 2.0F;

            graphics.pose().pushPose();
            graphics.pose().translate(tx, ty, 200.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(font, text, 0, 0, hovered ? COLOR_GOLD : COLOR_TEXT_MAIN, true);
            graphics.pose().popPose();
        }
    }
}
