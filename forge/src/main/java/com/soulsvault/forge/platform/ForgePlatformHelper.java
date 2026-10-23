package com.soulsvault.forge.platform;

import com.soulsvault.core.AccessorySlotAccess;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultEntry;
import com.soulsvault.forge.compat.CosmeticArmorCompat;
import com.soulsvault.forge.compat.CuriosCompat;
import com.soulsvault.forge.compat.EpicFightCompat;
import com.soulsvault.forge.compat.FtbQuestsCompat;
import com.soulsvault.forge.compat.WaystonesCompat;
import com.soulsvault.core.VaultAccess;
import com.soulsvault.core.VaultStorage;
import com.soulsvault.core.VaultSync;
import com.soulsvault.core.rest.RestPointProfile;
import com.soulsvault.forge.api.event.RestReplenishEvent;
import com.soulsvault.forge.api.event.VaultAccessEvent;
import com.soulsvault.forge.data.ForgePartySavedData;
import com.soulsvault.forge.data.ForgeSharedVaultSavedData;
import com.soulsvault.forge.data.ForgeVaultSavedData;
import com.soulsvault.forge.registry.ForgeModAttributes;
import com.soulsvault.network.PacketS2CEquipLoad;
import com.soulsvault.party.PartyRegistry;
import com.soulsvault.party.PartyService;
import com.soulsvault.forge.network.ForgeNetworkHandler;
import com.soulsvault.forge.registry.ForgeModBlocks;
import com.soulsvault.platform.services.IPlatformHelper;
import com.soulsvault.world.entity.SpectralCasketEntity;
import com.soulsvault.world.inventory.SoulsVaultMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    public List<AccessorySlotAccess> getAccessorySlots(Player player, boolean cosmetic) {
        return CuriosCompat.getAccessorySlots(player, cosmetic);
    }

    @Override
    public List<AccessorySlotAccess> getCosmeticArmorSlots(Player player) {
        return CosmeticArmorCompat.getCosmeticArmorSlots(player);
    }

    @Override
    public boolean triggerWaystonesButton() {
        return WaystonesCompat.triggerInventoryWaystoneAction();
    }

    @Override
    public MenuType<SoulsVaultMenu> getVaultMenuType() {
        return ForgeModBlocks.VAULT_MENU.get();
    }

    @Override
    public void openVaultMenu(ServerPlayer player, RestPointProfile.Grants grants) {
        PlayerVaultData vaultData = getPlayerVaultData(player);
        syncFullVaultToClient(player, vaultData);
        NetworkHooks.openScreen(
                player,
                new SimpleMenuProvider(
                        (containerId, playerInventory, p) -> new SoulsVaultMenu(containerId, playerInventory, grants),
                        Component.translatable("gui.soulsvault.title")
                ),
                grants::encode
        );
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
    public PlayerVaultData getPlayerVaultData(MinecraftServer server, UUID playerId) {
        PlayerVaultData vault = ForgeVaultSavedData.get(server, playerId).getVaultData();
        String sharedId = PartyService.sharedStorageId(server, playerId);
        vault.bindStorage(sharedId != null ? ForgeSharedVaultSavedData.get(server, sharedId).getStorage() : null);
        VaultSync.recordBinding(playerId, vault.getStorage().getId());
        return vault;
    }

    @Override
    public VaultStorage getSharedStorage(MinecraftServer server, String storageId) {
        return ForgeSharedVaultSavedData.get(server, storageId).getStorage();
    }

    @Override
    public PartyRegistry getPartyRegistry(MinecraftServer server) {
        return ForgePartySavedData.get(server).getRegistry();
    }

    @Override
    @Nullable
    public Component postVaultAccessEvent(ServerPlayer player, VaultAccess.Type type, boolean atRestPoint) {
        VaultAccessEvent event = new VaultAccessEvent(player, type, atRestPoint);
        if (!MinecraftForge.EVENT_BUS.post(event)) {
            return null;
        }
        return event.getDenyReason() != null ? event.getDenyReason() : Component.translatable("gui.soulsvault.notice.denied");
    }

    @Override
    public boolean postRestReplenishPre(ServerPlayer player) {
        return MinecraftForge.EVENT_BUS.post(new RestReplenishEvent.Pre(player));
    }

    @Override
    public int postReplenishRefill(ServerPlayer player, ItemKey key, int amount) {
        RestReplenishEvent.Refill event = new RestReplenishEvent.Refill(player, key, amount);
        MinecraftForge.EVENT_BUS.post(event);
        return event.getAmount();
    }

    @Override
    public void postRestReplenishPost(ServerPlayer player, int sweptToVault, int refilledToPlayer) {
        MinecraftForge.EVENT_BUS.post(new RestReplenishEvent.Post(player, sweptToVault, refilledToPlayer));
    }

    @Override
    public double getEpicFightWeight(ItemStack stack, EquipmentSlot slot) {
        return EpicFightCompat.getWeight(stack, slot);
    }

    @Override
    public double getEquipLoadBonus(ServerPlayer player) {
        AttributeInstance bonus = player.getAttribute(ForgeModAttributes.EQUIP_LOAD_BONUS.get());
        return bonus != null ? bonus.getValue() : 0.0D;
    }

    @Override
    public void syncQuestProgress(List<ServerPlayer> players) {
        FtbQuestsCompat.syncMembers(players);
    }

    @Override
    public void sendEquipLoad(ServerPlayer player, double load, double capacity) {
        ForgeNetworkHandler.sendToClient(player, new PacketS2CEquipLoad(load, capacity));
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
