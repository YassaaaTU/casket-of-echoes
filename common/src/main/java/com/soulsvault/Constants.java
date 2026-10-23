package com.soulsvault;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Constants {
    public static final String MOD_ID = "soulsvault";
    public static final String MOD_NAME = "Casket of Echoes";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);
    public static final Gson GSON = new GsonBuilder().create();

    public static final int MAX_SYNC_CHUNK_SIZE = 100;
    public static final long DEFAULT_MAX_STACK_CAPACITY = 9999L;
    /** Default Build Echo slots; the live value is {@code SoulsVaultConfig.echoes().baseSlots()} plus upgrades. */
    public static final int MAX_BUILD_ECHOES = 5;
    /** Hard network/storage ceiling for Build Echo slots, whatever the config says. */
    public static final int ABSOLUTE_MAX_BUILD_ECHOES = 32;

    public static final TagKey<Block> REST_POINTS_TAG = TagKey.create(
            Registries.BLOCK, new ResourceLocation(MOD_ID, "rest_points")
    );
    public static final TagKey<Item> CONSUMABLES_TAG = TagKey.create(
            Registries.ITEM, new ResourceLocation(MOD_ID, "consumables")
    );
    public static final TagKey<Item> KEY_ITEMS_TAG = TagKey.create(
            Registries.ITEM, new ResourceLocation(MOD_ID, "key_items")
    );
    public static final TagKey<Item> FORGE_WEAPONS_TAG = TagKey.create(
            Registries.ITEM, new ResourceLocation("forge", "tools/weapons")
    );
    /** Items that can never be stored in a Casket (shulker boxes, backpacks: no storage inside storage). */
    public static final TagKey<Item> VAULT_BLACKLIST_TAG = TagKey.create(
            Registries.ITEM, new ResourceLocation(MOD_ID, "vault_blacklist")
    );
    /** Entities whose presence nearby counts as a boss fight for the combat lock. */
    public static final TagKey<EntityType<?>> BOSSES_TAG = TagKey.create(
            Registries.ENTITY_TYPE, new ResourceLocation(MOD_ID, "bosses")
    );

    private Constants() {
    }
}
