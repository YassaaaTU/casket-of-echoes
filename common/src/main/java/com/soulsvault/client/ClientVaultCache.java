package com.soulsvault.client;

import com.soulsvault.core.BuildEcho;
import com.soulsvault.core.EquipLoad;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.Keepsakes;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultEntry;
import com.soulsvault.core.VaultMeta;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Client-side in-memory vault state updated via chunked sync and single-entry delta packets.
 */
public final class ClientVaultCache {
    private static final PlayerVaultData CLIENT_VAULT = new PlayerVaultData();
    private static final Keepsakes CLIENT_KEEPSAKES = new Keepsakes();
    private static final List<Runnable> KEEPSAKE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<ChangeListener> CHANGE_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<Consumer<OverflowToastEvent>> TOAST_LISTENERS = new CopyOnWriteArrayList<>();

    private static Runnable vaultScreenOpener = () -> {
    };
    private static volatile EquipLoad.Result equipLoad = EquipLoad.Result.NONE;

    /**
     * Receives vault changes so views can update incrementally instead of rebuilding every frame.
     */
    public interface ChangeListener {
        /** Everything may have changed (a full sync arrived). */
        void onFullSync();

        /** One entry changed; {@code entry} is {@code null} when it was removed. */
        void onDelta(ItemKey key, @Nullable VaultEntry entry);
    }

    private ClientVaultCache() {
    }

    public static PlayerVaultData getVaultData() {
        return CLIENT_VAULT;
    }

    public static VaultMeta getMeta() {
        return CLIENT_VAULT.getMeta();
    }

    public static EquipLoad.Result getEquipLoad() {
        return equipLoad;
    }

    public static void setVaultScreenOpener(Runnable opener) {
        vaultScreenOpener = opener != null ? opener : () -> {
        };
    }

    /**
     * Forgets everything the last server synced (on disconnect), so the next world starts from an empty mirror.
     */
    public static void reset() {
        CLIENT_VAULT.replaceAllFromSync(List.of(), List.of(), null);
        CLIENT_KEEPSAKES.setFromSync(false, false, List.of());
        equipLoad = EquipLoad.Result.NONE;
    }

    /**
     * Starts delivering sync and delta notifications to {@code listener} (an open Casket screen).
     */
    public static void registerChangeListener(ChangeListener listener) {
        if (listener != null) {
            CHANGE_LISTENERS.add(listener);
        }
    }

    public static void unregisterChangeListener(ChangeListener listener) {
        CHANGE_LISTENERS.remove(listener);
    }

    /**
     * Receives every overflow / replenish notification (the HUD banner).
     */
    public static void registerToastListener(Consumer<OverflowToastEvent> listener) {
        if (listener != null) {
            TOAST_LISTENERS.add(listener);
        }
    }

    /**
     * Applies one chunk of a full sync: chunk 0 replaces the mirror (and refreshes an open Casket screen), later
     * chunks append entries.
     */
    public static void handleSyncChunk(
            int chunkIndex,
            int totalChunks,
            List<VaultEntry> entries,
            List<BuildEcho> echoes,
            VaultMeta meta
    ) {
        if (chunkIndex <= 0) {
            CLIENT_VAULT.replaceAllFromSync(entries, echoes, meta);
            vaultScreenOpener.run();
        } else {
            CLIENT_VAULT.appendEntriesFromSync(entries);
        }
        for (ChangeListener listener : CHANGE_LISTENERS) {
            listener.onFullSync();
        }
    }

    /**
     * Applies a single-entry update and notifies listeners with the resulting entry.
     */
    public static void handleDelta(ItemKey key, @Nullable VaultEntry updatedEntry) {
        CLIENT_VAULT.applyDelta(key, updatedEntry);
        VaultEntry current = CLIENT_VAULT.getEntry(key);
        for (ChangeListener listener : CHANGE_LISTENERS) {
            listener.onDelta(key, current);
        }
    }

    public static Keepsakes getKeepsakes() {
        return CLIENT_KEEPSAKES;
    }

    /**
     * Notified whenever the Keepsakes mirror changes.
     */
    public static void registerKeepsakeListener(Runnable listener) {
        if (listener != null) {
            KEEPSAKE_LISTENERS.add(listener);
        }
    }

    public static void unregisterKeepsakeListener(Runnable listener) {
        KEEPSAKE_LISTENERS.remove(listener);
    }

    /**
     * Replaces the Keepsakes mirror with what the server sent.
     */
    public static void handleKeepsakes(boolean unlocked, boolean upgraded, List<VaultEntry> entries) {
        CLIENT_KEEPSAKES.setFromSync(unlocked, upgraded, entries);
        for (Runnable listener : KEEPSAKE_LISTENERS) {
            listener.run();
        }
    }

    public static void handleEquipLoad(double load, double capacity) {
        equipLoad = new EquipLoad.Result(load, capacity);
    }

    /**
     * Forwards a server overflow / replenish notification to the toast listeners.
     */
    public static void handleOverflowToast(ItemStack displayStack, int count, boolean isReplenish) {
        OverflowToastEvent event = new OverflowToastEvent(displayStack.copy(), count, isReplenish, System.currentTimeMillis());
        for (Consumer<OverflowToastEvent> listener : TOAST_LISTENERS) {
            listener.accept(event);
        }
    }

    /**
     * A pickup-overflow or replenish notification for the HUD banner.
     */
    public record OverflowToastEvent(
            ItemStack displayStack,
            int count,
            boolean isReplenish,
            long timestampMillis
    ) {
    }
}
