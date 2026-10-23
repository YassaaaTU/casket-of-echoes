package com.soulsvault.party;

import com.soulsvault.Constants;
import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.core.CovenantLedger;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultStorage;
import com.soulsvault.core.VaultSync;
import com.soulsvault.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lifecycle of a party's shared Covenant Casket, identical for every party provider:
 * <ul>
 *   <li><b>Join</b>: being in a sharing party is not enough; each member chooses what happens to their own Casket
 *       (merge / empty first / override) before they use the Covenant. Members with an empty Casket join directly.
 *       This is how joins made in FTB Teams, OPAC or {@code /team} still get the casket prompt.</li>
 *   <li><b>Leave</b> (left, kicked, or no longer in the party in another mod): the member gets back what they still
 *       own (config {@code returnContributionsOnLeave}) and their own Casket is raised to the Covenant level they
 *       earned.</li>
 *   <li><b>Dissolve</b> (disbanded, sharing turned off, or the party vanished in another mod): every member, online
 *       or not, gets back what they own; unclaimed items go to the leader. Nothing is ever deleted.</li>
 * </ul>
 * {@link #reconcile} runs every few seconds so changes made outside Casket of Echoes are picked up even while the
 * server was offline.
 */
public final class CovenantService {
    private static final int RECONCILE_INTERVAL_TICKS = 100;
    private static final Map<UUID, String> PROMPTED = new ConcurrentHashMap<>();

    private CovenantService() {
    }

    private static VaultStorage storageOf(MinecraftServer server, String storageId) {
        return Services.PLATFORM.getSharedStorage(server, storageId);
    }

    public static boolean isMember(MinecraftServer server, PartyView party, UUID player) {
        CovenantLedger ledger = storageOf(server, party.storageId()).getLedger();
        return ledger != null && ledger.isMember(player);
    }

    /**
     * Whether the player is in a sharing party but has not chosen what happens to their own Casket yet.
     */
    public static boolean isPending(MinecraftServer server, UUID player) {
        PartyView party = PartyService.getParty(server, player);
        return party != null && PartyService.sharesCasket(party) && !isMember(server, party, player);
    }

    // ------------------------------------------------------------------------------------------------ join

    /**
     * Joins the party's Covenant, applying the chosen casket policy to the player's own Casket.
     *
     * @return why it failed, or {@code null}
     */
    @Nullable
    public static Component join(ServerPlayer player, PartyView party, CasketJoinPolicy policy, boolean confirmedOverride) {
        MinecraftServer server = player.server;
        if (!PartyService.sharesCasket(party)) {
            return Component.translatable("gui.soulsvault.party.error.not_shared");
        }
        VaultStorage shared = storageOf(server, party.storageId());
        CovenantLedger ledger = shared.getLedger();
        if (ledger == null || ledger.isMember(player.getUUID())) {
            return null;
        }
        VaultStorage own = Services.PLATFORM.getPlayerVaultData(server, player.getUUID()).getPrivateStorage();
        PartyService.MergeResult merge = null;
        if (!own.isEmpty()) {
            SoulsVaultConfig.Party config = SoulsVaultConfig.party();
            switch (policy) {
                case MERGE -> {
                    if (!config.allowCasketMerge()) {
                        return Component.translatable("gui.soulsvault.party.error.merge_disabled");
                    }
                    merge = mergeInto(own, shared, player.getUUID());
                }
                case EMPTY_FIRST -> {
                    return Component.translatable("gui.soulsvault.party.error.casket_not_empty", own.totalItems());
                }
                case OVERRIDE -> {
                    if (!config.allowCasketOverride()) {
                        return Component.translatable("gui.soulsvault.party.error.override_disabled");
                    }
                    if (!confirmedOverride) {
                        return Component.translatable("gui.soulsvault.party.error.override_unconfirmed");
                    }
                    Constants.LOG.info("{} overrode (deleted) their private Casket ({} items, {} kinds) to join the Covenant of {}",
                            player.getGameProfile().getName(), own.totalItems(), own.uniqueEntries(), party.name());
                    own.clear();
                }
            }
        }
        ledger.addMember(player.getUUID(), System.currentTimeMillis());
        PartyService.registry(server).markCovenant(party.key(), party.leader());
        PROMPTED.remove(player.getUUID());

        player.sendSystemMessage(PartyService.prefix().append(Component.translatable("gui.soulsvault.covenant.joined", party.name())));
        if (merge != null) {
            Component summary = merge.describe(player.getDisplayName());
            player.sendSystemMessage(PartyService.prefix().append(summary));
            ServerPlayer leader = party.leader() != null ? server.getPlayerList().getPlayer(party.leader()) : null;
            if (leader != null && leader != player) {
                leader.sendSystemMessage(PartyService.prefix().append(summary));
            }
        }
        VaultSync.resync(player);
        VaultSync.resyncViewers(server, party.storageId());
        PartyService.forceSync(player);
        return null;
    }

    /**
     * Moves what fits from a private Casket into the Covenant, owned by {@code owner}; the rest stays set aside.
     */
    public static PartyService.MergeResult mergeInto(VaultStorage from, VaultStorage to, UUID owner) {
        long moved = 0L;
        for (ItemKey key : new ArrayList<>(from.keys())) {
            long accepted = to.deposit(key, from.getCount(key), owner);
            if (accepted > 0L) {
                from.withdraw(key, accepted);
                moved += accepted;
            }
        }
        return new PartyService.MergeResult(moved, from.totalItems());
    }

    // ------------------------------------------------------------------------------------------------ leave / dissolve

    /**
     * Removes a member from the Covenant: returns what they own (or leaves it to the party), credits earned upgrades,
     * and dissolves the Covenant if nobody is left.
     */
    public static void leave(MinecraftServer server, String storageId, UUID member, boolean returnItems) {
        VaultStorage shared = storageOf(server, storageId);
        CovenantLedger ledger = shared.getLedger();
        if (ledger == null || !ledger.isMember(member)) {
            return;
        }
        PlayerVaultData vault = Services.PLATFORM.getPlayerVaultData(server, member);
        long returned = 0L;
        if (returnItems) {
            returned = returnShares(shared, ledger, member, vault.getPrivateStorage());
        } else {
            ledger.transferToUnowned(member);
        }
        vault.getPrivateStorage().raiseExpansionsTo(ledger.creditOf(member));
        ledger.removeMember(member);

        ServerPlayer online = server.getPlayerList().getPlayer(member);
        if (online != null) {
            online.sendSystemMessage(PartyService.prefix().append(returnItems
                    ? Component.translatable("gui.soulsvault.covenant.left_returned", returned)
                    : Component.translatable("gui.soulsvault.covenant.left_kept")));
            VaultSync.resync(online);
            PartyService.forceSync(online);
        }
        if (ledger.members().isEmpty()) {
            dissolve(server, storageId, member);
        } else {
            VaultSync.resyncViewers(server, storageId);
        }
    }

    /**
     * Ends the Covenant: everyone gets back what they own, unclaimed items go to {@code recipient}.
     */
    public static void dissolve(MinecraftServer server, String storageId, @Nullable UUID recipient) {
        VaultStorage shared = storageOf(server, storageId);
        CovenantLedger ledger = shared.getLedger();
        if (ledger == null) {
            return;
        }
        List<UUID> formerMembers = List.copyOf(ledger.members());
        for (UUID member : formerMembers) {
            PlayerVaultData vault = Services.PLATFORM.getPlayerVaultData(server, member);
            returnShares(shared, ledger, member, vault.getPrivateStorage());
            vault.getPrivateStorage().raiseExpansionsTo(ledger.creditOf(member));
            ledger.removeMember(member);
        }
        if (!shared.isEmpty() && recipient != null) {
            VaultStorage target = Services.PLATFORM.getPlayerVaultData(server, recipient).getPrivateStorage();
            for (ItemKey key : new ArrayList<>(shared.keys())) {
                target.depositUnchecked(key, shared.withdraw(key, shared.getCount(key)));
            }
        }
        if (!shared.isEmpty()) {
            Constants.LOG.warn("Covenant {} dissolved with {} unclaimed items and nobody to receive them; they stay in its file",
                    storageId, shared.totalItems());
        }
        ledger.clear();
        String partyKey = storageId.startsWith("party:") ? storageId.substring("party:".length()) : storageId;
        PartyService.registry(server).unmarkCovenant(partyKey);
        PartyService.invalidate();
        for (UUID member : formerMembers) {
            ServerPlayer online = server.getPlayerList().getPlayer(member);
            if (online != null) {
                online.sendSystemMessage(PartyService.prefix().append(Component.translatable("gui.soulsvault.covenant.dissolved")));
                VaultSync.resync(online);
                PartyService.forceSync(online);
            }
        }
        ServerPlayer receiver = recipient != null ? server.getPlayerList().getPlayer(recipient) : null;
        if (receiver != null && !formerMembers.contains(recipient)) {
            VaultSync.resync(receiver);
        }
    }

    private static long returnShares(VaultStorage shared, CovenantLedger ledger, UUID member, VaultStorage target) {
        long returned = 0L;
        for (Map.Entry<ItemKey, Long> owned : ledger.sharesOf(member).entrySet()) {
            long taken = shared.withdraw(owned.getKey(), owned.getValue(), member);
            target.depositUnchecked(owned.getKey(), taken);
            returned += taken;
        }
        return returned;
    }

    // ------------------------------------------------------------------------------------------------ upgrades

    /**
     * Credits a Reliquary Shard used on a Covenant to members who have belonged long enough.
     */
    public static void onExpansion(VaultStorage storage) {
        CovenantLedger ledger = storage.getLedger();
        if (ledger != null) {
            long minMillis = SoulsVaultConfig.party().upgradeCreditMinutes() * 60_000L;
            ledger.creditExpansion(storage.getExpansions(), System.currentTimeMillis(), minMillis);
        }
    }

    // ------------------------------------------------------------------------------------------------ reconcile

    /**
     * Picks up membership changes made anywhere (other mods, offline edits): members no longer in the party leave,
     * Covenants whose party vanished or stopped sharing dissolve, members with an empty Casket join automatically,
     * and everyone else who still has to choose gets a (single) chat prompt.
     */
    public static void reconcile(MinecraftServer server) {
        if (server.getTickCount() % RECONCILE_INTERVAL_TICKS != 0) {
            return;
        }
        PartyRegistry registry = PartyService.registry(server);
        for (Map.Entry<String, UUID> covenant : registry.getCovenants().entrySet()) {
            String partyKey = covenant.getKey();
            String storageId = "party:" + partyKey;
            CovenantLedger ledger = storageOf(server, storageId).getLedger();
            if (ledger == null) {
                continue;
            }
            PartyView party = null;
            for (UUID member : List.copyOf(ledger.members())) {
                PartyView current = PartyService.getParty(server, member);
                if (current == null || !current.key().equals(partyKey)) {
                    leave(server, storageId, member, SoulsVaultConfig.party().returnContributionsOnLeave());
                } else {
                    party = current;
                }
            }
            if (party != null && !PartyService.sharesCasket(party)) {
                dissolve(server, storageId, party.leader() != null ? party.leader() : covenant.getValue());
            } else if (party != null && party.leader() != null && !party.leader().equals(covenant.getValue())) {
                registry.markCovenant(partyKey, party.leader());
            }
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PartyView party = PartyService.getParty(server, player.getUUID());
            if (party == null || !PartyService.sharesCasket(party) || isMember(server, party, player.getUUID())) {
                continue;
            }
            if (Services.PLATFORM.getPlayerVaultData(server, player.getUUID()).getPrivateStorage().isEmpty()) {
                join(player, party, CasketJoinPolicy.MERGE, false);
            } else if (!party.key().equals(PROMPTED.get(player.getUUID()))) {
                PROMPTED.put(player.getUUID(), party.key());
                prompt(player, party);
            }
        }
    }

    public static void prompt(ServerPlayer player, PartyView party) {
        MutableComponent open = Component.translatable("gui.soulsvault.party.invite.open")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/casket party gui")));
        player.sendSystemMessage(PartyService.prefix()
                .append(Component.translatable("gui.soulsvault.covenant.prompt", party.name()))
                .append(" ")
                .append(open));
        PartyService.forceSync(player);
    }

    public static void forget(UUID player) {
        PROMPTED.remove(player);
    }

    /**
     * Lets every member of the party be prompted again (after sharing is turned on).
     */
    public static void resetPrompts(PartyView party) {
        for (UUID id : party.members()) {
            PROMPTED.remove(id);
        }
    }
}
