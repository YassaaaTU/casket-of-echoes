package com.soulsvault.party;

import com.mojang.authlib.GameProfile;
import com.soulsvault.Constants;
import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.config.SoulsVaultConfig.PartyProviderType;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultStorage;
import com.soulsvault.core.VaultSync;
import com.soulsvault.network.PacketC2SPartyAction;
import com.soulsvault.network.PacketS2CPartySync;
import com.soulsvault.party.provider.BuiltinPartyProvider;
import com.soulsvault.party.provider.FtbTeamsPartyProvider;
import com.soulsvault.party.provider.OpacPartyProvider;
import com.soulsvault.party.provider.VanillaTeamPartyProvider;
import com.soulsvault.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side party logic shared by every party provider: who is in which party, the shared Casket binding,
 * built-in party management (invites with the casket-choice flow, kick, leave, disband, settings), friendly-fire
 * protection, advancement sharing, and the party HUD/GUI sync.
 */
public final class PartyService {
    private static final int MAX_NAME_LENGTH = 32;
    private static final int SYNC_INTERVAL_TICKS = 5;
    private static final int RECENT_HURT_TICKS = 10;

    private static final BuiltinPartyProvider BUILTIN = new BuiltinPartyProvider();
    private static final List<PartyProvider> PROVIDERS = List.of(
            BUILTIN,
            new FtbTeamsPartyProvider(),
            new OpacPartyProvider(),
            new VanillaTeamPartyProvider()
    );

    private static final Map<UUID, Optional<PartyView>> VIEW_CACHE = new HashMap<>();
    private static int viewCacheTick = Integer.MIN_VALUE;
    private static final Map<UUID, Integer> LAST_HURT_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_SYNC_HASH = new ConcurrentHashMap<>();

    private PartyService() {
    }

    // ------------------------------------------------------------------------------------------------ lookup

    public static PartyRegistry registry(MinecraftServer server) {
        return Services.PLATFORM.getPartyRegistry(server);
    }

    /**
     * The provider selected by {@code party.provider}, or {@code null} when parties are off / unavailable.
     */
    @Nullable
    public static PartyProvider activeProvider() {
        PartyProviderType type = SoulsVaultConfig.party().provider();
        if (type == PartyProviderType.NONE) {
            return null;
        }
        if (type == PartyProviderType.AUTO) {
            for (PartyProviderType preferred : new PartyProviderType[]{PartyProviderType.FTB_TEAMS, PartyProviderType.OPAC}) {
                PartyProvider provider = providerOf(preferred);
                if (provider != null && provider.isAvailable()) {
                    return provider;
                }
            }
            return BUILTIN.isAvailable() ? BUILTIN : null;
        }
        PartyProvider provider = providerOf(type);
        return provider != null && provider.isAvailable() ? provider : null;
    }

    @Nullable
    private static PartyProvider providerOf(PartyProviderType type) {
        for (PartyProvider provider : PROVIDERS) {
            if (provider.type() == type) {
                return provider;
            }
        }
        return null;
    }

    public static boolean builtinActive() {
        PartyProvider provider = activeProvider();
        return provider != null && provider.type() == PartyProviderType.BUILTIN;
    }

    /**
     * The player's party from the active provider, cached for the current server tick.
     */
    @Nullable
    public static synchronized PartyView getParty(MinecraftServer server, UUID player) {
        int tick = server.getTickCount();
        if (tick != viewCacheTick) {
            VIEW_CACHE.clear();
            viewCacheTick = tick;
        }
        Optional<PartyView> cached = VIEW_CACHE.get(player);
        if (cached != null) {
            return cached.orElse(null);
        }
        PartyProvider provider = activeProvider();
        PartyView view = provider != null ? provider.getParty(server, player, registry(server)) : null;
        VIEW_CACHE.put(player, Optional.ofNullable(view));
        return view;
    }

    public static synchronized void invalidate() {
        VIEW_CACHE.clear();
        LAST_SYNC_HASH.clear();
    }

    public static boolean sharesCasket(PartyView party) {
        return SoulsVaultConfig.party().sharedCasket().resolve(party.settings().sharedCasket());
    }

