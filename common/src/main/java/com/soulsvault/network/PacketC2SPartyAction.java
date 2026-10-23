package com.soulsvault.network;

import com.soulsvault.party.CasketJoinPolicy;
import com.soulsvault.party.PartyService;
import com.soulsvault.party.PartySettings;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/**
 * Client-to-Server party management request from the party GUI. Every action is validated by {@link PartyService}.
 */
public final class PacketC2SPartyAction {

    public enum Action {
        CREATE,
        INVITE,
        ACCEPT,
        DECLINE,
        LEAVE,
        KICK,
        TRANSFER,
        DISBAND,
        SAVE_SETTINGS,
        MERGE_PRIVATE,
        COVENANT_JOIN
    }

    private final Action action;
    private final String text;
    @Nullable
    private final UUID target;
    private final CasketJoinPolicy policy;
    private final boolean confirmed;
    @Nullable
    private final PartySettings settings;

    private PacketC2SPartyAction(
            Action action,
            String text,
            @Nullable UUID target,
            CasketJoinPolicy policy,
            boolean confirmed,
            @Nullable PartySettings settings
    ) {
        this.action = Objects.requireNonNull(action);
        this.text = text == null ? "" : text;
        this.target = target;
        this.policy = policy == null ? CasketJoinPolicy.MERGE : policy;
        this.confirmed = confirmed;
        this.settings = settings;
    }

    public static PacketC2SPartyAction simple(Action action) {
        return new PacketC2SPartyAction(action, "", null, CasketJoinPolicy.MERGE, false, null);
    }

    public static PacketC2SPartyAction invite(String playerName, CasketJoinPolicy policy) {
        return new PacketC2SPartyAction(Action.INVITE, playerName, null, policy, false, null);
    }

    public static PacketC2SPartyAction accept(UUID partyId, CasketJoinPolicy policy, boolean confirmedOverride) {
        return new PacketC2SPartyAction(Action.ACCEPT, "", partyId, policy, confirmedOverride, null);
    }

    /**
     * Joins the party's Covenant Casket with a casket choice (for members of a sharing party who haven't yet).
     */
    public static PacketC2SPartyAction covenantJoin(CasketJoinPolicy policy, boolean confirmedOverride) {
        return new PacketC2SPartyAction(Action.COVENANT_JOIN, "", null, policy, confirmedOverride, null);
    }

    public static PacketC2SPartyAction decline(UUID partyId) {
        return new PacketC2SPartyAction(Action.DECLINE, "", partyId, CasketJoinPolicy.MERGE, false, null);
    }

    public static PacketC2SPartyAction member(Action action, UUID member) {
        return new PacketC2SPartyAction(action, "", member, CasketJoinPolicy.MERGE, false, null);
    }

    public static PacketC2SPartyAction saveSettings(String name, PartySettings settings) {
        return new PacketC2SPartyAction(Action.SAVE_SETTINGS, name, null, CasketJoinPolicy.MERGE, false, settings);
    }

    public Action getAction() {
        return this.action;
    }

    public String getText() {
        return this.text;
    }

    @Nullable
    public UUID getTarget() {
        return this.target;
    }

    public CasketJoinPolicy getPolicy() {
        return this.policy;
    }

    public boolean isConfirmed() {
        return this.confirmed;
    }

    @Nullable
    public PartySettings getSettings() {
        return this.settings;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(this.action);
        buf.writeUtf(this.text, 64);
        buf.writeNullable(this.target, FriendlyByteBuf::writeUUID);
        buf.writeEnum(this.policy);
        buf.writeBoolean(this.confirmed);
        buf.writeNullable(this.settings, (b, s) -> s.encode(b));
    }

    public static PacketC2SPartyAction decode(FriendlyByteBuf buf) {
        return new PacketC2SPartyAction(
                buf.readEnum(Action.class),
                buf.readUtf(64),
                buf.readNullable(FriendlyByteBuf::readUUID),
                buf.readEnum(CasketJoinPolicy.class),
                buf.readBoolean(),
                buf.readNullable(PartySettings::decode)
        );
    }

    public void handleServer(ServerPlayer player) {
        if (player != null) {
            PartyService.handleAction(player, this);
        }
    }
}
