package com.soulsvault.api;

import com.soulsvault.Constants;
import com.soulsvault.core.Attunement;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultAccess;
import com.soulsvault.core.VaultSync;
import com.soulsvault.platform.Services;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stable entry point for other mods, KubeJS ({@code Java.loadClass("com.soulsvault.api.SoulsVaultApi")}) and
 * CraftTweaker. Loader events (vault access, Rest &amp; Replenish) live in the loader module, e.g.
 * {@code com.soulsvault.forge.api.event} on Forge.
 */
public final class SoulsVaultApi {
    private static final Map<ResourceLocation, EchoSlotType> ECHO_SLOT_TYPES = new LinkedHashMap<>();

    private SoulsVaultApi() {
    }

    /**
     * Registers an extra Build Echo slot group. Call during mod construction on both sides.
     */
    public static synchronized void registerEchoSlotType(EchoSlotType type) {
        if (ECHO_SLOT_TYPES.putIfAbsent(type.id(), type) != null) {
            Constants.LOG.warn("Build Echo slot type {} is already registered", type.id());
        }
    }

    /**
     * Every registered Build Echo slot type, in registration order (read-only view).
     */
    public static synchronized Collection<EchoSlotType> getEchoSlotTypes() {
        return Collections.unmodifiableCollection(ECHO_SLOT_TYPES.values());
    }

    /**
     * The slot type registered under {@code id}, or {@code null}.
     */
    @Nullable
    public static synchronized EchoSlotType getEchoSlotType(ResourceLocation id) {
        return ECHO_SLOT_TYPES.get(id);
    }

    /**
     * The Casket the player currently uses (their party's shared one when the party shares storage).
     */
    public static PlayerVaultData getVault(ServerPlayer player) {
        return Services.PLATFORM.getPlayerVaultData(player);
    }

    /**
     * Stores {@code stack} in the player's Casket (shrinking it by what was accepted) and syncs every viewer.
     *
     * @return the number of items accepted
     */
    public static long deposit(ServerPlayer player, ItemStack stack) {
        PlayerVaultData vault = getVault(player);
        ItemKey key = ItemKey.fromStack(stack);
        long accepted = vault.deposit(key, stack.getCount());
        if (accepted > 0L) {
            stack.shrink((int) accepted);
            VaultSync.commit(player, List.of(key));
        }
        return accepted;
    }

    /**
     * Why the player may not use their Casket's storage right now (combat lock, pack rules), or {@code null}.
     */
    @Nullable
    public static Component getAccessDenialReason(ServerPlayer player) {
        return VaultAccess.check(player, VaultAccess.Type.VAULT, false);
    }

    /**
     * Whether the player may use their Casket under {@code vault.requireAttunement} (always true when it is off).
     */
    public static boolean isAttuned(ServerPlayer player) {
        return Attunement.isAttuned(player);
    }

    /**
     * Attunes the player to their Casket (with the title / sound ceremony), or seals it again with {@code false}.
     */
    public static void setAttuned(ServerPlayer player, boolean attuned) {
        Attunement.setAttuned(player, attuned, true);
    }
}
