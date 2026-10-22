package com.soulsvault.forge.registry;

import com.soulsvault.Constants;
import com.soulsvault.world.block.BottomlessCasketBlock;
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

    private ForgeModBlocks() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        modEventBus.addListener(ForgeModBlocks::addCreativeTabItems);
    }

    private static void addCreativeTabItems(final BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(BOTTOMLESS_CASKET_ITEM);
        }
    }
}
