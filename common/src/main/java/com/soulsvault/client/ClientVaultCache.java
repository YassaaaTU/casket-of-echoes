package com.soulsvault.client;

import com.soulsvault.core.BuildEcho;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultEntry;
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
    private static final List<Runnable> SYNC_LISTENERS = new CopyOnWriteArrayList<>();
    private static final List<Consumer<OverflowToastEvent>> TOAST_LISTENERS = new CopyOnWriteArrayList<>();

    private static Runnable vaultScreenOpener = () -> {
    };

    private ClientVaultCache() {
    }

    public static PlayerVaultData getVaultData() {
        return CLIENT_VAULT;
    }

    public static void setVaultScreenOpener(Runnable opener) {
        vaultScreenOpener = opener != null ? opener : () -> {
        };
    }

    public static void registerSyncListener(Runnable listener) {
        if (listener != null) {
            SYNC_LISTENERS.add(listener);
        }
    }

    public static void unregisterSyncListener(Runnable listener) {
        SYNC_LISTENERS.remove(listener);
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
            List<BuildEcho> echoes
    ) {
        if (chunkIndex <= 0) {
            CLIENT_VAULT.replaceAllFromSync(entries, echoes);
            vaultScreenOpener.run();
        } else {
            CLIENT_VAULT.appendEntriesFromSync(entries);
        }
        notifySyncListeners();
    }

    public static void handleDelta(ItemKey key, @Nullable VaultEntry updatedEntry) {
        CLIENT_VAULT.applyDelta(key, updatedEntry);
        notifySyncListeners();
    }

    public static void handleOverflowToast(ItemStack displayStack, int count, boolean isReplenish) {
        OverflowToastEvent event = new OverflowToastEvent(displayStack.copy(), count, isReplenish, System.currentTimeMillis());
        for (Consumer<OverflowToastEvent> listener : TOAST_LISTENERS) {
            listener.accept(event);
        }
    }

    private static void notifySyncListeners() {
        for (Runnable listener : SYNC_LISTENERS) {
            listener.run();
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
