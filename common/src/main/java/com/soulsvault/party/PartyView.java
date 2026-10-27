package com.soulsvault.party;

import com.soulsvault.config.SoulsVaultConfig.PartyProviderType;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * A provider-agnostic snapshot of a player's party, whatever system defines it.
 *
 * @param key      stable id, e.g. {@code builtin:<uuid>}, {@code ftb:<uuid>}, {@code opac:<uuid>}, {@code team:<name>}
 * @param leader   the party leader/owner, or {@code null} when the provider has none (vanilla teams: operators manage)
 * @param members  member ids in display order
 * @param settings leader options, stored by Casket of Echoes even for external providers
 */
public record PartyView(
        String key,
        PartyProviderType provider,
        String name,
        @Nullable UUID leader,
        List<UUID> members,
        PartySettings settings
) {
    public boolean isBuiltin() {
        return this.provider == PartyProviderType.BUILTIN;
    }

    public boolean isLeader(UUID player) {
        return this.leader != null && this.leader.equals(player);
    }

    /**
     * Id of the party's shared Casket storage.
     */
    public String storageId() {
        return "party:" + this.key;
    }
}
