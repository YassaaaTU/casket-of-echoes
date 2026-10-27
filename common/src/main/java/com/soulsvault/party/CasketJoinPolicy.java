package com.soulsvault.party;

import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * What happens to a joining player's private Casket when their new party shares one. The inviter proposes a
 * policy; the invitee may accept it or pick {@link #EMPTY_FIRST} / {@link #OVERRIDE} instead.
 */
public enum CasketJoinPolicy {
    /** Move everything that fits into the party Casket; what doesn't fit stays set aside in the private one. */
    MERGE,
    /** Joining is refused until the private Casket is empty. */
    EMPTY_FIRST,
    /** The private Casket is permanently deleted on join (confirmed twice in the GUI). */
    OVERRIDE;

    /**
     * Short name shown on the policy buttons.
     */
    public Component displayName() {
        return Component.translatable("gui.soulsvault.party.policy." + name().toLowerCase(Locale.ROOT));
    }

    /**
     * One-line explanation shown under the policy buttons.
     */
    public Component description() {
        return Component.translatable("gui.soulsvault.party.policy." + name().toLowerCase(Locale.ROOT) + ".desc");
    }

    /**
     * The next policy in button order, wrapping around.
     */
    public CasketJoinPolicy next() {
        CasketJoinPolicy[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
