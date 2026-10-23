package com.soulsvault.party.provider;

import com.soulsvault.Constants;
import com.soulsvault.config.SoulsVaultConfig.PartyProviderType;
import com.soulsvault.party.PartyProvider;
import com.soulsvault.party.PartyRegistry;
import com.soulsvault.party.PartyView;
import com.soulsvault.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * FTB Teams party teams as parties (soft dependency via reflection on {@code dev.ftb.mods.ftbteams.api}).
 * Personal "player teams" are ignored. Note that FTB Quests progress is already shared inside an FTB party team,
 * by FTB's own design.
 */
public final class FtbTeamsPartyProvider implements PartyProvider {
    private static final String MOD_ID = "ftbteams";

    private boolean initialized;
    private Method apiMethod;
    private Method isManagerLoadedMethod;
    private Method getManagerMethod;
    private Method getTeamForPlayerMethod;
    private Method isPartyTeamMethod;
    private Method getTeamIdMethod;
    private Method getNameMethod;
    private Method getOwnerMethod;
    private Method getMembersMethod;

    @Override
    public PartyProviderType type() {
        return PartyProviderType.FTB_TEAMS;
    }

    @Override
    public boolean isAvailable() {
        return Services.PLATFORM.isModLoaded(MOD_ID) && init();
    }

    private synchronized boolean init() {
        if (this.initialized) {
            return this.getTeamForPlayerMethod != null;
        }
        this.initialized = true;
        try {
            Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            Class<?> apiInterface = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API");
            Class<?> managerClass = Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");
            Class<?> teamClass = Class.forName("dev.ftb.mods.ftbteams.api.Team");
            this.apiMethod = apiClass.getMethod("api");
            this.isManagerLoadedMethod = apiInterface.getMethod("isManagerLoaded");
            this.getManagerMethod = apiInterface.getMethod("getManager");
            this.getTeamForPlayerMethod = managerClass.getMethod("getTeamForPlayerID", UUID.class);
            this.isPartyTeamMethod = teamClass.getMethod("isPartyTeam");
            this.getTeamIdMethod = teamClass.getMethod("getId");
            this.getNameMethod = teamClass.getMethod("getName");
            this.getOwnerMethod = teamClass.getMethod("getOwner");
            this.getMembersMethod = teamClass.getMethod("getMembers");
        } catch (Throwable t) {
            Constants.LOG.warn("FTB Teams party integration unavailable: {}", t.toString());
            this.getTeamForPlayerMethod = null;
        }
        return this.getTeamForPlayerMethod != null;
    }

    @Override
    @Nullable
    public PartyView getParty(MinecraftServer server, UUID player, PartyRegistry registry) {
        if (!isAvailable()) {
            return null;
        }
        try {
            Object api = this.apiMethod.invoke(null);
            if (!(Boolean) this.isManagerLoadedMethod.invoke(api)) {
                return null;
            }
            Object manager = this.getManagerMethod.invoke(api);
            Optional<?> team = (Optional<?>) this.getTeamForPlayerMethod.invoke(manager, player);
            if (team.isEmpty() || !(Boolean) this.isPartyTeamMethod.invoke(team.get())) {
                return null;
            }
            Object value = team.get();
            UUID teamId = (UUID) this.getTeamIdMethod.invoke(value);
            Component name = (Component) this.getNameMethod.invoke(value);
            UUID owner = (UUID) this.getOwnerMethod.invoke(value);
            List<UUID> members = new ArrayList<>((Collection<UUID>) this.getMembersMethod.invoke(value));
            if (owner != null && members.remove(owner)) {
                members.add(0, owner);
            }
            String key = "ftb:" + teamId;
            return new PartyView(key, PartyProviderType.FTB_TEAMS, name.getString(), owner, List.copyOf(members),
                    registry.getExternalSettings(key));
        } catch (Throwable t) {
            Constants.LOG.debug("FTB Teams party lookup failed for {}: {}", player, t.toString());
            return null;
        }
    }
}
