package com.soulsvault.network;

import com.soulsvault.client.ClientVaultCache;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.VaultEntry;
import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * Server-to-Client single-entry delta update packet.
 * Updates or removes a single {@link ItemKey} in the client vault cache without resending the full vault.
 */
public final class PacketS2CVaultDelta {
    private final ItemKey key;
    @Nullable
    private final VaultEntry updatedEntry;

    public PacketS2CVaultDelta(ItemKey key, @Nullable VaultEntry updatedEntry) {
        this.key = Objects.requireNonNullElse(key, ItemKey.EMPTY);
        this.updatedEntry = updatedEntry != null && updatedEntry.shouldRetain() ? updatedEntry : null;
    }

    public void encode(FriendlyByteBuf buf) {
        this.key.toNetwork(buf);
        buf.writeBoolean(this.updatedEntry != null);
        if (this.updatedEntry != null) {
            this.updatedEntry.toNetwork(buf);
        }
    }

    public static PacketS2CVaultDelta decode(FriendlyByteBuf buf) {
        ItemKey key = ItemKey.fromNetwork(buf);
        boolean hasEntry = buf.readBoolean();
        VaultEntry entry = hasEntry ? VaultEntry.fromNetwork(buf) : null;
        return new PacketS2CVaultDelta(key, entry);
    }

    public void handleClient() {
        ClientVaultCache.handleDelta(this.key, this.updatedEntry);
    }
}