    /**
     * The shared storage id the player should use, or {@code null} for their private Casket. Being in a sharing
     * party is not enough: the player must have joined its Covenant (made their casket choice).
     */
    @Nullable
    public static String sharedStorageId(MinecraftServer server, UUID player) {
        PartyView party = getParty(server, player);
        return party != null && sharesCasket(party) && CovenantService.isMember(server, party, player) ? party.storageId() : null;
    }

    @Nullable
    public static String sharedPartyName(MinecraftServer server, UUID player) {
        PartyView party = getParty(server, player);
        return party != null ? party.name() : null;
    }

    public static boolean canManage(ServerPlayer player, PartyView party) {
        return party.leader() != null ? party.isLeader(player.getUUID()) : player.hasPermissions(2);
    }

    public static boolean canInvite(ServerPlayer player, PartyView party) {
        return party.isBuiltin() && (party.isLeader(player.getUUID()) || party.settings().membersCanInvite());
    }

    // ------------------------------------------------------------------------------------------------ actions

    public static void handleAction(ServerPlayer player, PacketC2SPartyAction packet) {
        Component error = switch (packet.getAction()) {
            case CREATE -> createParty(player);
            case INVITE -> invite(player, packet.getText(), packet.getPolicy());
            case ACCEPT -> packet.getTarget() == null ? null
                    : accept(player, packet.getTarget(), packet.getPolicy(), packet.isConfirmed());
            case DECLINE -> packet.getTarget() == null ? null : decline(player, packet.getTarget());
            case LEAVE -> leave(player);
            case KICK -> packet.getTarget() == null ? null : kick(player, packet.getTarget());
            case TRANSFER -> packet.getTarget() == null ? null : transferLeadership(player, packet.getTarget());
            case DISBAND -> disband(player);
            case SAVE_SETTINGS -> packet.getSettings() == null ? null
                    : saveSettings(player, packet.getText(), packet.getSettings());
            case MERGE_PRIVATE -> mergePrivateCasket(player);
            case COVENANT_JOIN -> joinCovenant(player, packet.getPolicy(), packet.isConfirmed());
        };
        if (error != null) {
            player.sendSystemMessage(prefix().append(error.copy().withStyle(ChatFormatting.RED)));
        }
        forceSync(player);
    }

    @Nullable
    public static Component createParty(ServerPlayer leader) {
        if (!builtinActive()) {
            return Component.translatable("gui.soulsvault.party.error.builtin_disabled");
        }
        MinecraftServer server = leader.server;
        if (getParty(server, leader.getUUID()) != null) {
            return Component.translatable("gui.soulsvault.party.error.already_in_party");
        }
        PartyRegistry registry = registry(server);
        String name = Component.translatable("gui.soulsvault.party.default_name", leader.getGameProfile().getName()).getString();
        PartyRegistry.BuiltinParty party = registry.create(leader.getUUID(), truncate(name));
        invalidate();
        leader.sendSystemMessage(prefix().append(Component.translatable("gui.soulsvault.party.created", party.getName())));

        // The founder makes the same casket choice as everyone else (joined directly if their Casket is empty).
        PartyView view = getParty(server, leader.getUUID());
        if (view != null && sharesCasket(view)) {
            if (Services.PLATFORM.getPlayerVaultData(server, leader.getUUID()).getPrivateStorage().isEmpty()) {
                CovenantService.join(leader, view, CasketJoinPolicy.MERGE, false);
            } else {
                CovenantService.prompt(leader, view);
            }
        }
        return null;
    }

    /**
     * Joins the party's Covenant Casket for a member who is in a sharing party but hasn't chosen yet.
     */
    @Nullable
    public static Component joinCovenant(ServerPlayer player, CasketJoinPolicy policy, boolean confirmedOverride) {
        PartyView party = getParty(player.server, player.getUUID());
        if (party == null || !sharesCasket(party)) {
            return Component.translatable("gui.soulsvault.party.error.not_shared");
        }
        return CovenantService.join(player, party, policy, confirmedOverride);
    }

