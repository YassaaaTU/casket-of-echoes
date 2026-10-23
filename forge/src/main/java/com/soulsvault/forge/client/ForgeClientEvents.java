package com.soulsvault.forge.client;

import com.soulsvault.client.ClientVaultCache;
import com.soulsvault.client.gui.SoulsVaultScreen;
import com.soulsvault.client.gui.widget.GothicButton;
import com.soulsvault.client.hud.SoulsOverflowToastOverlay;
import com.soulsvault.client.renderer.SpectralCasketRenderer;
import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.forge.network.ForgeNetworkHandler;
import com.soulsvault.forge.registry.ForgeModBlocks;
import com.soulsvault.network.PacketC2SOpenVault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only Forge registration for {@link SoulsVaultScreen}, {@link SoulsOverflowToastOverlay},
 * {@link SpectralCasketRenderer}, and full inventory replacement interception.
 */
public final class ForgeClientEvents {

    private ForgeClientEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ForgeClientEvents::onClientSetup);
        modEventBus.addListener(ForgeClientEvents::onRegisterGuiOverlays);
        modEventBus.addListener(ForgeClientEvents::onRegisterRenderers);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientEvents::onScreenOpening);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientEvents::onScreenInitPost);
    }

    private static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ForgeModBlocks.VAULT_MENU.get(), SoulsVaultScreen::new);
            SoulsOverflowToastOverlay.init();
            ClientVaultCache.setVaultScreenOpener(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof SoulsVaultScreen vaultScreen) {
                    vaultScreen.refreshAllSlots();
                }
            });
        });
    }

    private static void onScreenOpening(final ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof InventoryScreen)) {
            return;
        }
        if (!SoulsVaultConfig.isFullInventoryReplacement()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) {
            return;
        }
        event.setCanceled(true);
        ForgeNetworkHandler.sendToServer(PacketC2SOpenVault.openFromInventoryKey());
    }

    private static void onScreenInitPost(final ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen invScreen)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) {
            return;
        }
        // Add a compact Gothic button on the vanilla InventoryScreen so the player can re-enable Souls Inventory UI anytime
        int btnX = invScreen.getGuiLeft() + invScreen.getXSize() - 62;
        int btnY = invScreen.getGuiTop() - 16;
        event.addListener(new GothicButton(
                btnX,
                Math.max(2, btnY),
                62,
                14,
                Component.translatable("gui.soulsvault.config.inv_souls"),
                btn -> {
                    SoulsVaultConfig.setFullInventoryReplacement(true);
                    ForgeNetworkHandler.sendToServer(PacketC2SOpenVault.openFromInventoryKey());
                }
        ));
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
