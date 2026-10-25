package com.soulsvault.network;

import com.soulsvault.core.ItemKey;
import com.soulsvault.core.KeepsakeSync;
import com.soulsvault.core.Keepsakes;
import com.soulsvault.core.Milestones;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultAccess;
import com.soulsvault.core.VaultCategory;
import com.soulsvault.core.VaultRules;
import com.soulsvault.core.VaultSync;
import com.soulsvault.platform.Services;
import com.soulsvault.world.inventory.SoulsVaultMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Client-to-Server packet for all vault mutations, cursor transfers, and crafting grid actions.
 */
public final class PacketC2SVaultAction {
    public static final int FLAG_AUTO_REFILL = 1;
    public static final int FLAG_OVERFLOW_DEPOSIT = 1 << 1;

    public enum ActionType {
        DEPOSIT,
        WITHDRAW,
        DEPOSIT_CARRIED,
        PICKUP_TO_CURSOR,
        CLEAR_CRAFTING_GRID,
        DEPOSIT_ALL_CATEGORY,
        TOGGLE_FAVORITE,
        SET_REFILL_TARGET,
        REST_REPLENISH,
        SAVE_BUILD_ECHO,
        LOAD_BUILD_ECHO,
        UNLOCK_REMOTE,
        SORT_INVENTORY,
        KEEPSAKE_DEPOSIT_CARRIED,
        KEEPSAKE_PICKUP,
        KEEPSAKE_WITHDRAW
    }

    private final ActionType action;
    private final ItemKey key;
    private final long amount;
    private final int extraData;

    public PacketC2SVaultAction(ActionType action, ItemKey key, long amount, int extraData) {
        this.action = Objects.requireNonNull(action, "action cannot be null");
        this.key = Objects.requireNonNullElse(key, ItemKey.EMPTY);
        this.amount = amount;
        this.extraData = extraData;
    }

    public static PacketC2SVaultAction deposit(ItemKey key, long amount) {
        return new PacketC2SVaultAction(ActionType.DEPOSIT, key, amount, 0);
    }

    public static PacketC2SVaultAction withdraw(ItemKey key, long amount) {
        return new PacketC2SVaultAction(ActionType.WITHDRAW, key, amount, 0);
    }

    public static PacketC2SVaultAction depositCarried(boolean singleItemOnly) {
        return new PacketC2SVaultAction(ActionType.DEPOSIT_CARRIED, ItemKey.EMPTY, singleItemOnly ? 1L : 64L, singleItemOnly ? 1 : 0);
    }

    public static PacketC2SVaultAction pickupToCursor(ItemKey key, int amount) {
        return new PacketC2SVaultAction(ActionType.PICKUP_TO_CURSOR, key, Math.max(1, amount), 0);
    }

    public static PacketC2SVaultAction clearCraftingGrid() {
        return new PacketC2SVaultAction(ActionType.CLEAR_CRAFTING_GRID, ItemKey.EMPTY, 0L, 0);
    }

    public static PacketC2SVaultAction depositAllCategory(VaultCategory category) {
        return new PacketC2SVaultAction(ActionType.DEPOSIT_ALL_CATEGORY, ItemKey.EMPTY, 0L, category.ordinal());
    }

    public static PacketC2SVaultAction toggleFavorite(ItemKey key) {
        return new PacketC2SVaultAction(ActionType.TOGGLE_FAVORITE, key, 0L, 0);
    }

    public static PacketC2SVaultAction setRefillTarget(
            ItemKey key,
            int refillTarget,
            boolean autoRefill,
            boolean overflowDeposit
    ) {
        int flags = (autoRefill ? FLAG_AUTO_REFILL : 0) | (overflowDeposit ? FLAG_OVERFLOW_DEPOSIT : 0);
        return new PacketC2SVaultAction(ActionType.SET_REFILL_TARGET, key, refillTarget, flags);
    }

    public static PacketC2SVaultAction restReplenish() {
        return new PacketC2SVaultAction(ActionType.REST_REPLENISH, ItemKey.EMPTY, 0L, 0);
    }

    public static PacketC2SVaultAction saveBuildEcho(int echoIndex) {
        return new PacketC2SVaultAction(ActionType.SAVE_BUILD_ECHO, ItemKey.EMPTY, 0L, echoIndex);
    }

    public static PacketC2SVaultAction loadBuildEcho(int echoIndex) {
        return new PacketC2SVaultAction(ActionType.LOAD_BUILD_ECHO, ItemKey.EMPTY, 0L, echoIndex);
    }

    public static PacketC2SVaultAction unlockRemoteAccess() {
        return new PacketC2SVaultAction(ActionType.UNLOCK_REMOTE, ItemKey.EMPTY, 0L, 0);
    }

    public static PacketC2SVaultAction sortInventory() {
        return new PacketC2SVaultAction(ActionType.SORT_INVENTORY, ItemKey.EMPTY, 0L, 0);
    }

