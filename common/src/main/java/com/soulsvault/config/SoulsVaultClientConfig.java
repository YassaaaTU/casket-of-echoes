package com.soulsvault.config;

import com.soulsvault.core.VaultCategory;
import com.soulsvault.core.VaultSortMode;
import com.soulsvault.world.inventory.SoulsVaultMenu.LeftPaneMode;

/**
 * Per-player client preferences ({@code config/soulsvault-client.toml} on Forge). Unlike {@link SoulsVaultConfig}
 * these are never synced: every player chooses their own HUD, toast and Casket screen behaviour.
 */
public final class SoulsVaultClientConfig {

    /**
     * Screen corner or edge where overflow / replenish banners stack.
     */
    public enum ToastPosition {
        TOP_LEFT,
        TOP_RIGHT,
        MIDDLE_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    /**
     * How much each party frame shows.
     */
    public enum PartyHudStyle {
        /** Portrait, name and health bar. */
        PORTRAIT,
        /** Name and health bar. */
        BARS,
        /** Name plus a low-health / taking-damage indicator. */
        MINIMAL
    }

    /**
     * Screen edge the party frames are attached to.
     */
    public enum HudAnchor {
        TOP_LEFT,
        MIDDLE_LEFT,
        TOP_RIGHT,
        MIDDLE_RIGHT
    }

    /** What the {@link PartyHudStyle#PORTRAIT} frame shows. */
    public enum PortraitMode {
        /** Static 3D model with the teammate's skin, armor and held weapons. */
        MODEL,
        /** The teammate's face. */
        FACE
    }

    /** Who draws teammates in the world. */
    public enum MarkerMode {
        /** Casket markers, unless Xaero's Minimap already draws tracked teammates in the world (no double icons). */
        AUTO,
        /** Casket markers even when Xaero's Minimap draws its own. */
        ON,
        OFF
    }

    /** Whether party frames show the hunger bar. */
    public enum HungerDisplay {
        /** Hidden when a mod that removes hunger (HungerStrike) is installed. */
        AUTO,
        SHOW,
        HIDE
    }

    /**
     * Full client settings snapshot, swapped atomically on load and on every change.
     */
    public record Settings(
            boolean toastsEnabled,
            ToastPosition toastPosition,
            double toastDurationSeconds,
            boolean toastSound,
            VaultSortMode defaultSortMode,
            boolean rememberLastTab,
            VaultCategory lastCategory,
            VaultSortMode lastSortMode,
            LeftPaneMode lastLeftPane,
            boolean partyHudVisible,
            PartyHudStyle partyHudStyle,
            HudAnchor partyHudAnchor,
            double partyHudScale,
            int partyHudFullRows,
            PortraitMode partyPortrait,
            HungerDisplay partyHunger,
            MarkerMode partyMarkers,
            int partyMarkerMinDistance,
            boolean partyOnXaeroMaps
    ) {
        public static final Settings DEFAULT = new Settings(
                true,
                ToastPosition.MIDDLE_RIGHT,
                3.2D,
                false,
                VaultSortMode.RECENTLY_ACQUIRED,
                true,
                VaultCategory.ALL,
                VaultSortMode.RECENTLY_ACQUIRED,
                LeftPaneMode.GEAR_CURIOS,
                true,
                PartyHudStyle.PORTRAIT,
                HudAnchor.MIDDLE_LEFT,
                1.0D,
                4,
                PortraitMode.MODEL,
                HungerDisplay.AUTO,
                MarkerMode.AUTO,
                10,
                true
        );
    }

    /** Persists values the client changes at runtime (remembered tabs, party HUD options). */
    public interface Writer {
        void rememberTab(VaultCategory category, VaultSortMode sortMode);

        void rememberLeftPane(LeftPaneMode mode);

        void setPartyHud(boolean visible, PartyHudStyle style, HudAnchor anchor);

        void setPartyVisuals(PortraitMode portrait, MarkerMode markers);
    }

    private static volatile Settings settings = Settings.DEFAULT;
    private static volatile Writer writer = null;

    private SoulsVaultClientConfig() {
    }

    public static Settings get() {
        return settings;
    }

    /**
     * Installs settings read from the config file, and the writer used to persist runtime changes
     * ({@code null} keeps the current writer).
     */
    public static void apply(Settings newSettings, Writer newWriter) {
        settings = newSettings != null ? newSettings : Settings.DEFAULT;
        if (newWriter != null) {
            writer = newWriter;
        }
    }

    /**
     * Category tab a newly opened Casket screen starts on.
     */
    public static VaultCategory initialCategory() {
        return settings.rememberLastTab() ? settings.lastCategory() : VaultCategory.ALL;
    }

    /**
     * Sort mode a newly opened Casket screen starts with.
     */
    public static VaultSortMode initialSortMode() {
        return settings.rememberLastTab() ? settings.lastSortMode() : settings.defaultSortMode();
    }

    /**
     * Left pane a newly opened Casket screen starts on.
     */
    public static LeftPaneMode initialLeftPane() {
        return settings.rememberLastTab() ? settings.lastLeftPane() : LeftPaneMode.GEAR_CURIOS;
    }

    /**
     * Saves the tab and sort mode for next time (when {@code rememberLastTab} is on and they changed).
     */
    public static void rememberTab(VaultCategory category, VaultSortMode sortMode) {
        Writer current = writer;
        if (settings.rememberLastTab() && current != null
                && (category != settings.lastCategory() || sortMode != settings.lastSortMode())) {
            current.rememberTab(category, sortMode);
        }
    }

    /**
     * Saves the left pane for next time (when {@code rememberLastTab} is on and it changed).
     */
    public static void rememberLeftPane(LeftPaneMode mode) {
        Writer current = writer;
        if (settings.rememberLastTab() && current != null && mode != null && mode != settings.lastLeftPane()) {
            current.rememberLeftPane(mode);
        }
    }

    /**
     * Shows or hides the party HUD and saves the choice.
     */
    public static void togglePartyHud() {
        setPartyHud(!settings.partyHudVisible(), settings.partyHudStyle(), settings.partyHudAnchor());
    }

    public static void setPartyHud(boolean visible, PartyHudStyle style, HudAnchor anchor) {
        Writer current = writer;
        if (current != null) {
            current.setPartyHud(visible, style, anchor);
        }
    }

    public static void setPartyVisuals(PortraitMode portrait, MarkerMode markers) {
        Writer current = writer;
        if (current != null) {
            current.setPartyVisuals(portrait, markers);
        }
    }
}
