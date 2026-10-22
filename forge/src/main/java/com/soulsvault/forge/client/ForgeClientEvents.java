package com.soulsvault.forge.client;

import com.soulsvault.client.ClientVaultCache;
import com.soulsvault.client.gui.SoulsVaultScreen;
import com.soulsvault.client.hud.SoulsOverflowToastOverlay;
import com.soulsvault.client.renderer.SpectralCasketRenderer;
import com.soulsvault.forge.registry.ForgeModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only Forge registration for {@link SoulsVaultScreen}, {@link SoulsOverflowToastOverlay},
 * and {@link SpectralCasketRenderer}.
 */
public final class ForgeClientEvents {

    private ForgeClientEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ForgeClientEvents::onClientSetup);
        modEventBus.addListener(ForgeClientEvents::onRegisterGuiOverlays);
        modEventBus.addListener(ForgeClientEvents::onRegisterRenderers);
    }

    private static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SoulsOverflowToastOverlay.init();
            ClientVaultCache.setVaultScreenOpener(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof SoulsVaultScreen vaultScreen) {
                    vaultScreen.refreshAllSlots();
                } else {
                    mc.setScreen(new SoulsVaultScreen());
                }
            });
        });
    }

    private static void onRegisterGuiOverlays(final RegisterGuiOverlaysEvent event) {
        SoulsOverflowToastOverlay.init();
        event.registerAboveAll(
                "overflow_toast",
                (gui, guiGraphics, partialTick, screenWidth, screenHeight) ->
                        SoulsOverflowToastOverlay.render(guiGraphics, partialTick, screenWidth, screenHeight)
        );
    }

    private static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ForgeModBlocks.SPECTRAL_CASKET.get(), SpectralCasketRenderer::new);
    }
}
