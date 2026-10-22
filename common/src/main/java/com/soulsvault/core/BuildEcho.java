package com.soulsvault.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Saved equipment and Curios loadout snapshot ("Build Echo").
 */
public final class BuildEcho {
    private static final String NBT_INDEX = "Index";
    private static final String NBT_NAME = "Name";
    private static final String NBT_MAIN_HAND = "MainHand";
    private static final String NBT_OFF_HAND = "OffHand";
    private static final String NBT_ARMOR = "Armor";
    private static final String NBT_CURIOS = "Curios";
    private static final String NBT_SLOT_ID = "SlotId";
    private static final String NBT_ITEMS = "Items";

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private final int index;
    private final String name;
    private final ItemKey mainHand;
    private final ItemKey offHand;
    private final Map<EquipmentSlot, ItemKey> armorSlots;
    private final Map<String, List<ItemKey>> curioSlots;

    public BuildEcho(
            int index,
            String name,
            ItemKey mainHand,
            ItemKey offHand,
            Map<EquipmentSlot, ItemKey> armorSlots,
            Map<String, List<ItemKey>> curioSlots
    ) {
        this.index = Math.max(0, index);
        this.name = Objects.requireNonNullElse(name, "Echo " + (this.index + 1));
        this.mainHand = Objects.requireNonNullElse(mainHand, ItemKey.EMPTY);
        this.offHand = Objects.requireNonNullElse(offHand, ItemKey.EMPTY);

        EnumMap<EquipmentSlot, ItemKey> armorCopy = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemKey key = armorSlots != null ? armorSlots.get(slot) : null;
            armorCopy.put(slot, key != null ? key : ItemKey.EMPTY);
        }
        this.armorSlots = Collections.unmodifiableMap(armorCopy);

        Map<String, List<ItemKey>> curiosCopy = new LinkedHashMap<>();
        if (curioSlots != null) {
            for (Map.Entry<String, List<ItemKey>> entry : curioSlots.entrySet()) {
                curiosCopy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        this.curioSlots = Collections.unmodifiableMap(curiosCopy);
    }

    public int getIndex() {
        return this.index;
    }

    public String getName() {
        return this.name;
    }

    public ItemKey getMainHand() {
        return this.mainHand;
    }

    public ItemKey getOffHand() {
        return this.offHand;
    }

    public ItemKey getArmor(EquipmentSlot slot) {
        return this.armorSlots.getOrDefault(slot, ItemKey.EMPTY);
    }

    public Map<EquipmentSlot, ItemKey> getArmorSlots() {
        return this.armorSlots;
    }

    public Map<String, List<ItemKey>> getCurioSlots() {
        return this.curioSlots;
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt(NBT_INDEX, this.index);
        tag.putString(NBT_NAME, this.name);
        if (!this.mainHand.isEmpty()) {
            tag.put(NBT_MAIN_HAND, this.mainHand.toTag());
        }
        if (!this.offHand.isEmpty()) {
            tag.put(NBT_OFF_HAND, this.offHand.toTag());
        }

        CompoundTag armorTag = new CompoundTag();
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemKey key = getArmor(slot);
            if (!key.isEmpty()) {
                armorTag.put(slot.getName(), key.toTag());
            }
        }
        tag.put(NBT_ARMOR, armorTag);

        ListTag curiosList = new ListTag();
        for (Map.Entry<String, List<ItemKey>> entry : this.curioSlots.entrySet()) {
            CompoundTag slotGroup = new CompoundTag();
            slotGroup.putString(NBT_SLOT_ID, entry.getKey());
            ListTag itemsTag = new ListTag();
            for (ItemKey itemKey : entry.getValue()) {
                itemsTag.add(itemKey.toTag());
            }
            slotGroup.put(NBT_ITEMS, itemsTag);
            curiosList.add(slotGroup);
        }
        tag.put(NBT_CURIOS, curiosList);
        return tag;
    }

