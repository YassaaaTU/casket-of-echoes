package com.soulsvault.core;

import com.soulsvault.api.EchoSlotType;
import net.minecraft.SharedConstants;
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
 * Saved equipment loadout snapshot ("Build Echo"): hands, armor, Curios, and any registered
 * {@link EchoSlotType} groups (e.g. the hotbar), plus a player-chosen name.
 */
public final class BuildEcho {
    public static final int MAX_NAME_LENGTH = 32;

    private static final String NBT_INDEX = "Index";
    private static final String NBT_NAME = "Name";
    private static final String NBT_MAIN_HAND = "MainHand";
    private static final String NBT_OFF_HAND = "OffHand";
    private static final String NBT_ARMOR = "Armor";
    private static final String NBT_CURIOS = "Curios";
    private static final String NBT_SLOT_ID = "SlotId";
    private static final String NBT_ITEMS = "Items";
    private static final String NBT_EXTRAS = "Extras";
    private static final String NBT_TYPE = "Type";
    private static final String NBT_COUNT = "Count";
    private static final String NBT_TOGGLES = "Toggles";
    private static final String NBT_ENABLED = "Enabled";
    private static final String NBT_SELECTED = "SelectedHotbar";

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    /** An item plus the stack size to restore (1 for equipment). */
    public record EchoStack(ItemKey key, int count) {
        public EchoStack {
            key = Objects.requireNonNullElse(key, ItemKey.EMPTY);
            count = key.isEmpty() ? 0 : Math.max(1, count);
        }

        public boolean isEmpty() {
            return this.key.isEmpty();
        }
    }

    private final int index;
    private final String name;
    private final ItemKey mainHand;
    private final ItemKey offHand;
    private final Map<EquipmentSlot, ItemKey> armorSlots;
    private final Map<String, List<ItemKey>> curioSlots;
    private final Map<String, List<EchoStack>> extraSlots;
    private final Map<String, Boolean> typeToggles;
    private final int selectedHotbarSlot;

    public BuildEcho(
            int index,
            String name,
            ItemKey mainHand,
            ItemKey offHand,
            Map<EquipmentSlot, ItemKey> armorSlots,
            Map<String, List<ItemKey>> curioSlots
    ) {
        this(index, name, mainHand, offHand, armorSlots, curioSlots, Map.of(), Map.of(), -1);
    }

