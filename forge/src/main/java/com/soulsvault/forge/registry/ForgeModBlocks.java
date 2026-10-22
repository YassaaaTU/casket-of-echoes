package com.soulsvault.forge.registry;

import com.soulsvault.Constants;
import com.soulsvault.world.block.BottomlessCasketBlock;
import com.soulsvault.world.entity.SpectralCasketEntity;
import com.soulsvault.world.item.VestigeMothItem;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ForgeModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, Constants.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, Constants.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Constants.MOD_ID);

    public static final RegistryObject<BottomlessCasketBlock> BOTTOMLESS_CASKET = BLOCKS.register(
            "bottomless_casket",
            BottomlessCasketBlock::new
    );

    public static final RegistryObject<BlockItem> BOTTOMLESS_CASKET_ITEM = ITEMS.register(
            "bottomless_casket",
            () -> new BlockItem(
                    BOTTOMLESS_CASKET.get(),
                    new Item.Properties().rarity(Rarity.UNCOMMON).fireResistant()
            )
    );

    public static final RegistryObject<VestigeMothItem> VESTIGE_MOTH = ITEMS.register(
            "vestige_moth",
            VestigeMothItem::new
    );

    public static final RegistryObject<EntityType<SpectralCasketEntity>> SPECTRAL_CASKET = ENTITY_TYPES.register(
            "spectral_casket",
            () -> EntityType.Builder.<SpectralCasketEntity>of(SpectralCasketEntity::new, MobCategory.MISC)
                    .sized(0.85F, 0.85F)
                    .clientTrackingRange(8)
                    .updateInterval(10)
                    .fireImmune()
                    .build("spectral_casket")
    );

    private ForgeModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(ForgeModBlocks::addCreativeTabItems);
    }

    private static void addCreativeTabItems(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(BOTTOMLESS_CASKET_ITEM);
            event.accept(VESTIGE_MOTH);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(VESTIGE_MOTH);
        }
    }
}
