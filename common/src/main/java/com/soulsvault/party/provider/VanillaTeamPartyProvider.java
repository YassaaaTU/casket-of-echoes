package com.soulsvault.party.provider;

import com.mojang.authlib.GameProfile;
import com.soulsvault.config.SoulsVaultConfig.PartyProviderType;
import com.soulsvault.party.PartyProvider;
import com.soulsvault.party.PartyRegistry;
import com.soulsvault.party.PartyView;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.GameProfileCache;
import net.minecraft.world.scores.PlayerTeam;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vanilla scoreboard teams ({@code /team}) as parties. Teams have no owner, so operators manage their options.
 */
public final class VanillaTeamPartyProvider implements PartyProvider {
    /**
     * Ids of offline team members by name, resolved off the server thread once per session: a name missing from the
     * profile cache would otherwise trigger a blocking Mojang API lookup on every party refresh. Names that are not
     * players (teams can hold entities) stay unresolved.
     */
    private final Map<String, Optional<UUID>> offlineIds = new ConcurrentHashMap<>();

    @Override
    public PartyProviderType type() {
        return PartyProviderType.VANILLA_TEAMS;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    @Nullable
    public PartyView getParty(MinecraftServer server, UUID player, PartyRegistry registry) {
        String playerName = nameOf(server, player);
        if (playerName == null) {
            return null;
        }
        PlayerTeam team = server.getScoreboard().getPlayersTeam(playerName);
        if (team == null) {
            return null;
        }
        List<UUID> members = new ArrayList<>();
        for (String memberName : team.getPlayers()) {
            UUID memberId = idOf(server, memberName);
            if (memberId != null) {
                members.add(memberId);
            }
        }
        String key = "team:" + team.getName();
        return new PartyView(
                key,
                PartyProviderType.VANILLA_TEAMS,
                team.getDisplayName().getString(),
                null,
                List.copyOf(members),
                registry.getExternalSettings(key)
        );
    }

    @Nullable
    private static String nameOf(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) {
            return online.getScoreboardName();
        }
        if (server.getProfileCache() == null) {
            return null;
        }
        return server.getProfileCache().get(player).map(GameProfile::getName).orElse(null);
    }

    @Nullable
    private UUID idOf(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getUUID();
        }
        GameProfileCache cache = server.getProfileCache();
        if (cache == null) {
            return null;
        }
        Optional<UUID> known = this.offlineIds.get(name);
        if (known != null) {
            return known.orElse(null);
        }
        this.offlineIds.put(name, Optional.empty());
        try {
            cache.getAsync(name, profile -> profile.ifPresent(found -> this.offlineIds.put(name, Optional.of(found.getId()))));
        } catch (IllegalStateException e) {
            // No executor yet (server still starting); retried on a later refresh
            this.offlineIds.remove(name);
        }
        return null;
    }
}
