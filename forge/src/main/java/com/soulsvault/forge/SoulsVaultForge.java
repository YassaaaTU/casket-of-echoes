package com.soulsvault.forge;

import com.soulsvault.CommonClass;
import com.soulsvault.Constants;
import com.soulsvault.core.VaultCategory;
import com.soulsvault.forge.network.ForgeNetworkHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Constants.MOD_ID)
public class SoulsVaultForge {

    public SoulsVaultForge() {
        CommonClass.init();

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onCommonSetup);

        ForgeNetworkHandler.register();

        MinecraftForge.EVENT_BUS.addListener(this::onTagsUpdated);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ForgeNetworkHandler::register);
    }

    private void onTagsUpdated(final TagsUpdatedEvent event) {
        VaultCategory.clearCache();
    }
}
