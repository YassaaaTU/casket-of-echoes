package com.soulsvault.party;

import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.config.SoulsVaultConfig.PartyProviderType;
import com.soulsvault.platform.Services;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shared progression for parties that opted in: live sharing of newly earned advancements, and a catch-up when a
 * member logs in (or sharing is turned on) so members who were offline receive what the others earned meanwhile,
 * and vice versa. Catch-up merges what the online members have completed; members offline at the time catch up
 * the next time they are online together with someone who has it.
 */
public final class ProgressionSync {
    private static final ThreadLocal<Boolean> GRANTING = ThreadLocal.withInitial(() -> false);

    private ProgressionSync() {
    }

    public static boolean sharesAdvancements(PartyView party) {
        return SoulsVaultConfig.party().shareAdvancements().resolve(party.settings().shareAdvancements());
    }

    /**
     * FTB Teams party teams already share FTB Quests progress by design, so only other providers need syncing.
     */
    public static boolean sharesQuests(PartyView party) {
        return party.provider() != PartyProviderType.FTB_TEAMS
                && SoulsVaultConfig.party().shareQuests().resolve(party.settings().shareQuests());
    }

    /**
     * Grants an advancement earned by one online member to the other online members.
     */
    public static void onAdvancementEarned(ServerPlayer player, Advancement advancement) {
        if (GRANTING.get() || advancement.getDisplay() == null) {
            return;
        }
        PartyView party = PartyService.getParty(player.server, player.getUUID());
        if (party == null || !sharesAdvancements(party)) {
            return;
        }
        for (ServerPlayer member : onlineMembers(player.server, party)) {
            if (member != player) {
                grant(member, advancement);
            }
        }
    }

    /**
     * Catch-up when a member logs in.
     */
    public static void onLogin(ServerPlayer player) {
        PartyView party = PartyService.getParty(player.server, player.getUUID());
        if (party != null) {
            syncParty(player.server, party);
        }
    }

    /**
     * Gives every online member everything any online member has completed (advancements and, through the
     * platform, FTB Quests quests), per the party's sharing options.
     */
    public static void syncParty(MinecraftServer server, PartyView party) {
        List<ServerPlayer> online = onlineMembers(server, party);
        if (online.size() < 2) {
            return;
        }
        if (sharesAdvancements(party)) {
            for (Advancement advancement : server.getAdvancements().getAllAdvancements()) {
                if (advancement.getDisplay() == null) {
                    continue;
                }
                boolean anyDone = false;
                for (ServerPlayer member : online) {
                    if (member.getAdvancements().getOrStartProgress(advancement).isDone()) {
                        anyDone = true;
                        break;
                    }
                }
                if (anyDone) {
                    for (ServerPlayer member : online) {
                        grant(member, advancement);
                    }
                }
            }
        }
        if (sharesQuests(party)) {
            Services.PLATFORM.syncQuestProgress(online);
        }
    }

    private static void grant(ServerPlayer member, Advancement advancement) {
        AdvancementProgress progress = member.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone()) {
            return;
        }
        List<String> remaining = new ArrayList<>();
        progress.getRemainingCriteria().forEach(remaining::add);
        GRANTING.set(true);
        try {
            for (String criterion : remaining) {
                member.getAdvancements().award(advancement, criterion);
            }
        } finally {
            GRANTING.set(false);
        }
    }

    private static List<ServerPlayer> onlineMembers(MinecraftServer server, PartyView party) {
        List<ServerPlayer> online = new ArrayList<>();
        for (UUID id : party.members()) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }
}
