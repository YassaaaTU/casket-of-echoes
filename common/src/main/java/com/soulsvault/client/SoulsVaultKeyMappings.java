package com.soulsvault.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.soulsvault.client.party.ClientPartyCache;
import com.soulsvault.config.SoulsVaultClientConfig;
import com.soulsvault.network.PacketC2SOpenVault;
import com.soulsvault.platform.Services;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * Rebindable Casket of Echoes key mappings, listed under their own "Casket of Echoes" category in
 * Options &gt; Controls (and therefore searchable / rebindable through Controlling, VisualKeys, etc.).
 * <p>
 * The mappings themselves are plain vanilla {@link KeyMapping}s; the loader module registers them and
 * installs a {@link KeyMatcher} so conflict contexts and key modifiers (e.g. Forge's {@code CTRL + key})
 * are honoured when matching.
 */
public final class SoulsVaultKeyMappings {
    public static final String CATEGORY = "key.categories.soulsvault";

    /** Toggles the Item Inspection pane for the hovered / selected item (inside the Casket screen). */
    public static final KeyMapping INSPECT = new KeyMapping(
            "key.soulsvault.inspect", InputConstants.Type.KEYSYM, InputConstants.KEY_I, CATEGORY
    );
    /** Toggles the 3x3 crafting sub-tab of the left pane (inside the Casket screen). */
    public static final KeyMapping CRAFTING = new KeyMapping(
            "key.soulsvault.crafting", InputConstants.Type.KEYSYM, InputConstants.KEY_C, CATEGORY
    );
    /** Toggles the Build Echoes sub-tab of the left pane (inside the Casket screen). */
    public static final KeyMapping BUILD_ECHOES = new KeyMapping(
            "key.soulsvault.build_echoes", InputConstants.Type.KEYSYM, InputConstants.KEY_B, CATEGORY
    );
    /** Opens the Casket while standing near a Rest Point or Spectral Casket (unbound by default). */
    public static final KeyMapping OPEN_CASKET = new KeyMapping(
            "key.soulsvault.open_casket", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY
    );

    /** Opens the party GUI (unbound by default). */
    public static final KeyMapping OPEN_PARTY = new KeyMapping(
            "key.soulsvault.open_party", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY
    );
    /** Shows / hides the party HUD frames (unbound by default; also in the party GUI). */
    public static final KeyMapping TOGGLE_PARTY_HUD = new KeyMapping(
            "key.soulsvault.toggle_party_hud", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), CATEGORY
    );

    /** Mappings that only act while the Casket screen is open (GUI conflict context). */
    public static final List<KeyMapping> SCREEN_KEYS = List.of(INSPECT, CRAFTING, BUILD_ECHOES);
    /** Mappings that only act while in-world with no screen open (in-game conflict context). */
    public static final List<KeyMapping> WORLD_KEYS = List.of(OPEN_CASKET, OPEN_PARTY, TOGGLE_PARTY_HUD);

    private static KeyMatcher keyMatcher = KeyMapping::matches;
    private static MouseMatcher mouseMatcher = KeyMapping::matchesMouse;

    private SoulsVaultKeyMappings() {
    }

    /**
     * Installs loader-aware matchers (e.g. Forge's {@code isActiveAndMatches}, which respects conflict
     * contexts and key modifiers). Defaults to vanilla matching.
     */
    public static void setMatchers(KeyMatcher keys, MouseMatcher mouse) {
        keyMatcher = keys != null ? keys : KeyMapping::matches;
        mouseMatcher = mouse != null ? mouse : KeyMapping::matchesMouse;
    }

    /**
     * Whether a key press triggers {@code mapping} (unbound mappings never match).
     */
    public static boolean matchesKey(KeyMapping mapping, int keyCode, int scanCode) {
        return !mapping.isUnbound() && keyMatcher.matches(mapping, keyCode, scanCode);
    }

    /**
     * Whether a mouse click triggers {@code mapping} (unbound mappings never match).
     */
    public static boolean matchesMouse(KeyMapping mapping, int button) {
        return !mapping.isUnbound() && mouseMatcher.matches(mapping, button);
    }

    /**
     * Returns {@code "[Key] "} for the mapping's current binding, or an empty component if unbound,
     * so UI labels always reflect what the player actually bound.
     */
    public static MutableComponent keyHint(KeyMapping mapping) {
        if (mapping.isUnbound()) {
            return Component.empty();
        }
        return Component.literal("[").append(mapping.getTranslatedKeyMessage()).append("] ");
    }

    /**
     * Polls in-world key mappings. Called once per client tick by the loader module.
     */
    public static void tickWorldKeys(Minecraft mc) {
        while (OPEN_CASKET.consumeClick()) {
            if (mc.player != null && mc.screen == null && !mc.player.isSpectator()) {
                Services.PLATFORM.sendToServer(PacketC2SOpenVault.request());
            }
        }
        while (OPEN_PARTY.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                ClientPartyCache.openScreen(null, false);
            }
        }
        while (TOGGLE_PARTY_HUD.consumeClick()) {
            SoulsVaultClientConfig.togglePartyHud();
        }
    }

    /**
     * Loader-specific keyboard matching (conflict context, modifiers).
     */
    @FunctionalInterface
    public interface KeyMatcher {
        boolean matches(KeyMapping mapping, int keyCode, int scanCode);
    }

    /**
     * Loader-specific mouse button matching (conflict context, modifiers).
     */
    @FunctionalInterface
    public interface MouseMatcher {
        boolean matches(KeyMapping mapping, int button);
    }
}
