package com.soulsvault;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Constants {
    public static final String MOD_ID = "soulsvault";
    public static final String MOD_NAME = "Casket of Echoes";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    public static final int MAX_SYNC_CHUNK_SIZE = 100;
    public static final long DEFAULT_MAX_STACK_CAPACITY = 9999L;
    public static final int MAX_BUILD_ECHOES = 5;
    public static final int VESTIGE_MOTH_XP_LEVEL_COST = 1;
    public static final int SPECTRAL_CASKET_LIFETIME_TICKS = 60 * 20;
    public static final double REST_POINT_REACH_BLOCKS = 6.0D;
    public static final double REST_POINT_REACH_SQR = REST_POINT_REACH_BLOCKS * REST_POINT_REACH_BLOCKS;

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

    private Constants() {
    }
}
