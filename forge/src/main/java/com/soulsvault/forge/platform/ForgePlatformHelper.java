package com.soulsvault.forge.platform;

import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultEntry;
import com.soulsvault.forge.compat.CuriosCompat;
import com.soulsvault.forge.compat.EpicFightCompat;
import com.soulsvault.forge.data.ForgeVaultSavedData;
import com.soulsvault.forge.network.ForgeNetworkHandler;
import com.soulsvault.forge.registry.ForgeModBlocks;
import com.soulsvault.platform.services.IPlatformHelper;
import com.soulsvault.world.entity.SpectralCasketEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public boolean isEpicFightWeapon(ItemStack stack) {
        return EpicFightCompat.isWeapon(stack);
    }

    @Override
    public boolean isCurioItem(ItemStack stack) {
        return CuriosCompat.isCurio(stack);
    }

    @Override
    public List<Component> getExtraInspectionStats(ItemStack stack) {
        return EpicFightCompat.getInspectionStats(stack);
    }

    @Override
    public Map<String, List<ItemKey>> captureCurioSlots(Player player) {
        return CuriosCompat.captureCurios(player);
    }

    @Override
    public void swapCurioSlots(
            ServerPlayer player,
            PlayerVaultData vaultData,
            Map<String, List<ItemKey>> desiredCurios,
            Set<ItemKey> modifiedKeys
    ) {
        CuriosCompat.swapCurios(player, vaultData, desiredCurios, modifiedKeys);
    }

    @Override
    public boolean spawnSpectralCasket(ServerLevel level, Vec3 pos, ServerPlayer owner) {
        SpectralCasketEntity entity = ForgeModBlocks.SPECTRAL_CASKET.get().create(level);
        if (entity == null) {
            return false;
        }
        entity.moveTo(pos.x, pos.y, pos.z, owner.getYRot() + 180.0F, 0.0F);
        entity.setOwnerUuid(owner.getUUID());
        return level.addFreshEntity(entity);
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
