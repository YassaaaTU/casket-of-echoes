package com.soulsvault.party;

import com.soulsvault.config.SoulsVaultConfig.PartyProviderType;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What one client needs about their party for the party HUD and the party GUI.
 *
 * @param viewerCanManage whether the receiving player may change options (leader, or operator for vanilla teams)
 * @param viewerCanInvite whether the receiving player may send invites (built-in parties only)
 * @param sharesCasket    the effective shared-Casket state (leader option resolved against the server rule)
 * @param viewerInCovenant whether the receiving player joined the Covenant (else they still have to choose)
 */
public record PartySnapshot(
        String key,
        PartyProviderType provider,
        String name,
        @Nullable UUID leader,
        boolean viewerCanManage,
        boolean viewerCanInvite,
        boolean sharesCasket,
        boolean viewerInCovenant,
        PartySettings settings,
        List<Member> members
) {
    public static final int MAX_EFFECTS = 8;

    /**
     * A visible status effect. Durations are not sent (they would change every second); {@code ending} flags the
     * last 10 seconds so the HUD can blink the icon.
     */
    public record Effect(String id, int amplifier, boolean ending, boolean harmful) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUtf(this.id, 256);
            buf.writeVarInt(this.amplifier);
            buf.writeBoolean(this.ending);
            buf.writeBoolean(this.harmful);
        }

        static Effect decode(FriendlyByteBuf buf) {
            return new Effect(buf.readUtf(256), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
        }
    }

    /**
     * Live member status. Health values are rounded to half points to keep updates small.
     *
     * @param dimension the member's dimension id (the client compares it with its own), empty when offline
     * @param food      hunger points (0-20)
     * @param armor     armor points
     * @param effects   up to {@link #MAX_EFFECTS} visible status effects, harmful first
     */
    public record Member(
            UUID id,
            String name,
            boolean online,
            float health,
            float maxHealth,
            float absorption,
            boolean recentlyHurt,
            boolean dead,
            String dimension,
            int food,
            int armor,
            List<Effect> effects
    ) {
        void encode(FriendlyByteBuf buf) {
            buf.writeUUID(this.id);
            buf.writeUtf(this.name, 64);
            buf.writeBoolean(this.online);
            buf.writeFloat(this.health);
            buf.writeFloat(this.maxHealth);
            buf.writeFloat(this.absorption);
            buf.writeBoolean(this.recentlyHurt);
            buf.writeBoolean(this.dead);
            buf.writeUtf(this.dimension, 256);
            buf.writeVarInt(this.food);
            buf.writeVarInt(this.armor);
            buf.writeVarInt(this.effects.size());
            for (Effect effect : this.effects) {
                effect.encode(buf);
            }
        }

        static Member decode(FriendlyByteBuf buf) {
            return new Member(
                    buf.readUUID(),
                    buf.readUtf(64),
                    buf.readBoolean(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readUtf(256),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    decodeEffects(buf)
            );
        }

        private static List<Effect> decodeEffects(FriendlyByteBuf buf) {
            int count = Math.min(MAX_EFFECTS, buf.readVarInt());
            List<Effect> effects = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                effects.add(Effect.decode(buf));
            }
            return effects;
        }
    }

    public boolean isBuiltin() {
        return this.provider == PartyProviderType.BUILTIN;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.key, 256);
        buf.writeEnum(this.provider);
        buf.writeUtf(this.name, 64);
        buf.writeNullable(this.leader, FriendlyByteBuf::writeUUID);
        buf.writeBoolean(this.viewerCanManage);
        buf.writeBoolean(this.viewerCanInvite);
        buf.writeBoolean(this.sharesCasket);
        buf.writeBoolean(this.viewerInCovenant);
        this.settings.encode(buf);
        buf.writeVarInt(this.members.size());
        for (Member member : this.members) {
            member.encode(buf);
        }
    }

    public static PartySnapshot decode(FriendlyByteBuf buf) {
        String key = buf.readUtf(256);
        PartyProviderType provider = buf.readEnum(PartyProviderType.class);
        String name = buf.readUtf(64);
        UUID leader = buf.readNullable(FriendlyByteBuf::readUUID);
        boolean canManage = buf.readBoolean();
        boolean canInvite = buf.readBoolean();
        boolean sharesCasket = buf.readBoolean();
        boolean inCovenant = buf.readBoolean();
        PartySettings settings = PartySettings.decode(buf);
        int count = Math.min(256, buf.readVarInt());
        List<Member> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            members.add(Member.decode(buf));
        }
        return new PartySnapshot(key, provider, name, leader, canManage, canInvite, sharesCasket, inCovenant, settings, members);
    }
}