    public static PacketC2SVaultAction keepsakeDepositCarried(boolean singleItemOnly) {
        return new PacketC2SVaultAction(ActionType.KEEPSAKE_DEPOSIT_CARRIED, ItemKey.EMPTY, 0L, singleItemOnly ? 1 : 0);
    }

    public static PacketC2SVaultAction keepsakePickup(ItemKey key, int amount) {
        return new PacketC2SVaultAction(ActionType.KEEPSAKE_PICKUP, key, Math.max(1, amount), 0);
    }

    public static PacketC2SVaultAction keepsakeWithdraw(ItemKey key, int amount) {
        return new PacketC2SVaultAction(ActionType.KEEPSAKE_WITHDRAW, key, Math.max(1, amount), 0);
    }

    public ActionType getAction() {
        return this.action;
    }

    public ItemKey getKey() {
        return this.key;
    }

    public long getAmount() {
        return this.amount;
    }

    public int getExtraData() {
        return this.extraData;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(this.action);
        this.key.toNetwork(buf);
        buf.writeVarLong(this.amount);
        buf.writeVarInt(this.extraData);
    }

    public static PacketC2SVaultAction decode(FriendlyByteBuf buf) {
        ActionType action = buf.readEnum(ActionType.class);
        ItemKey key = ItemKey.fromNetwork(buf);
        long amount = buf.readVarLong();
        int extraData = buf.readVarInt();
        return new PacketC2SVaultAction(action, key, amount, extraData);
    }

