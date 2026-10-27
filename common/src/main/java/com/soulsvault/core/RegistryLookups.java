package com.soulsvault.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Read-only lookups in the game's built-in registries, kept in one place.
 * <p>
 * Forge marks vanilla's {@link BuiltInRegistries} deprecated in favour of its own registry wrappers, which the
 * loader-agnostic common module cannot reference. Reading ids and entries is safe on every loader, so the
 * deprecation is suppressed here, per method, instead of at every call site.
 */
public final class RegistryLookups {

    private RegistryLookups() {
    }

    /**
     * Registry id of {@code item}.
     */
    @SuppressWarnings("deprecation") // Forge deprecates vanilla registry access; read-only lookups are loader-neutral
    public static ResourceLocation itemId(Item item) {
        return BuiltInRegistries.ITEM.getKey(item);
    }

    /**
     * The item registered under {@code id}, or empty when no such item exists (never {@link Items#AIR}).
     */
    @SuppressWarnings("deprecation") // Forge deprecates vanilla registry access; read-only lookups are loader-neutral
    public static Optional<Item> item(@Nullable ResourceLocation id) {
        return id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR);
    }

    /**
     * The block registered under {@code id}, if any.
     */
    @SuppressWarnings("deprecation") // Forge deprecates vanilla registry access; read-only lookups are loader-neutral
    public static Optional<Block> block(ResourceLocation id) {
        return BuiltInRegistries.BLOCK.getOptional(id);
    }

    /**
     * Registry id of {@code effect}, or {@code null} when it is not registered.
     */
    @Nullable
    @SuppressWarnings("deprecation") // Forge deprecates vanilla registry access; read-only lookups are loader-neutral
    public static ResourceLocation mobEffectId(MobEffect effect) {
        return BuiltInRegistries.MOB_EFFECT.getKey(effect);
    }

    /**
     * The effect registered under {@code id}, or {@code null}.
     */
    @Nullable
    @SuppressWarnings("deprecation") // Forge deprecates vanilla registry access; read-only lookups are loader-neutral
    public static MobEffect mobEffect(@Nullable ResourceLocation id) {
        return id == null ? null : BuiltInRegistries.MOB_EFFECT.get(id);
    }
}
