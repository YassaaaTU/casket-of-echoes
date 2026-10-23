package com.soulsvault.client.gui.grid;

import com.google.common.collect.Multimap;
import com.soulsvault.core.ItemKey;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;
import java.util.Locale;

/**
 * Display data for one item, computed once per Casket screen and reused by sorting and searching (names, mod id,
 * tags and tooltips are expensive to rebuild for thousands of entries every frame).
 */
final class VaultEntryInfo {
    final ItemKey key;
    final String displayName;
    final String lowerName;
    final String namespace;
    final String registryId;
    final String path;
    final int rarity;

    private Double attackDamage;
    private List<String> tags;
    private String tooltip;

    VaultEntryInfo(ItemKey key) {
        this.key = key;
        ItemStack stack = key.toSingleStack();
        this.displayName = stack.getHoverName().getString();
        this.lowerName = this.displayName.toLowerCase(Locale.ROOT);
        this.namespace = key.getNamespace().toLowerCase(Locale.ROOT);
        this.registryId = key.getRegistryId().toString().toLowerCase(Locale.ROOT);
        this.path = key.getRegistryId().getPath().toLowerCase(Locale.ROOT);
        this.rarity = stack.getRarity().ordinal();
    }

    double attackDamage() {
        if (this.attackDamage == null) {
            Multimap<Attribute, AttributeModifier> modifiers = this.key.toSingleStack().getAttributeModifiers(EquipmentSlot.MAINHAND);
            double sum = 0.0D;
            for (AttributeModifier modifier : modifiers.get(Attributes.ATTACK_DAMAGE)) {
                sum += modifier.getAmount();
            }
            this.attackDamage = sum;
        }
        return this.attackDamage;
    }

    List<String> tags() {
        if (this.tags == null) {
            this.tags = this.key.toSingleStack().getTags()
                    .map(tag -> tag.location().toString().toLowerCase(Locale.ROOT))
                    .toList();
        }
        return this.tags;
    }

    String tooltip() {
        if (this.tooltip == null) {
            Minecraft mc = Minecraft.getInstance();
            StringBuilder builder = new StringBuilder();
            for (Component line : this.key.toSingleStack().getTooltipLines(mc.player, TooltipFlag.Default.NORMAL)) {
                builder.append(line.getString().toLowerCase(Locale.ROOT)).append('\n');
            }
            this.tooltip = builder.toString();
        }
        return this.tooltip;
    }
}
