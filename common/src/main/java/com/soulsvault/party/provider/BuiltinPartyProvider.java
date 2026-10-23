package com.soulsvault.party.provider;

import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.config.SoulsVaultConfig.PartyProviderType;
import com.soulsvault.party.PartyProvider;
import com.soulsvault.party.PartyRegistry;
import com.soulsvault.party.PartyView;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Casket of Echoes' own parties, managed through the party GUI and {@code /casket party}.
 */
public final class BuiltinPartyProvider implements PartyProvider {

    @Override
    public PartyProviderType type() {
        return PartyProviderType.BUILTIN;
    }

    @Override
    public boolean isAvailable() {
        return SoulsVaultConfig.party().builtinPartiesEnabled();
    }

    @Override
    @Nullable
    public PartyView getParty(MinecraftServer server, UUID player, PartyRegistry registry) {
        PartyRegistry.BuiltinParty party = registry.getPartyOf(player);
        if (party == null) {
            return null;
        }
        return new PartyView(
                "builtin:" + party.getId(),
                PartyProviderType.BUILTIN,
                party.getName(),
                party.getLeader(),
                List.copyOf(party.getMembers()),
                party.getSettings()
        );
    }
}
