package com.soulsvault.forge;

import com.soulsvault.CommonClass;
import com.soulsvault.Constants;
import com.soulsvault.core.VaultCategory;
import com.soulsvault.forge.client.ForgeClientEvents;
import com.soulsvault.forge.config.ForgeSoulsVaultConfig;
import com.soulsvault.forge.event.ForgeServerEvents;
import com.soulsvault.forge.network.ForgeNetworkHandler;
import com.soulsvault.forge.registry.ForgeModBlocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(Constants.MOD_ID)
public class SoulsVaultForge {

    public SoulsVaultForge() {
        CommonClass.init();

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ForgeSoulsVaultConfig.register(modEventBus);
        ForgeModBlocks.register(modEventBus);
        modEventBus.addListener(this::onCommonSetup);

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ForgeClientEvents.register(modEventBus));

        ForgeNetworkHandler.register();
        ForgeServerEvents.register();

        MinecraftForge.EVENT_BUS.addListener(this::onTagsUpdated);
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ForgeNetworkHandler::register);
    }

    private void onTagsUpdated(final TagsUpdatedEvent event) {
        VaultCategory.clearCache();
    }
}