    @Nullable
    public static Component invite(ServerPlayer inviter, String targetName, CasketJoinPolicy policy) {
        if (!builtinActive()) {
            return Component.translatable("gui.soulsvault.party.error.builtin_disabled");
        }
        MinecraftServer server = inviter.server;
        PartyView party = getParty(server, inviter.getUUID());
        if (party == null) {
            Component error = createParty(inviter);
            if (error != null) {
                return error;
            }
            party = getParty(server, inviter.getUUID());
            if (party == null) {
                return Component.translatable("gui.soulsvault.party.error.generic");
            }
        }
        if (!canInvite(inviter, party)) {
            return Component.translatable("gui.soulsvault.party.error.no_permission");
        }
        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName.trim());
        if (target == null) {
            return Component.translatable("gui.soulsvault.party.error.not_online", targetName);
        }
        if (target == inviter) {
            return Component.translatable("gui.soulsvault.party.error.self");
        }
        if (getParty(server, target.getUUID()) != null) {
            return Component.translatable("gui.soulsvault.party.error.target_in_party", target.getDisplayName());
        }
        int maxMembers = Math.max(2, SoulsVaultConfig.party().maxMembers());
        if (party.members().size() >= maxMembers) {
            return Component.translatable("gui.soulsvault.party.error.full", maxMembers);
        }
        Component policyError = checkPolicyAllowed(policy);
        if (policyError != null) {
            return policyError;
        }

        UUID partyId = UUID.fromString(party.key().substring("builtin:".length()));
        PartyInvite invite = new PartyInvite(
                partyId,
                party.name(),
                inviter.getUUID(),
                inviter.getGameProfile().getName(),
                policy,
                System.currentTimeMillis() + PartyInvite.LIFETIME_MILLIS,
                sharesCasket(party)
        );
        registry(server).addInvite(target.getUUID(), invite);