    public static BuildEcho fromTag(CompoundTag tag) {
        int index = tag.getInt(NBT_INDEX);
        String name = tag.contains(NBT_NAME, Tag.TAG_STRING) ? tag.getString(NBT_NAME) : "Echo " + (index + 1);
        ItemKey mainHand = tag.contains(NBT_MAIN_HAND, Tag.TAG_COMPOUND)
                ? ItemKey.fromTag(tag.getCompound(NBT_MAIN_HAND))
                : ItemKey.EMPTY;
        ItemKey offHand = tag.contains(NBT_OFF_HAND, Tag.TAG_COMPOUND)
                ? ItemKey.fromTag(tag.getCompound(NBT_OFF_HAND))
                : ItemKey.EMPTY;

        EnumMap<EquipmentSlot, ItemKey> armorSlots = new EnumMap<>(EquipmentSlot.class);
        if (tag.contains(NBT_ARMOR, Tag.TAG_COMPOUND)) {
            CompoundTag armorTag = tag.getCompound(NBT_ARMOR);
            for (EquipmentSlot slot : ARMOR_SLOTS) {
                if (armorTag.contains(slot.getName(), Tag.TAG_COMPOUND)) {
                    armorSlots.put(slot, ItemKey.fromTag(armorTag.getCompound(slot.getName())));
                }
            }
        }

        Map<String, List<ItemKey>> curioSlots = new LinkedHashMap<>();
        if (tag.contains(NBT_CURIOS, Tag.TAG_LIST)) {
            ListTag curiosList = tag.getList(NBT_CURIOS, Tag.TAG_COMPOUND);
            for (int i = 0; i < curiosList.size(); i++) {
                CompoundTag slotGroup = curiosList.getCompound(i);
                String slotId = slotGroup.getString(NBT_SLOT_ID);
                ListTag itemsTag = slotGroup.getList(NBT_ITEMS, Tag.TAG_COMPOUND);
                List<ItemKey> keys = new ArrayList<>(itemsTag.size());
                for (int j = 0; j < itemsTag.size(); j++) {
                    keys.add(ItemKey.fromTag(itemsTag.getCompound(j)));
                }
                curioSlots.put(slotId, keys);
            }
        }

        return new BuildEcho(index, name, mainHand, offHand, armorSlots, curioSlots);
    }

    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeVarInt(this.index);
        buf.writeUtf(this.name, 64);
        this.mainHand.toNetwork(buf);
        this.offHand.toNetwork(buf);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            getArmor(slot).toNetwork(buf);
        }
        buf.writeVarInt(this.curioSlots.size());
        for (Map.Entry<String, List<ItemKey>> entry : this.curioSlots.entrySet()) {
            buf.writeUtf(entry.getKey(), 64);
            List<ItemKey> keys = entry.getValue();
            buf.writeVarInt(keys.size());
            for (ItemKey key : keys) {
                key.toNetwork(buf);
            }
        }
    }

    public static BuildEcho fromNetwork(FriendlyByteBuf buf) {
        int index = buf.readVarInt();
        String name = buf.readUtf(64);
        ItemKey mainHand = ItemKey.fromNetwork(buf);
        ItemKey offHand = ItemKey.fromNetwork(buf);
        EnumMap<EquipmentSlot, ItemKey> armorSlots = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            armorSlots.put(slot, ItemKey.fromNetwork(buf));
        }
        int curioGroupCount = buf.readVarInt();
        Map<String, List<ItemKey>> curioSlots = new LinkedHashMap<>();
        for (int i = 0; i < curioGroupCount; i++) {
            String slotId = buf.readUtf(64);
            int keyCount = buf.readVarInt();
            List<ItemKey> keys = new ArrayList<>(keyCount);
            for (int j = 0; j < keyCount; j++) {
                keys.add(ItemKey.fromNetwork(buf));
            }
            curioSlots.put(slotId, keys);
        }
        return new BuildEcho(index, name, mainHand, offHand, armorSlots, curioSlots);
    }
}
