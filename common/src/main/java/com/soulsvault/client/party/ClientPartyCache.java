package com.soulsvault.client.party;

import com.soulsvault.client.gui.SoulsVaultScreen;
import com.soulsvault.party.PartyInvite;
import com.soulsvault.party.PartySnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Client copy of the player's party (for the party HUD and party GUI), updated by {@code PacketS2CPartySync}.
 */
public final class ClientPartyCache {
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    @Nullable
    private static volatile PartySnapshot snapshot;
    private static volatile List<PartyInvite> invites = List.of();
    private static volatile long privateCasketItems;

    private ClientPartyCache() {
    }

    public static void update(@Nullable PartySnapshot newSnapshot, List<PartyInvite> newInvites, long newPrivateItems) {
        snapshot = newSnapshot;
        invites = List.copyOf(newInvites);
        privateCasketItems = newPrivateItems;
        for (Runnable listener : LISTENERS) {
            listener.run();
        }
    }

    public static void reset() {
        update(null, List.of(), 0L);
    }

    @Nullable
    public static PartySnapshot getSnapshot() {
        return snapshot;
    }

    public static List<PartyInvite> getInvites() {
        return invites;
    }

    /**
     * Items in the player's private Casket (set aside while a shared party Casket is used).
     */
    public static long getPrivateCasketItems() {
        return privateCasketItems;
    }

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    public static void openScreen(@Nullable Screen parent, boolean invitesTab) {
        Minecraft.getInstance().setScreen(new PartyScreen(parent, invitesTab));
    }

    /**
     * Opens the party GUI on the server's request, returning to the Casket afterwards if it was open.
     */
    public static void requestOpenScreen(boolean invitesTab) {
        Minecraft mc = Minecraft.getInstance();
        Screen parent = mc.screen instanceof SoulsVaultScreen ? mc.screen : null;
        openScreen(parent, invitesTab);
    }
}
