package com.soulsvault.core;

import com.soulsvault.Constants;
import com.soulsvault.config.SoulsVaultConfig;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * Pack-controlled rules for what may enter a Casket: the {@code #soulsvault:vault_blacklist} item tag
 * (no storage inside storage) and the {@code vault.maxItemNbtBytes} size cap (book-ban protection for the
 * Vault file and sync packets).
 */
public final class VaultRules {

    private VaultRules() {
    }

    public static boolean canStore(ItemKey key) {
        return getRejectionReason(key) == null;
    }

    /**
     * Returns why {@code key} cannot be stored, or {@code null} if it can.
     */
    @Nullable
    @SuppressWarnings("deprecation")
    public static Component getRejectionReason(ItemKey key) {
        if (key == null || key.isEmpty()) {
            return Component.translatable("gui.soulsvault.notice.cannot_store");
        }
        if (key.getItem().builtInRegistryHolder().is(Constants.VAULT_BLACKLIST_TAG)) {
            return Component.translatable("gui.soulsvault.notice.blacklisted", key.toSingleStack().getHoverName());
        }
        int maxNbt = SoulsVaultConfig.vault().maxItemNbtBytes();
        if (maxNbt > 0 && key.getNbtSizeBytes() > maxNbt) {
            return Component.translatable("gui.soulsvault.notice.nbt_too_large", key.toSingleStack().getHoverName());
        }
        return null;
    }
}
