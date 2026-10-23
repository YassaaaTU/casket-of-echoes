package com.soulsvault.party;

import com.soulsvault.config.SoulsVaultConfig.PartyProviderType;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Source of party membership: Casket of Echoes' built-in parties or another mod's team system.
 */
public interface PartyProvider {

    PartyProviderType type();

    /** Whether the backing mod is installed and ready. */
    boolean isAvailable();

    /**
     * The player's party, or {@code null} when they are not in one (personal FTB "player teams" don't count).
     */
    @Nullable
    PartyView getParty(MinecraftServer server, UUID player, PartyRegistry registry);
}
