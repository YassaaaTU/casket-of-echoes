package com.soulsvault.party.provider;

import com.soulsvault.Constants;
import com.soulsvault.config.SoulsVaultConfig.PartyProviderType;
import com.soulsvault.party.PartyProvider;
import com.soulsvault.party.PartyRegistry;
import com.soulsvault.party.PartyView;
import com.soulsvault.platform.Services;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Open Parties and Claims parties (soft dependency via reflection on {@code xaero.pac.common.server.api}).
 */
public final class OpacPartyProvider implements PartyProvider {
    private static final String MOD_ID = "openpartiesandclaims";

    private boolean initialized;
    private Method getApiMethod;
    private Method getPartyManagerMethod;
    private Method getPartyByMemberMethod;
    private Method getIdMethod;
    private Method getDefaultNameMethod;
    private Method getOwnerMethod;
    private Method getMemberInfoStreamMethod;
    private Method getMemberUuidMethod;

    @Override
    public PartyProviderType type() {
        return PartyProviderType.OPAC;
    }

    @Override
    public boolean isAvailable() {
        return Services.PLATFORM.isModLoaded(MOD_ID) && init();
    }

    private synchronized boolean init() {
        if (this.initialized) {
            return this.getPartyByMemberMethod != null;
        }
        this.initialized = true;
        try {
            Class<?> apiClass = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            Class<?> managerClass = Class.forName("xaero.pac.common.server.parties.party.api.IPartyManagerAPI");
            Class<?> partyClass = Class.forName("xaero.pac.common.parties.party.api.IPartyAPI");
            Class<?> memberClass = Class.forName("xaero.pac.common.parties.party.member.api.IPartyMemberAPI");
            this.getApiMethod = apiClass.getMethod("get", MinecraftServer.class);
            this.getPartyManagerMethod = apiClass.getMethod("getPartyManager");
            this.getPartyByMemberMethod = managerClass.getMethod("getPartyByMember", UUID.class);
            this.getIdMethod = partyClass.getMethod("getId");
            this.getDefaultNameMethod = partyClass.getMethod("getDefaultName");
            this.getOwnerMethod = partyClass.getMethod("getOwner");
            this.getMemberInfoStreamMethod = partyClass.getMethod("getMemberInfoStream");
            this.getMemberUuidMethod = memberClass.getMethod("getUUID");
        } catch (Throwable t) {
            Constants.LOG.warn("Open Parties and Claims party integration unavailable: {}", t.toString());
            this.getPartyByMemberMethod = null;
        }
        return this.getPartyByMemberMethod != null;
    }

    @Override
    @Nullable
    public PartyView getParty(MinecraftServer server, UUID player, PartyRegistry registry) {
        if (!isAvailable()) {
            return null;
        }
        try {
            Object api = this.getApiMethod.invoke(null, server);
            Object manager = this.getPartyManagerMethod.invoke(api);
            Object party = this.getPartyByMemberMethod.invoke(manager, player);
            if (party == null) {
                return null;
            }
            UUID partyId = (UUID) this.getIdMethod.invoke(party);
            String name = (String) this.getDefaultNameMethod.invoke(party);
            Object ownerInfo = this.getOwnerMethod.invoke(party);
            UUID owner = ownerInfo != null ? (UUID) this.getMemberUuidMethod.invoke(ownerInfo) : null;
            List<UUID> members = new ArrayList<>();
            try (Stream<?> stream = (Stream<?>) this.getMemberInfoStreamMethod.invoke(party)) {
                for (Object member : stream.toList()) {
                    members.add((UUID) this.getMemberUuidMethod.invoke(member));
                }
            }
            if (owner != null && members.remove(owner)) {
                members.add(0, owner);
            }
            String key = "opac:" + partyId;
            return new PartyView(key, PartyProviderType.OPAC, name, owner, List.copyOf(members),
                    registry.getExternalSettings(key));
        } catch (Throwable t) {
            Constants.LOG.debug("Open Parties and Claims party lookup failed for {}: {}", player, t.toString());
            return null;
        }
    }
}
