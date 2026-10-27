package com.soulsvault.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Immutable deduplication key encapsulating {@code (Item, Optional<CompoundTag>)} with a precomputed hash.
 * <p>
 * Isolates all {@link CompoundTag} identity checks in one place for forward compatibility with
 * Minecraft 1.21+ {@code DataComponentPatch}. Preserves full NBT for Armourer's Workshop skins,
 * Apotheosis affixes, and Epic Fight innate skills.
 */
public final class ItemKey {
    public static final ItemKey EMPTY = new ItemKey(Items.AIR, null);

    private static final String NBT_ITEM = "Item";
    private static final String NBT_TAG = "Tag";

    private final Item item;
    @Nullable
    private final CompoundTag tag;
    private final int cachedHash;
    private volatile int nbtSizeBytes = -1;
    private volatile int maxStackSize;

    public ItemKey(Item item, @Nullable CompoundTag tag) {
        this.item = Objects.requireNonNull(item, "item cannot be null");
        this.tag = (tag == null || tag.isEmpty()) ? null : tag.copy();
        this.cachedHash = computeHash(this.item, this.tag);
    }

    private static int computeHash(Item item, @Nullable CompoundTag tag) {
        ResourceLocation id = RegistryLookups.itemId(item);
        return 31 * id.hashCode() + (tag != null ? tag.hashCode() : 0);
    }

    /**
     * The key of {@code stack} ({@link #EMPTY} for an empty stack); its NBT is copied.
     */
    public static ItemKey fromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return EMPTY;
        }
        return new ItemKey(stack.getItem(), stack.getTag());
    }

    public boolean isEmpty() {
        return this.item == Items.AIR;
    }

    public Item getItem() {
        return this.item;
    }

    /**
     * The item's registry id.
     */
    public ResourceLocation getRegistryId() {
        return RegistryLookups.itemId(this.item);
    }

    /**
     * Namespace (mod id) of the item.
     */
    public String getNamespace() {
        return getRegistryId().getNamespace();
    }

    public boolean hasTag() {
        return this.tag != null;
    }

    /**
     * Serialized size of this key's NBT in bytes (0 without NBT), computed once and cached. Used to refuse
     * book-ban sized items before they reach the Vault file or sync packets.
     */
    public int getNbtSizeBytes() {
        int cached = this.nbtSizeBytes;
        if (cached >= 0) {
            return cached;
        }
        int size = 0;
        if (this.tag != null) {
            CountingOutputStream counter = new CountingOutputStream();
            try (DataOutputStream out = new DataOutputStream(counter)) {
                NbtIo.write(this.tag, out);
            } catch (IOException e) {
                size = Integer.MAX_VALUE;
            }
            size = size == Integer.MAX_VALUE ? size : (int) Math.min(Integer.MAX_VALUE, counter.count);
        }
        this.nbtSizeBytes = size;
        return size;
    }

    /**
     * Returns a defensive copy of the underlying NBT tag, or {@code null} if none is present.
     */
    @Nullable
    public CompoundTag getTag() {
        return this.tag != null ? this.tag.copy() : null;
    }

    /**
     * Creates a new {@link ItemStack} with the given count and a cloned copy of this key's NBT tag.
     */
    public ItemStack toStack(int count) {
        if (isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(this.item, count);
        if (this.tag != null) {
            stack.setTag(this.tag.copy());
        }
        return stack;
    }

    /**
     * A new one-item stack of this key.
     */
    public ItemStack toSingleStack() {
        return toStack(1);
    }

    /**
     * Largest stack this item forms, computed once from a real stack so loader hooks that size stacks by their NBT
     * are honoured (always at least 1).
     */
    public int getMaxStackSize() {
        int cached = this.maxStackSize;
        if (cached <= 0) {
            cached = isEmpty() ? 1 : Math.max(1, toSingleStack().getMaxStackSize());
            this.maxStackSize = cached;
        }
        return cached;
    }

    /**
     * Fast check whether an {@link ItemStack} matches this key without cloning NBT.
     */
    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return isEmpty();
        }
        if (stack.getItem() != this.item) {
            return false;
        }
        CompoundTag stackTag = stack.getTag();
        if (stackTag == null || stackTag.isEmpty()) {
            return this.tag == null;
        }
        return Objects.equals(this.tag, stackTag);
    }

    /**
     * Save format: the item id plus its NBT, if any.
     */
    public CompoundTag toTag() {
        CompoundTag out = new CompoundTag();
        out.putString(NBT_ITEM, getRegistryId().toString());
        if (this.tag != null) {
            out.put(NBT_TAG, this.tag.copy());
        }
        return out;
    }

    /**
     * Reads {@link #toTag()} output; items that no longer exist (mod removed) read as {@link #EMPTY}.
     */
    public static ItemKey fromTag(CompoundTag nbt) {
        if (nbt == null || !nbt.contains(NBT_ITEM)) {
            return EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(nbt.getString(NBT_ITEM));
        if (id == null) {
            return EMPTY;
        }
        Item item = RegistryLookups.item(id).orElse(null);
        if (item == null) {
            return EMPTY;
        }
        CompoundTag itemTag = nbt.contains(NBT_TAG, CompoundTag.TAG_COMPOUND)
                ? nbt.getCompound(NBT_TAG)
                : null;
        return new ItemKey(item, itemTag);
    }

    @SuppressWarnings("deprecation") // Forge deprecates vanilla registry access; network ids are loader-neutral
    public void toNetwork(FriendlyByteBuf buf) {
        buf.writeId(BuiltInRegistries.ITEM, this.item);
        buf.writeNbt(this.tag);
    }

    @SuppressWarnings("deprecation") // Forge deprecates vanilla registry access; network ids are loader-neutral
    public static ItemKey fromNetwork(FriendlyByteBuf buf) {
        Item item = buf.readById(BuiltInRegistries.ITEM);
        CompoundTag tag = buf.readNbt();
        if (item == null || item == Items.AIR) {
            return EMPTY;
        }
        return new ItemKey(item, tag);
    }

    @Override
    public int hashCode() {
        return this.cachedHash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ItemKey other)) {
            return false;
        }
        return this.cachedHash == other.cachedHash
                && this.item == other.item
                && Objects.equals(this.tag, other.tag);
    }

    @Override
    public String toString() {
        return "ItemKey{" + getRegistryId() + (this.tag != null ? ", hasTag=true" : "") + "}";
    }

    private static final class CountingOutputStream extends OutputStream {
        private long count;

        @Override
        public void write(int b) {
            this.count++;
        }

        @Override
        public void write(byte[] b, int off, int len) {
            this.count += len;
        }
    }
}
