package com.soulsvault.network;

import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultCategory;
import com.soulsvault.core.VaultEntry;
import com.soulsvault.platform.Services;
import com.soulsvault.world.inventory.SoulsVaultMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

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
        LOAD_BUILD_ECHO
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
        if (player.containerMenu instanceof SoulsVaultMenu vaultMenu && !vaultMenu.isVaultAccessAllowed()) {
            if (this.action != ActionType.CLEAR_CRAFTING_GRID) {
                return;
            }
        }

        PlayerVaultData vaultData = Services.PLATFORM.getPlayerVaultData(player);

        switch (this.action) {
            case DEPOSIT -> {
                long deposited = vaultData.depositFromPlayerInventory(player, this.key, this.amount);
                if (deposited > 0L) {
                    Services.PLATFORM.markVaultDirty(player);
                    Services.PLATFORM.sendVaultDelta(player, this.key, vaultData.getEntry(this.key));
                }
            }
            case WITHDRAW -> {
                long withdrawn = vaultData.withdrawToPlayerInventory(player, this.key, this.amount);
                if (withdrawn > 0L) {
                    Services.PLATFORM.markVaultDirty(player);
                    Services.PLATFORM.sendVaultDelta(player, this.key, vaultData.getEntry(this.key));
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
                        Services.PLATFORM.markVaultDirty(player);
                        Services.PLATFORM.sendVaultDelta(player, carriedKey, vaultData.getEntry(carriedKey));
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
                        Services.PLATFORM.markVaultDirty(player);
                        Services.PLATFORM.sendVaultDelta(player, this.key, vaultData.getEntry(this.key));
                    }
                } else if (this.key.matches(carried) && carried.getCount() < maxStack) {
                    int space = maxStack - carried.getCount();
                    int request = (int) Math.min(space, Math.max(1L, this.amount));
                    long withdrawn = vaultData.withdraw(this.key, request);
                    if (withdrawn > 0L) {
                        carried.grow((int) withdrawn);
                        player.containerMenu.setCarried(carried);
                        player.containerMenu.broadcastChanges();
                        Services.PLATFORM.markVaultDirty(player);
                        Services.PLATFORM.sendVaultDelta(player, this.key, vaultData.getEntry(this.key));
                    }
                }
            }
            case CLEAR_CRAFTING_GRID -> {
                if (player.containerMenu instanceof SoulsVaultMenu vaultMenu) {
                    vaultMenu.clearCraftingGridToVaultOrInventory(player);
                }
            }
            case DEPOSIT_ALL_CATEGORY -> {
                VaultCategory[] categories = VaultCategory.values();
                if (this.extraData >= 0 && this.extraData < categories.length) {
                    VaultCategory category = categories[this.extraData];
                    Set<ItemKey> modified = vaultData.depositAllInCategory(player, category);
                    if (!modified.isEmpty()) {
                        Services.PLATFORM.markVaultDirty(player);
                        for (ItemKey modKey : modified) {
                            Services.PLATFORM.sendVaultDelta(player, modKey, vaultData.getEntry(modKey));
                        }
                    }
                }
            }
            case TOGGLE_FAVORITE -> {
                if (!this.key.isEmpty()) {
                    VaultEntry updated = vaultData.toggleFavorite(this.key);
                    Services.PLATFORM.markVaultDirty(player);
                    Services.PLATFORM.sendVaultDelta(player, this.key, updated);
                }
            }
            case SET_REFILL_TARGET -> {
                if (!this.key.isEmpty()) {
                    boolean autoRefill = (this.extraData & FLAG_AUTO_REFILL) != 0;
                    boolean overflowDeposit = (this.extraData & FLAG_OVERFLOW_DEPOSIT) != 0;
                    int target = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, this.amount));
                    VaultEntry updated = vaultData.configureRefill(this.key, autoRefill, target, overflowDeposit);
                    Services.PLATFORM.markVaultDirty(player);
                    Services.PLATFORM.sendVaultDelta(player, this.key, updated);
                }
            }
            case REST_REPLENISH -> vaultData.performRestAndReplenish(player);
            case SAVE_BUILD_ECHO -> {
                vaultData.saveBuildEcho(player, this.extraData, "Echo " + (this.extraData + 1));
                Services.PLATFORM.markVaultDirty(player);
                Services.PLATFORM.syncFullVaultToClient(player, vaultData);
            }
            case LOAD_BUILD_ECHO -> {
                Set<ItemKey> modified = vaultData.loadBuildEcho(player, this.extraData);
                if (!modified.isEmpty()) {
                    Services.PLATFORM.markVaultDirty(player);
                    for (ItemKey modKey : modified) {
                        Services.PLATFORM.sendVaultDelta(player, modKey, vaultData.getEntry(modKey));
                    }
                }
            }
        }
    }
}