        MutableComponent open = Component.translatable("gui.soulsvault.party.invite.open")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/casket party gui invites")));
        target.sendSystemMessage(prefix()
                .append(Component.translatable("gui.soulsvault.party.invite.received", inviter.getDisplayName(), party.name()))
                .append(" ")
                .append(open));
        inviter.sendSystemMessage(prefix().append(Component.translatable("gui.soulsvault.party.invite.sent", target.getDisplayName())));
        forceSync(target);
        return null;
    }

    @Nullable
    private static Component checkPolicyAllowed(CasketJoinPolicy policy) {
        SoulsVaultConfig.Party config = SoulsVaultConfig.party();
        if (policy == CasketJoinPolicy.MERGE && !config.allowCasketMerge()) {
            return Component.translatable("gui.soulsvault.party.error.merge_disabled");
        }
        if (policy == CasketJoinPolicy.OVERRIDE && !config.allowCasketOverride()) {
            return Component.translatable("gui.soulsvault.party.error.override_disabled");
        }
        return null;
    }

    @Nullable
    public static Component accept(ServerPlayer invitee, UUID partyId, CasketJoinPolicy chosen, boolean confirmedOverride) {
        if (!builtinActive()) {
            return Component.translatable("gui.soulsvault.party.error.builtin_disabled");
        }
        MinecraftServer server = invitee.server;
        PartyRegistry registry = registry(server);
        PartyInvite invite = registry.getInvites(invitee.getUUID()).stream()
                .filter(candidate -> candidate.partyId().equals(partyId))
                .findFirst()
                .orElse(null);
        PartyRegistry.BuiltinParty party = registry.getParty(partyId);
        if (invite == null || party == null) {
            return Component.translatable("gui.soulsvault.party.error.invite_expired");
        }
        if (getParty(server, invitee.getUUID()) != null) {
            return Component.translatable("gui.soulsvault.party.error.already_in_party");
        }
        int maxMembers = Math.max(2, SoulsVaultConfig.party().maxMembers());
        if (party.getMembers().size() >= maxMembers) {
            return Component.translatable("gui.soulsvault.party.error.full", maxMembers);
        }

        PartyView leaderView = getParty(server, party.getLeader());
        boolean shares = leaderView != null && sharesCasket(leaderView);
        VaultStorage privateStorage = Services.PLATFORM.getPlayerVaultData(server, invitee.getUUID()).getPrivateStorage();
        if (shares && !privateStorage.isEmpty()) {
            if (chosen != invite.policy() && chosen != CasketJoinPolicy.EMPTY_FIRST && chosen != CasketJoinPolicy.OVERRIDE) {
                return Component.translatable("gui.soulsvault.party.error.policy_not_offered");
            }
            Component policyError = checkPolicyAllowed(chosen);
            if (policyError != null) {
                return policyError;
            }
            if (chosen == CasketJoinPolicy.EMPTY_FIRST) {
                return Component.translatable("gui.soulsvault.party.error.casket_not_empty", privateStorage.totalItems());
            }
            if (chosen == CasketJoinPolicy.OVERRIDE && !confirmedOverride) {
                return Component.translatable("gui.soulsvault.party.error.override_unconfirmed");
            }
        }

        registry.takeInvite(invitee.getUUID(), partyId);
        registry.addMember(party, invitee.getUUID());
        invalidate();

        Component joined = Component.translatable("gui.soulsvault.party.joined", invitee.getDisplayName(), party.getName());
        for (ServerPlayer member : onlineMembers(server, party.getMembers())) {
            member.sendSystemMessage(prefix().append(joined));
        }
        PartyView view = getParty(server, invitee.getUUID());
        if (shares && view != null) {
            CovenantService.join(invitee, view, chosen, confirmedOverride);
        }
        VaultSync.resync(invitee);
        return null;
    }

    @Nullable
    public static Component decline(ServerPlayer invitee, UUID partyId) {
        PartyRegistry registry = registry(invitee.server);
        PartyInvite invite = registry.takeInvite(invitee.getUUID(), partyId);
        if (invite != null) {
            ServerPlayer inviter = invitee.server.getPlayerList().getPlayer(invite.inviter());
            if (inviter != null) {
                inviter.sendSystemMessage(prefix().append(
                        Component.translatable("gui.soulsvault.party.invite.declined", invitee.getDisplayName())));
            }
        }
        return null;
    }

    @Nullable
    public static Component leave(ServerPlayer player) {
        MinecraftServer server = player.server;
        PartyRegistry registry = registry(server);
        PartyRegistry.BuiltinParty party = registry.getPartyOf(player.getUUID());
        if (party == null) {
            return Component.translatable("gui.soulsvault.party.error.not_builtin_member");
        }
        String storageId = "party:builtin:" + party.getId();
        List<UUID> remaining = new ArrayList<>(party.getMembers());
        remaining.remove(player.getUUID());

        registry.removeMember(party, player.getUUID());
        invalidate();
        // The last member out dissolves the Covenant (everyone gets their items back).
        CovenantService.leave(server, storageId, player.getUUID(), SoulsVaultConfig.party().returnContributionsOnLeave());
        player.sendSystemMessage(prefix().append(Component.translatable("gui.soulsvault.party.left", party.getName())));
        for (ServerPlayer member : onlineMembers(server, remaining)) {
            member.sendSystemMessage(prefix().append(
                    Component.translatable("gui.soulsvault.party.member_left", player.getDisplayName())));
        }
        VaultSync.resync(player);
        return null;
    }

    @Nullable
    public static Component kick(ServerPlayer leader, UUID target) {
        MinecraftServer server = leader.server;
        PartyRegistry registry = registry(server);
        PartyRegistry.BuiltinParty party = registry.getPartyOf(leader.getUUID());
        if (party == null || !party.getLeader().equals(leader.getUUID())) {
            return Component.translatable("gui.soulsvault.party.error.no_permission");
        }
        if (target.equals(leader.getUUID()) || !party.getMembers().contains(target)) {
            return Component.translatable("gui.soulsvault.party.error.generic");
        }
        String storageId = "party:builtin:" + party.getId();
        registry.removeMember(party, target);
        invalidate();
        CovenantService.leave(server, storageId, target, SoulsVaultConfig.party().returnContributionsOnLeave());
        String name = nameOf(server, target);
        for (ServerPlayer member : onlineMembers(server, party.getMembers())) {
            member.sendSystemMessage(prefix().append(Component.translatable("gui.soulsvault.party.kicked", name)));
        }
        ServerPlayer kicked = server.getPlayerList().getPlayer(target);
        if (kicked != null) {
            kicked.sendSystemMessage(prefix().append(Component.translatable("gui.soulsvault.party.you_were_kicked", party.getName())));
            VaultSync.resync(kicked);
            forceSync(kicked);
        }
        VaultSync.resyncViewers(server, storageId);
        return null;
    }

    @Nullable
    public static Component transferLeadership(ServerPlayer leader, UUID target) {
        PartyRegistry registry = registry(leader.server);
        PartyRegistry.BuiltinParty party = registry.getPartyOf(leader.getUUID());
        if (party == null || !party.getLeader().equals(leader.getUUID()) || !party.getMembers().contains(target)) {
            return Component.translatable("gui.soulsvault.party.error.no_permission");
        }
        registry.setLeader(party, target);
        invalidate();
        Component message = Component.translatable("gui.soulsvault.party.new_leader", nameOf(leader.server, target));
        for (ServerPlayer member : onlineMembers(leader.server, party.getMembers())) {
            member.sendSystemMessage(prefix().append(message));
        }
        return null;
    }

    @Nullable
    public static Component disband(ServerPlayer leader) {
        MinecraftServer server = leader.server;
        PartyRegistry registry = registry(server);
        PartyRegistry.BuiltinParty party = registry.getPartyOf(leader.getUUID());
        if (party == null || !party.getLeader().equals(leader.getUUID())) {
            return Component.translatable("gui.soulsvault.party.error.no_permission");
        }
        String storageId = "party:builtin:" + party.getId();
        List<UUID> members = new ArrayList<>(party.getMembers());
        String partyName = party.getName();
        registry.disband(party);
        invalidate();
        CovenantService.dissolve(server, storageId, leader.getUUID());
        for (ServerPlayer member : onlineMembers(server, members)) {
            member.sendSystemMessage(prefix().append(Component.translatable("gui.soulsvault.party.disbanded", partyName)));
            VaultSync.resync(member);
            forceSync(member);
        }
        return null;
    }

    @Nullable
    public static Component saveSettings(ServerPlayer manager, String requestedName, PartySettings settings) {
        MinecraftServer server = manager.server;
        PartyView party = getParty(server, manager.getUUID());
        if (party == null || !canManage(manager, party)) {
            return Component.translatable("gui.soulsvault.party.error.no_permission");
        }
        boolean sharedBefore = sharesCasket(party);
        boolean sharedAfter = SoulsVaultConfig.party().sharedCasket().resolve(settings.sharedCasket());

        PartyRegistry registry = registry(server);
        if (party.isBuiltin()) {
            PartyRegistry.BuiltinParty builtin = registry.getPartyOf(manager.getUUID());
            if (builtin == null) {
                return Component.translatable("gui.soulsvault.party.error.generic");
            }
            String name = truncate(requestedName.trim().isEmpty() ? builtin.getName() : stripFormatting(requestedName.trim()));
            registry.update(builtin, name, settings);
        } else {
            registry.setExternalSettings(party.key(), settings);
        }
        invalidate();

        if (sharedBefore && !sharedAfter) {
            CovenantService.dissolve(server, party.storageId(), party.leader() != null ? party.leader() : manager.getUUID());
        }
        Component message = Component.translatable("gui.soulsvault.party.settings_saved", manager.getDisplayName());
        for (ServerPlayer member : onlineMembers(server, party.members())) {
            member.sendSystemMessage(prefix().append(message));
            if (!sharedBefore && sharedAfter) {
                member.sendSystemMessage(prefix().append(Component.translatable("gui.soulsvault.party.shared_enabled")));
            }
        }
        if (!sharedBefore && sharedAfter) {
            CovenantService.resetPrompts(party);
        }
        // Turning progression sharing on catches everyone online up immediately.
        boolean sharingTurnedOn = (!party.settings().shareAdvancements() && settings.shareAdvancements())
                || (!party.settings().shareQuests() && settings.shareQuests());
        PartyView updated = sharingTurnedOn ? getParty(server, manager.getUUID()) : null;
        if (updated != null) {
            ProgressionSync.syncParty(server, updated);
        }
        return null;
    }

    @Nullable
    public static Component mergePrivateCasket(ServerPlayer player) {
        MinecraftServer server = player.server;
        PartyView party = getParty(server, player.getUUID());
        if (party == null || !sharesCasket(party)) {
            return Component.translatable("gui.soulsvault.party.error.not_shared");
        }
        if (!SoulsVaultConfig.party().allowCasketMerge()) {
            return Component.translatable("gui.soulsvault.party.error.merge_disabled");
        }
        if (!CovenantService.isMember(server, party, player.getUUID())) {
            return Component.translatable("gui.soulsvault.covenant.error.not_joined");
        }
        PlayerVaultData vault = Services.PLATFORM.getPlayerVaultData(server, player.getUUID());
        MergeResult merge = CovenantService.mergeInto(vault.getPrivateStorage(),
                Services.PLATFORM.getSharedStorage(server, party.storageId()), player.getUUID());
        player.sendSystemMessage(prefix().append(merge.describe(player.getDisplayName())));
        VaultSync.resyncViewers(server, party.storageId());
        VaultSync.resync(player);
        return null;
    }

    // ------------------------------------------------------------------------------------------------ storage moves

    /**
     * Result of merging a private Casket into a shared one.
     */
    public record MergeResult(long moved, long leftOver) {
        public Component describe(Component who) {
            return this.leftOver > 0L
                    ? Component.translatable("gui.soulsvault.party.merge.partial", who, this.moved, this.leftOver)
                    : Component.translatable("gui.soulsvault.party.merge.done", who, this.moved);
        }
    }

    // ------------------------------------------------------------------------------------------------ combat

    /**
     * Whether damage from {@code attacker} to {@code victim} should be cancelled because both belong to the same
     * party (players, their tamed pets, their mounts such as Book of Dragons dragons, and their projectiles).
     */
    public static boolean shouldPreventHarm(MinecraftServer server, Entity victim, @Nullable Entity attacker) {
        UUID victimOwner = resolveVictim(victim);
        UUID attackerOwner = resolveResponsible(attacker, 0);
        if (victimOwner == null || attackerOwner == null || victimOwner.equals(attackerOwner)) {
            return false;
        }
        PartyView party = getParty(server, attackerOwner);
        if (party == null || !party.members().contains(victimOwner)) {
            return false;
        }
        return SoulsVaultConfig.party().preventFriendlyFire().resolve(party.settings().preventFriendlyFire());
    }

    @Nullable
    private static UUID resolveVictim(Entity victim) {
        if (victim instanceof Player player) {
            return player.getUUID();
        }
        if (victim instanceof OwnableEntity ownable) {
            return ownable.getOwnerUUID();
        }
        return null;
    }

    /**
     * Follows projectile owners, pet owners, traceable owners (fangs, TNT) and riders back to a player.
     */
    @Nullable
    private static UUID resolveResponsible(@Nullable Entity entity, int depth) {
        if (entity == null || depth > 4) {
            return null;
        }
        if (entity instanceof Player player) {
            return player.getUUID();
        }
        if (entity instanceof Projectile projectile) {
            return resolveResponsible(projectile.getOwner(), depth + 1);
        }
        if (entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null) {
            return ownable.getOwnerUUID();
        }
        if (entity instanceof TraceableEntity traceable) {
            return resolveResponsible(traceable.getOwner(), depth + 1);
        }
        if (entity.getControllingPassenger() instanceof Player rider) {
            return rider.getUUID();
        }
        return null;
    }

    public static void markHurt(ServerPlayer player) {
        LAST_HURT_TICK.put(player.getUUID(), player.server.getTickCount());
    }

    // ------------------------------------------------------------------------------------------------ sharing

    /**
     * Grants an advancement earned by one online member to the other online members when the party shares them.
     */
    public static void onAdvancementEarned(ServerPlayer player, Advancement advancement) {
        ProgressionSync.onAdvancementEarned(player, advancement);
    }

    /**
     * Other online members that should receive a quest completed by {@code player} (FTB Quests integration).
     */
    public static List<ServerPlayer> questShareTargets(ServerPlayer player) {
        PartyView party = getParty(player.server, player.getUUID());
        if (party == null || !ProgressionSync.sharesQuests(party)) {
            return List.of();
        }
        List<ServerPlayer> targets = onlineMembers(player.server, party.members());
        targets.remove(player);
        return targets;
    }

    // ------------------------------------------------------------------------------------------------ HUD sync

    public static void forceSync(ServerPlayer player) {
        LAST_SYNC_HASH.remove(player.getUUID());
    }

    public static void forget(UUID playerId) {
        LAST_SYNC_HASH.remove(playerId);
        LAST_HURT_TICK.remove(playerId);
    }

    /**
     * Sends changed party snapshots (members' health, invites, settings) to online players every few ticks.
     */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % SYNC_INTERVAL_TICKS != 0) {
            return;
        }
        Map<String, List<PartySnapshot.Member>> membersByParty = new HashMap<>();
        boolean builtin = builtinActive();
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            PartyView party = getParty(server, viewer.getUUID());
            PartySnapshot snapshot = null;
            if (party != null) {
                List<PartySnapshot.Member> members = membersByParty.computeIfAbsent(
                        party.key(), key -> describeMembers(server, party));
                snapshot = new PartySnapshot(
                        party.key(),
                        party.provider(),
                        party.name(),
                        party.leader(),
                        canManage(viewer, party),
                        canInvite(viewer, party),
                        sharesCasket(party),
                        CovenantService.isMember(server, party, viewer.getUUID()),
                        party.settings(),
                        members
                );
            }
            List<PartyInvite> invites = builtin ? registry(server).getInvites(viewer.getUUID()) : List.of();
            long privateItems = Services.PLATFORM.getPlayerVaultData(server, viewer.getUUID()).getPrivateStorage().totalItems();
            int hash = Objects.hash(snapshot, invites, privateItems);
            Integer previous = LAST_SYNC_HASH.get(viewer.getUUID());
            if (previous == null || previous != hash) {
                LAST_SYNC_HASH.put(viewer.getUUID(), hash);
                Services.PLATFORM.sendToClient(viewer, new PacketS2CPartySync(snapshot, invites, privateItems));
            }
        }
    }

    private static List<PartySnapshot.Member> describeMembers(MinecraftServer server, PartyView party) {
        List<PartySnapshot.Member> members = new ArrayList<>(party.members().size());
        int now = server.getTickCount();
        for (UUID id : party.members()) {
            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online == null) {
                members.add(new PartySnapshot.Member(id, nameOf(server, id), false, 0.0F, 20.0F, 0.0F, false, false, "",
                        0, 0, List.of()));
                continue;
            }
            Integer lastHurt = LAST_HURT_TICK.get(id);
            members.add(new PartySnapshot.Member(
                    id,
                    online.getGameProfile().getName(),
                    true,
                    roundHalf(online.getHealth()),
                    roundHalf(online.getMaxHealth()),
                    roundHalf(online.getAbsorptionAmount()),
                    lastHurt != null && now - lastHurt <= RECENT_HURT_TICKS,
                    online.isDeadOrDying(),
                    online.level().dimension().location().toString(),
                    online.getFoodData().getFoodLevel(),
                    online.getArmorValue(),
                    describeEffects(online)
            ));
        }
        return members;
    }

    /**
     * Visible effects for the party HUD, harmful first, without durations (so they don't resend every second).
     */
    private static List<PartySnapshot.Effect> describeEffects(ServerPlayer player) {
        List<PartySnapshot.Effect> effects = new ArrayList<>();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            if (!instance.showIcon()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect());
            if (id == null) {
                continue;
            }
            boolean ending = !instance.isInfiniteDuration() && instance.getDuration() <= 200;
            effects.add(new PartySnapshot.Effect(id.toString(), instance.getAmplifier(), ending,
                    instance.getEffect().getCategory() == MobEffectCategory.HARMFUL));
        }
        effects.sort(Comparator.comparing(PartySnapshot.Effect::harmful).reversed().thenComparing(PartySnapshot.Effect::id));
        return effects.size() > PartySnapshot.MAX_EFFECTS ? List.copyOf(effects.subList(0, PartySnapshot.MAX_EFFECTS)) : effects;
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private static float roundHalf(float value) {
        return Math.round(value * 2.0F) / 2.0F;
    }

    public static MutableComponent prefix() {
        return Component.translatable("gui.soulsvault.party.prefix").withStyle(ChatFormatting.GOLD).append(" ");
    }

    private static List<ServerPlayer> onlineMembers(MinecraftServer server, List<UUID> members) {
        List<ServerPlayer> online = new ArrayList<>();
        for (UUID id : members) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }

    public static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            return online.getGameProfile().getName();
        }
        if (server.getProfileCache() != null) {
            Optional<GameProfile> profile = server.getProfileCache().get(id);
            if (profile.isPresent()) {
                return profile.get().getName();
            }
        }
        return id.toString().substring(0, 8);
    }

    private static String truncate(String name) {
        return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    private static String stripFormatting(String name) {
        String stripped = ChatFormatting.stripFormatting(name);
        return stripped != null ? stripped : "";
    }
}
