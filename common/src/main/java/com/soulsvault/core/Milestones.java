package com.soulsvault.core;

import com.soulsvault.Constants;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Casket advancements ({@code data/soulsvault/advancements}), granted from code so quest books (FTB Quests
 * advancement tasks) and datapacks can follow a player's progress through the Casket's features.
 */
public final class Milestones {
    public static final String ROOT = "root";
    public static final String ATTUNED = "attuned";
    public static final String RESTED = "rested";
    public static final String OVERFLOW = "overflow";
    public static final String REFILL_PINNED = "refill_pinned";
    public static final String ECHO_SAVED = "echo_saved";
    public static final String ECHO_SWAPPED = "echo_swapped";
    public static final String SPECTRAL_CASKET = "spectral_casket";
    public static final String CASKET_EXPANDED = "casket_expanded";
    public static final String ECHO_SLOT_ADDED = "echo_slot_added";
    public static final String COVENANT_JOINED = "covenant_joined";
    public static final String KEEPSAKES_UNLOCKED = "keepsakes_unlocked";
    public static final String KEEPSAKES_UPGRADED = "keepsakes_upgraded";

    private static final String CRITERION = "granted";

    private Milestones() {
    }

    /**
     * Grants the named advancement (and the root one) unless the player already has it.
     */
    public static void award(ServerPlayer player, String name) {
        grant(player, ROOT);
        grant(player, name);
    }

    private static void grant(ServerPlayer player, String name) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(new ResourceLocation(Constants.MOD_ID, name));
        if (advancement != null && !player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            player.getAdvancements().award(advancement, CRITERION);
        }
    }
}