    public void handleServer(ServerPlayer player) {
        if (player == null || player.isSpectator()) {
            return;
        }
        // Every action originates from the Casket screen; requiring its menu prevents a modified client from
        // reaching the Vault away from a Rest Point (which would bypass allowRemoteVaultInFullInventory).
        if (!(player.containerMenu instanceof SoulsVaultMenu vaultMenu)) {
            return;
        }
        PlayerVaultData vaultData = Services.PLATFORM.getPlayerVaultData(player);

        // Actions that never move items between the player and the Vault are always allowed.
        switch (this.action) {
            case CLEAR_CRAFTING_GRID -> {
                vaultMenu.clearCraftingGridToVaultOrInventory(player);
                return;
            }
            case TOGGLE_FAVORITE -> {
                if (!this.key.isEmpty()) {
                    vaultData.toggleFavorite(this.key);
                    VaultSync.commitPrefs(player, this.key);
                }
                return;
            }
            case SET_REFILL_TARGET -> {
                if (!this.key.isEmpty()) {
                    boolean autoRefill = (this.extraData & FLAG_AUTO_REFILL) != 0;
                    boolean overflowDeposit = (this.extraData & FLAG_OVERFLOW_DEPOSIT) != 0;
                    int target = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, this.amount));
                    vaultData.configureRefill(this.key, autoRefill, target, overflowDeposit);
                    VaultSync.commitPrefs(player, this.key);
                    if (autoRefill || overflowDeposit) {
                        Milestones.award(player, Milestones.REFILL_PINNED);
                    }
                }
                return;
            }
            case SAVE_BUILD_ECHO -> {
                vaultData.saveBuildEcho(player, this.extraData);
                Services.PLATFORM.syncFullVaultToClient(player, vaultData);
                return;
            }
            case UNLOCK_REMOTE -> {
                vaultMenu.tryUnlockRemoteAccess(player);
                return;
            }
            case SORT_INVENTORY -> {
                Services.PLATFORM.sortPlayerMainInventory(player);
                PlayerVaultData.syncPlayerInventory(player);
                return;
            }
            default -> {
            }
        }

        Component denied = vaultMenu.getVaultDenialReason(player);
        if (denied == null && this.action == ActionType.REST_REPLENISH) {
            denied = VaultAccess.check(player, VaultAccess.Type.REST, vaultMenu.getGrants().replenish());
        } else if (denied == null && this.action == ActionType.LOAD_BUILD_ECHO) {
            denied = VaultAccess.check(player, VaultAccess.Type.ECHO_SWAP, vaultMenu.getGrants().echoSwap());
        }
        if (denied != null) {
            player.displayClientMessage(denied.copy().withStyle(ChatFormatting.RED), true);
            return;
        }

        switch (this.action) {
            case DEPOSIT -> {
                if (vaultData.depositFromPlayerInventory(player, this.key, this.amount) > 0L) {
                    VaultSync.commit(player, List.of(this.key));
                } else {
                    reportRejected(player, this.key);
                }
            }
            case WITHDRAW -> {
                if (vaultData.withdrawToPlayerInventory(player, this.key, this.amount) > 0L) {
                    VaultSync.commit(player, List.of(this.key));
                }
            }
            case DEPOSIT_CARRIED -> {
                ItemStack carried = player.containerMenu.getCarried();
                if (!carried.isEmpty()) {
                    ItemKey carriedKey = ItemKey.fromStack(carried);
                    int toDeposit = this.extraData == 1 ? 1 : carried.getCount();
                    long accepted = vaultData.deposit(carriedKey, toDeposit);
                    if (accepted > 0L) {
                        carried.shrink((int) accepted);
                        player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                        player.containerMenu.broadcastChanges();
                        VaultSync.commit(player, List.of(carriedKey));
                    } else {
                        reportRejected(player, carriedKey);
                    }
                }
            }
            case PICKUP_TO_CURSOR -> {
                if (this.key.isEmpty()) {
                    return;
                }
                ItemStack carried = player.containerMenu.getCarried();
                int maxStack = Math.max(1, this.key.getItem().getMaxStackSize());
                if (carried.isEmpty()) {
                    int request = (int) Math.min(maxStack, Math.max(1L, this.amount));
                    long withdrawn = vaultData.withdraw(this.key, request);
                    if (withdrawn > 0L) {
                        player.containerMenu.setCarried(this.key.toStack((int) withdrawn));
                        player.containerMenu.broadcastChanges();
                        VaultSync.commit(player, List.of(this.key));
                    }
                } else if (this.key.matches(carried) && carried.getCount() < maxStack) {
                    int space = maxStack - carried.getCount();
                    int request = (int) Math.min(space, Math.max(1L, this.amount));
                    long withdrawn = vaultData.withdraw(this.key, request);
                    if (withdrawn > 0L) {
                        carried.grow((int) withdrawn);
                        player.containerMenu.setCarried(carried);
                        player.containerMenu.broadcastChanges();
                        VaultSync.commit(player, List.of(this.key));
                    }
                }
            }
            case DEPOSIT_ALL_CATEGORY -> {
                VaultCategory[] categories = VaultCategory.values();
                if (this.extraData >= 0 && this.extraData < categories.length) {
                    Set<ItemKey> modified = vaultData.depositAllInCategory(player, categories[this.extraData]);
                    VaultSync.commit(player, modified);
                }
            }
            case REST_REPLENISH -> vaultData.performRestAndReplenish(player);
            case KEEPSAKE_DEPOSIT_CARRIED, KEEPSAKE_PICKUP, KEEPSAKE_WITHDRAW -> handleKeepsake(player, vaultData.getKeepsakes());
            case LOAD_BUILD_ECHO -> {
                VaultSync.commit(player, vaultData.loadBuildEcho(player, this.extraData));
                Milestones.award(player, Milestones.ECHO_SWAPPED);
            }
            default -> {
            }
        }
    }

    /**
     * Keepsakes actions; the Vault's access rules were already checked by the caller.
     */
    private void handleKeepsake(ServerPlayer player, Keepsakes keepsakes) {
        if (!keepsakes.isUnlocked()) {
            player.displayClientMessage(Component.translatable("gui.soulsvault.keepsakes.locked").withStyle(ChatFormatting.RED), true);
            return;
        }
        ItemStack carried = player.containerMenu.getCarried();
        switch (this.action) {
            case KEEPSAKE_DEPOSIT_CARRIED -> {
                if (carried.isEmpty()) {
                    return;
                }
                ItemKey carriedKey = ItemKey.fromStack(carried);
                long accepted = keepsakes.deposit(carriedKey, this.extraData == 1 ? 1 : carried.getCount());
                if (accepted <= 0L) {
                    Component reason = VaultRules.getRejectionReason(carriedKey);
                    player.displayClientMessage((reason != null ? reason : Component.translatable("gui.soulsvault.keepsakes.full"))
                            .copy().withStyle(ChatFormatting.RED), true);
                    return;
                }
                carried.shrink((int) accepted);
                player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                player.containerMenu.broadcastChanges();
            }
            case KEEPSAKE_PICKUP -> {
                if (this.key.isEmpty() || (!carried.isEmpty() && !this.key.matches(carried))) {
                    return;
                }
                int maxStack = Math.max(1, this.key.getItem().getMaxStackSize());
                int space = maxStack - carried.getCount();
                long withdrawn = keepsakes.withdraw(this.key, Math.min(space, Math.max(1L, this.amount)));
                if (withdrawn <= 0L) {
                    return;
                }
                if (carried.isEmpty()) {
                    player.containerMenu.setCarried(this.key.toStack((int) withdrawn));
                } else {
                    carried.grow((int) withdrawn);
                    player.containerMenu.setCarried(carried);
                }
                player.containerMenu.broadcastChanges();
            }
            case KEEPSAKE_WITHDRAW -> {
                int wanted = (int) Math.min(Integer.MAX_VALUE, Math.min(keepsakes.getCount(this.key), this.amount));
                int inserted = PlayerVaultData.insertIntoInventory(player.getInventory(), this.key, wanted);
                if (inserted <= 0) {
                    return;
                }
                keepsakes.withdraw(this.key, inserted);
                PlayerVaultData.syncPlayerInventory(player);
            }
            default -> {
                return;
            }
        }
        KeepsakeSync.send(player);
    }

    private static void reportRejected(ServerPlayer player, ItemKey key) {
        Component reason = VaultRules.getRejectionReason(key);
        if (reason != null) {
            player.displayClientMessage(reason.copy().withStyle(ChatFormatting.RED), true);
        }
    }
}
