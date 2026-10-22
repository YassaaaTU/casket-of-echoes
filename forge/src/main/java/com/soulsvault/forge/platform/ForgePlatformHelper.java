package com.soulsvault.forge.platform;

import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultEntry;
import com.soulsvault.forge.data.ForgeVaultSavedData;
import com.soulsvault.forge.network.ForgeNetworkHandler;
import com.soulsvault.platform.services.IPlatformHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

import javax.annotation.Nullable;

public class ForgePlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Forge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get() != null && ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public PlayerVaultData getPlayerVaultData(ServerPlayer player) {
        return ForgeVaultSavedData.get(player).getVaultData();
    }

    @Override
    public void markVaultDirty(ServerPlayer player) {
        ForgeVaultSavedData.get(player).setDirty();
    }

    @Override
    public void sendToServer(Object packet) {
        ForgeNetworkHandler.sendToServer(packet);
    }

    @Override
    public void sendToClient(ServerPlayer player, Object packet) {
        ForgeNetworkHandler.sendToClient(player, packet);
    }

    @Override
    public void syncFullVaultToClient(ServerPlayer player, PlayerVaultData vaultData) {
        ForgeNetworkHandler.syncFullVaultToClient(player, vaultData);
    }

    @Override
    public void sendVaultDelta(ServerPlayer player, ItemKey key, @Nullable VaultEntry updatedEntry) {
        ForgeNetworkHandler.sendVaultDelta(player, key, updatedEntry);
    }

    @Override
    public void sendOverflowToast(
            ServerPlayer player,
            ItemStack displayStack,
            int countSentToVault,
            boolean isReplenish
    ) {
        ForgeNetworkHandler.sendOverflowToast(player, displayStack, countSentToVault, isReplenish);
    }
}
