package com.soulsvault.client;

import com.soulsvault.core.BuildEcho;
import com.soulsvault.core.EquipLoad;
import com.soulsvault.core.ItemKey;
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

    public static void registerChangeListener(ChangeListener listener) {
        if (listener != null) {
            CHANGE_LISTENERS.add(listener);
        }
    }

    public static void unregisterChangeListener(ChangeListener listener) {
        CHANGE_LISTENERS.remove(listener);
    }

    public static void registerToastListener(Consumer<OverflowToastEvent> listener) {
        if (listener != null) {
            TOAST_LISTENERS.add(listener);
        }
    }

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

    public static void handleDelta(ItemKey key, @Nullable VaultEntry updatedEntry) {
        CLIENT_VAULT.applyDelta(key, updatedEntry);
        VaultEntry current = CLIENT_VAULT.getEntry(key);
        for (ChangeListener listener : CHANGE_LISTENERS) {
            listener.onDelta(key, current);
        }
    }

    public static void handleEquipLoad(double load, double capacity) {
        equipLoad = new EquipLoad.Result(load, capacity);
    }

    public static void handleOverflowToast(ItemStack displayStack, int count, boolean isReplenish) {
        OverflowToastEvent event = new OverflowToastEvent(displayStack.copy(), count, isReplenish, System.currentTimeMillis());
        for (Consumer<OverflowToastEvent> listener : TOAST_LISTENERS) {
            listener.accept(event);
        }
    }

    public record OverflowToastEvent(
            ItemStack displayStack,
            int count,
            boolean isReplenish,
            long timestampMillis
    ) {
    }
}
