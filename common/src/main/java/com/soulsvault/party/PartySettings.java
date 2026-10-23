package com.soulsvault.party;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Options a party leader controls in the party options GUI. Each one only applies while the matching server rule
 * ({@code party.*} in the server config) is {@code LEADER_CHOICE}; {@code ALWAYS} / {@code NEVER} override it.
 *
 * @param sharedCasket        members use one shared Casket instead of private ones
 * @param preventFriendlyFire members, their pets and their mounts cannot hurt each other
 * @param shareAdvancements   an advancement earned by one online member is granted to the others
 * @param shareQuests         an FTB Quests quest completed by one online member is completed for the others
 * @param membersCanInvite    non-leaders may send invites
 */
public record PartySettings(
        boolean sharedCasket,
        boolean preventFriendlyFire,
        boolean shareAdvancements,
        boolean shareQuests,
        boolean membersCanInvite
) {
    /** Defaults for new built-in parties. */
    public static final PartySettings DEFAULT = new PartySettings(true, true, false, false, false);
    /** Defaults for parties from other mods (FTB Teams, OPAC, vanilla teams) until their leader opts in. */
    public static final PartySettings EXTERNAL_DEFAULT = new PartySettings(false, true, false, false, false);

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("SharedCasket", this.sharedCasket);
        tag.putBoolean("PreventFriendlyFire", this.preventFriendlyFire);
        tag.putBoolean("ShareAdvancements", this.shareAdvancements);
        tag.putBoolean("ShareQuests", this.shareQuests);
        tag.putBoolean("MembersCanInvite", this.membersCanInvite);
        return tag;
    }

    public static PartySettings fromTag(CompoundTag tag) {
        return new PartySettings(
                tag.getBoolean("SharedCasket"),
                tag.getBoolean("PreventFriendlyFire"),
                tag.getBoolean("ShareAdvancements"),
                tag.getBoolean("ShareQuests"),
                tag.getBoolean("MembersCanInvite")
        );
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(this.sharedCasket);
        buf.writeBoolean(this.preventFriendlyFire);
        buf.writeBoolean(this.shareAdvancements);
        buf.writeBoolean(this.shareQuests);
        buf.writeBoolean(this.membersCanInvite);
    }

    public static PartySettings decode(FriendlyByteBuf buf) {
        return new PartySettings(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean());
    }
}