    public BuildEcho(
            int index,
            String name,
            ItemKey mainHand,
            ItemKey offHand,
            Map<EquipmentSlot, ItemKey> armorSlots,
            Map<String, List<ItemKey>> curioSlots,
            Map<String, List<EchoStack>> extraSlots,
            Map<String, Boolean> typeToggles,
            int selectedHotbarSlot
    ) {
        this.index = Math.max(0, index);
        this.name = sanitizeName(name, this.index);
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

        Map<String, List<EchoStack>> extrasCopy = new LinkedHashMap<>();
        if (extraSlots != null) {
            for (Map.Entry<String, List<EchoStack>> entry : extraSlots.entrySet()) {
                extrasCopy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
        }
        this.extraSlots = Collections.unmodifiableMap(extrasCopy);
        this.typeToggles = Collections.unmodifiableMap(new LinkedHashMap<>(typeToggles != null ? typeToggles : Map.of()));
        this.selectedHotbarSlot = selectedHotbarSlot;
    }

    /**
     * Name of an unnamed slot ("Echo 1", "Echo 2", ...).
     */
    public static String defaultName(int index) {
        return "Echo " + (index + 1);
    }

    /**
     * Trims, drops control and formatting characters, caps the length, and falls back to the default name.
     */
    private static String sanitizeName(String name, int index) {
        String trimmed = name == null ? "" : SharedConstants.filterText(name).trim();
        if (trimmed.isEmpty()) {
            return defaultName(index);
        }
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }

    /**
     * An unnamed slot with nothing saved.
     */
    public static BuildEcho empty(int index) {
        return new BuildEcho(index, defaultName(index), ItemKey.EMPTY, ItemKey.EMPTY, Map.of(), Map.of());
    }

    /**
     * Whether nothing at all is saved in this Echo.
     */
    public boolean isEmpty() {
        if (!this.mainHand.isEmpty() || !this.offHand.isEmpty()) {
            return false;
        }
        for (ItemKey key : this.armorSlots.values()) {
            if (!key.isEmpty()) {
                return false;
            }
        }
        for (List<ItemKey> keys : this.curioSlots.values()) {
            for (ItemKey key : keys) {
                if (!key.isEmpty()) {
                    return false;
                }
            }
        }
        for (List<EchoStack> stacks : this.extraSlots.values()) {
            for (EchoStack stack : stacks) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
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

    public Map<String, List<ItemKey>> getCurioSlots() {
        return this.curioSlots;
    }

    public List<EchoStack> getExtraSlots(EchoSlotType type) {
        return this.extraSlots.getOrDefault(type.id().toString(), List.of());
    }

    public Map<String, Boolean> getTypeToggles() {
        return this.typeToggles;
    }

    /**
     * Whether swapping to this Echo restores {@code type}'s slots (the player's toggle, else the type's default).
     */
    public boolean isTypeEnabled(EchoSlotType type) {
        return this.typeToggles.getOrDefault(type.id().toString(), type.enabledByDefault());
    }

    public int getSelectedHotbarSlot() {
        return this.selectedHotbarSlot;
    }

    public BuildEcho withName(String newName) {
        return new BuildEcho(this.index, newName, this.mainHand, this.offHand, this.armorSlots, this.curioSlots,
                this.extraSlots, this.typeToggles, this.selectedHotbarSlot);
    }

    public BuildEcho withTypeToggles(Map<String, Boolean> newToggles) {
        return new BuildEcho(this.index, this.name, this.mainHand, this.offHand, this.armorSlots, this.curioSlots,
                this.extraSlots, newToggles, this.selectedHotbarSlot);
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

        ListTag extrasList = new ListTag();
        for (Map.Entry<String, List<EchoStack>> entry : this.extraSlots.entrySet()) {
            CompoundTag group = new CompoundTag();
            group.putString(NBT_TYPE, entry.getKey());
            ListTag itemsTag = new ListTag();
            for (EchoStack stack : entry.getValue()) {
                CompoundTag stackTag = new CompoundTag();
                stackTag.put(NBT_ITEMS, stack.key().toTag());
                stackTag.putInt(NBT_COUNT, stack.count());
                itemsTag.add(stackTag);
            }
            group.put(NBT_ITEMS, itemsTag);
            extrasList.add(group);
        }
        tag.put(NBT_EXTRAS, extrasList);

        ListTag togglesList = new ListTag();
        for (Map.Entry<String, Boolean> entry : this.typeToggles.entrySet()) {
            CompoundTag toggle = new CompoundTag();
            toggle.putString(NBT_TYPE, entry.getKey());
            toggle.putBoolean(NBT_ENABLED, entry.getValue());
            togglesList.add(toggle);
        }
        tag.put(NBT_TOGGLES, togglesList);
        tag.putInt(NBT_SELECTED, this.selectedHotbarSlot);
        return tag;
    }

    public static BuildEcho fromTag(CompoundTag tag) {
        int index = tag.getInt(NBT_INDEX);
        String name = tag.contains(NBT_NAME, Tag.TAG_STRING) ? tag.getString(NBT_NAME) : defaultName(index);
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
        ListTag curiosList = tag.getList(NBT_CURIOS, Tag.TAG_COMPOUND);
        for (int i = 0; i < curiosList.size(); i++) {
            CompoundTag slotGroup = curiosList.getCompound(i);
            ListTag itemsTag = slotGroup.getList(NBT_ITEMS, Tag.TAG_COMPOUND);
            List<ItemKey> keys = new ArrayList<>(itemsTag.size());
            for (int j = 0; j < itemsTag.size(); j++) {
                keys.add(ItemKey.fromTag(itemsTag.getCompound(j)));
            }
            curioSlots.put(slotGroup.getString(NBT_SLOT_ID), keys);
        }

        Map<String, List<EchoStack>> extraSlots = new LinkedHashMap<>();
        ListTag extrasList = tag.getList(NBT_EXTRAS, Tag.TAG_COMPOUND);
        for (int i = 0; i < extrasList.size(); i++) {
            CompoundTag group = extrasList.getCompound(i);
            ListTag itemsTag = group.getList(NBT_ITEMS, Tag.TAG_COMPOUND);
            List<EchoStack> stacks = new ArrayList<>(itemsTag.size());
            for (int j = 0; j < itemsTag.size(); j++) {
                CompoundTag stackTag = itemsTag.getCompound(j);
                stacks.add(new EchoStack(ItemKey.fromTag(stackTag.getCompound(NBT_ITEMS)), stackTag.getInt(NBT_COUNT)));
            }
            extraSlots.put(group.getString(NBT_TYPE), stacks);
        }

        Map<String, Boolean> toggles = new LinkedHashMap<>();
        ListTag togglesList = tag.getList(NBT_TOGGLES, Tag.TAG_COMPOUND);
        for (int i = 0; i < togglesList.size(); i++) {
            CompoundTag toggle = togglesList.getCompound(i);
            toggles.put(toggle.getString(NBT_TYPE), toggle.getBoolean(NBT_ENABLED));
        }
        int selected = tag.contains(NBT_SELECTED, Tag.TAG_INT) ? tag.getInt(NBT_SELECTED) : -1;
        return new BuildEcho(index, name, mainHand, offHand, armorSlots, curioSlots, extraSlots, toggles, selected);
    }

    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeVarInt(this.index);
        buf.writeUtf(this.name, MAX_NAME_LENGTH * 4);
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
        buf.writeVarInt(this.extraSlots.size());
        for (Map.Entry<String, List<EchoStack>> entry : this.extraSlots.entrySet()) {
            buf.writeUtf(entry.getKey(), 128);
            buf.writeVarInt(entry.getValue().size());
            for (EchoStack stack : entry.getValue()) {
                stack.key().toNetwork(buf);
                buf.writeVarInt(stack.count());
            }
        }
        buf.writeVarInt(this.typeToggles.size());
        for (Map.Entry<String, Boolean> entry : this.typeToggles.entrySet()) {
            buf.writeUtf(entry.getKey(), 128);
            buf.writeBoolean(entry.getValue());
        }
        buf.writeVarInt(this.selectedHotbarSlot + 1);
    }

    public static BuildEcho fromNetwork(FriendlyByteBuf buf) {
        int index = buf.readVarInt();
        String name = buf.readUtf(MAX_NAME_LENGTH * 4);
        ItemKey mainHand = ItemKey.fromNetwork(buf);
        ItemKey offHand = ItemKey.fromNetwork(buf);
        EnumMap<EquipmentSlot, ItemKey> armorSlots = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            armorSlots.put(slot, ItemKey.fromNetwork(buf));
        }
        int curioGroupCount = Math.min(256, buf.readVarInt());
        Map<String, List<ItemKey>> curioSlots = new LinkedHashMap<>();
        for (int i = 0; i < curioGroupCount; i++) {
            String slotId = buf.readUtf(64);
            int keyCount = Math.min(256, buf.readVarInt());
            List<ItemKey> keys = new ArrayList<>(keyCount);
            for (int j = 0; j < keyCount; j++) {
                keys.add(ItemKey.fromNetwork(buf));
            }
            curioSlots.put(slotId, keys);
        }
        int extraGroupCount = Math.min(64, buf.readVarInt());
        Map<String, List<EchoStack>> extraSlots = new LinkedHashMap<>();
        for (int i = 0; i < extraGroupCount; i++) {
            String typeId = buf.readUtf(128);
            int stackCount = Math.min(256, buf.readVarInt());
            List<EchoStack> stacks = new ArrayList<>(stackCount);
            for (int j = 0; j < stackCount; j++) {
                stacks.add(new EchoStack(ItemKey.fromNetwork(buf), buf.readVarInt()));
            }
            extraSlots.put(typeId, stacks);
        }
        int toggleCount = Math.min(64, buf.readVarInt());
        Map<String, Boolean> toggles = new LinkedHashMap<>();
        for (int i = 0; i < toggleCount; i++) {
            toggles.put(buf.readUtf(128), buf.readBoolean());
        }
        int selected = buf.readVarInt() - 1;
        return new BuildEcho(index, name, mainHand, offHand, armorSlots, curioSlots, extraSlots, toggles, selected);
    }
}
