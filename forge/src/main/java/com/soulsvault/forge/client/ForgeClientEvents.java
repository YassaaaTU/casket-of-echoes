package com.soulsvault.forge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.soulsvault.client.ClientVaultCache;
import com.soulsvault.client.SoulsVaultKeyMappings;
import com.soulsvault.client.gui.SoulsVaultScreen;
import com.soulsvault.client.hud.SoulsOverflowToastOverlay;
import com.soulsvault.client.party.ClientPartyCache;
import com.soulsvault.client.party.PartyHudOverlay;
import com.soulsvault.client.party.PartyMarkers;
import com.soulsvault.client.renderer.SpectralCasketRenderer;
import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.forge.network.ForgeNetworkHandler;
import com.soulsvault.forge.registry.ForgeModBlocks;
import com.soulsvault.network.PacketC2SOpenVault;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only Forge registration for {@link SoulsVaultScreen}, {@link SoulsOverflowToastOverlay},
 * {@link SpectralCasketRenderer}, {@link SoulsVaultKeyMappings}, and full inventory replacement interception.
 */
public final class ForgeClientEvents {

    private ForgeClientEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ForgeClientEvents::onClientSetup);
        modEventBus.addListener(ForgeClientEvents::onRegisterGuiOverlays);
        modEventBus.addListener(ForgeClientEvents::onRegisterRenderers);
        modEventBus.addListener(ForgeClientEvents::onRegisterKeyMappings);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientEvents::onScreenOpening);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientEvents::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientEvents::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(ForgeClientEvents::onRenderLevelStage);
    }

    /**
     * Projects party markers with the camera of the frame being drawn (the HUD layer draws them afterwards).
     */
    private static void onRenderLevelStage(final RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            PartyMarkers.capture(event.getPoseStack().last().pose(), event.getProjectionMatrix(),
                    event.getCamera().getPosition(), event.getPartialTick());
        }
    }

    private static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        ClientPartyCache.reset();
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

    /**
     * Registers the "Casket of Echoes" Controls category. Screen shortcuts use the GUI conflict context so
     * they never clash with in-world keys, and matching goes through Forge's {@code isActiveAndMatches} so
     * key modifiers (CTRL/SHIFT/ALT + key) set via Controls or Controlling are respected.
     */
    private static void onRegisterKeyMappings(final RegisterKeyMappingsEvent event) {
        for (KeyMapping mapping : SoulsVaultKeyMappings.SCREEN_KEYS) {
            mapping.setKeyConflictContext(KeyConflictContext.GUI);
            event.register(mapping);
        }
        for (KeyMapping mapping : SoulsVaultKeyMappings.WORLD_KEYS) {
            mapping.setKeyConflictContext(KeyConflictContext.IN_GAME);
            event.register(mapping);
        }
        SoulsVaultKeyMappings.setMatchers(
                (mapping, keyCode, scanCode) -> mapping.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode)),
                (mapping, button) -> mapping.isActiveAndMatches(InputConstants.Type.MOUSE.getOrCreate(button))
        );
    }

    private static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            SoulsVaultKeyMappings.tickWorldKeys(Minecraft.getInstance());
        }
    }

    /**
     * Replaces the vanilla inventory with the Souls Inventory when the server's
     * {@code fullInventoryReplacement} config is enabled (value is synced from the server on login/reload).
     */
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

    private static void onRegisterGuiOverlays(final RegisterGuiOverlaysEvent event) {
        SoulsOverflowToastOverlay.init();
        event.registerAboveAll(
                "overflow_toast",
                (gui, guiGraphics, partialTick, screenWidth, screenHeight) ->
                        SoulsOverflowToastOverlay.render(guiGraphics, partialTick, screenWidth, screenHeight)
        );
        event.registerBelow(
                VanillaGuiOverlay.HOTBAR.id(),
                "party_markers",
                (gui, guiGraphics, partialTick, screenWidth, screenHeight) ->
                        PartyMarkers.render(guiGraphics, partialTick, screenWidth, screenHeight)
        );
        event.registerAbove(
                VanillaGuiOverlay.HOTBAR.id(),
                "party_frames",
                (gui, guiGraphics, partialTick, screenWidth, screenHeight) ->
                        PartyHudOverlay.render(guiGraphics, partialTick, screenWidth, screenHeight)
        );
    }

    private static void onRegisterRenderers(final EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ForgeModBlocks.SPECTRAL_CASKET.get(), SpectralCasketRenderer::new);
    }
}
