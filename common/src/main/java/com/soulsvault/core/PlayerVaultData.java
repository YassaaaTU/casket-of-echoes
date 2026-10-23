package com.soulsvault.core;

import com.soulsvault.Constants;
import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.platform.Services;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure Java vault state and transaction logic for a single player's Casket of Echoes.
 * <p>
 * Contains zero platform-specific imports; persistence and networking are routed
 * through {@link Services#PLATFORM}.
 */
public class PlayerVaultData {
    private static final String NBT_ENTRIES = "Entries";
    private static final String NBT_BUILD_ECHOES = "BuildEchoes";

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private final Map<ItemKey, VaultEntry> entries = new LinkedHashMap<>();
    private final List<BuildEcho> buildEchoes = new ArrayList<>();

    public Collection<VaultEntry> getEntries() {
        return Collections.unmodifiableCollection(this.entries.values());
    }

    public List<VaultEntry> getEntriesByCategory(VaultCategory category) {
        List<VaultEntry> result = new ArrayList<>();
        for (VaultEntry entry : this.entries.values()) {
            if (category.matches(entry.getCategory()) && entry.shouldRetain()) {
                result.add(entry);
            }
        }
        return result;
    }

    @Nullable
    public VaultEntry getEntry(ItemKey key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        return this.entries.get(key);
    }

    public long getCount(ItemKey key) {
        VaultEntry entry = getEntry(key);
        return entry != null ? entry.getCount() : 0L;
    }

    public List<BuildEcho> getBuildEchoes() {
        return Collections.unmodifiableList(this.buildEchoes);
    }

    /**
     * Per-{@link ItemKey} storage cap, driven by the server config ({@code maxStackCapacity}).
     */
    public long getMaxStackCapacity() {
        return SoulsVaultConfig.getMaxStackCapacity();
    }

    /**
     * Whether full-inventory pickups overflow into the Vault, driven by the server config
     * ({@code overflowOnFullInventory}).
     */
    public boolean isOverflowOnFullInventory() {
        return SoulsVaultConfig.isOverflowOnFullInventory();
    }

    /**
     * Deposits up to {@code amount} of {@code key} into the vault, respecting {@link #getMaxStackCapacity()}.
     *
     * @return the number of items actually accepted into the vault
     */
    public long deposit(ItemKey key, long amount) {
        if (key == null || key.isEmpty() || amount <= 0L) {
            return 0L;
        }
        VaultEntry entry = this.entries.get(key);
        long current = entry != null ? entry.getCount() : 0L;
        long availableSpace = Math.max(0L, getMaxStackCapacity() - current);
        long accepted = Math.min(amount, availableSpace);
        if (accepted <= 0L) {
            return 0L;
        }
        if (entry == null) {
            entry = new VaultEntry(key, accepted);
            this.entries.put(key, entry);
        } else {
            entry.addCount(accepted);
        }
        return accepted;
    }

    /**
     * Withdraws up to {@code requestedAmount} of {@code key} from the vault.
     *
     * @return the number of items actually withdrawn
     */
    public long withdraw(ItemKey key, long requestedAmount) {
        if (key == null || key.isEmpty() || requestedAmount <= 0L) {
            return 0L;
        }
        VaultEntry entry = this.entries.get(key);
        if (entry == null || entry.getCount() <= 0L) {
            return 0L;
        }
        long withdrawn = Math.min(entry.getCount(), requestedAmount);
        entry.addCount(-withdrawn);
        if (!entry.shouldRetain()) {
            this.entries.remove(key);
        }
        return withdrawn;
    }

    /**
     * Deposits up to {@code requestedAmount} matching {@code key} from the player's 36 main inventory slots.
     *
     * @return total items moved from the player's inventory into the vault
     */
    public long depositFromPlayerInventory(Player player, ItemKey key, long requestedAmount) {
        if (key == null || key.isEmpty() || requestedAmount <= 0L) {
            return 0L;
        }
        Inventory inv = player.getInventory();
        long remainingToDeposit = requestedAmount;
        long totalDeposited = 0L;

        for (int i = 0; i < inv.items.size() && remainingToDeposit > 0L; i++) {
            ItemStack slotStack = inv.items.get(i);
            if (slotStack.isEmpty() || !key.matches(slotStack)) {
                continue;
            }
            int toTake = (int) Math.min(slotStack.getCount(), remainingToDeposit);
            long accepted = deposit(key, toTake);
            if (accepted > 0L) {
                slotStack.shrink((int) accepted);
                if (slotStack.isEmpty()) {
                    inv.items.set(i, ItemStack.EMPTY);
                }
                remainingToDeposit -= accepted;
                totalDeposited += accepted;
            }
            if (accepted < toTake) {
                // Vault entry reached max capacity
                break;
            }
        }

        if (totalDeposited > 0L) {
            syncPlayerInventory(player);
        }
        return totalDeposited;
    }

    /**
     * Withdraws up to {@code requestedAmount} of {@code key} from the vault into the player's main inventory,
     * respecting {@link net.minecraft.world.item.Item#getMaxStackSize()} and available inventory space.
     *
     * @return total items inserted into the player's inventory
     */
    public long withdrawToPlayerInventory(Player player, ItemKey key, long requestedAmount) {
        if (key == null || key.isEmpty() || requestedAmount <= 0L) {
            return 0L;
        }
        VaultEntry entry = this.entries.get(key);
        if (entry == null || entry.getCount() <= 0L) {
            return 0L;
        }
        int maxPossible = (int) Math.min(Integer.MAX_VALUE, Math.min(entry.getCount(), requestedAmount));
        int inserted = insertIntoInventory(player.getInventory(), key, maxPossible);
        if (inserted > 0) {
            withdraw(key, inserted);
            syncPlayerInventory(player);
        }
        return inserted;
    }

    /**
     * Deposits all matching items of the given {@link VaultCategory} from the player's main inventory
     * into the vault (skipping items locked as favorites or within their configured autoRefill target).
     *
     * @return set of {@link ItemKey}s that were modified in the vault
     */
    public Set<ItemKey> depositAllInCategory(Player player, VaultCategory category) {
        Set<ItemKey> modifiedKeys = new LinkedHashSet<>();
        Inventory inv = player.getInventory();

        // First count carried quantities for keys with autoRefill so we do not dump below their refillTarget
        Map<ItemKey, Integer> carriedCounts = new LinkedHashMap<>();
        for (ItemStack stack : inv.items) {
            if (!stack.isEmpty()) {
                ItemKey key = ItemKey.fromStack(stack);
                carriedCounts.merge(key, stack.getCount(), Integer::sum);
            }
        }

        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack slotStack = inv.items.get(i);
            if (slotStack.isEmpty()) {
                continue;
            }
            ItemKey key = ItemKey.fromStack(slotStack);
            if (!category.matches(key)) {
                continue;
            }

            VaultEntry existing = this.entries.get(key);
            if (category == VaultCategory.ALL && existing != null && existing.isFavorite()) {
                continue;
            }
            int allowedToDeposit = slotStack.getCount();
            if (existing != null && existing.isAutoRefill()) {
                int currentTotal = carriedCounts.getOrDefault(key, 0);
                int excess = Math.max(0, currentTotal - existing.getRefillTarget());
                allowedToDeposit = Math.min(slotStack.getCount(), excess);
            }

            if (allowedToDeposit <= 0) {
                continue;
            }

            long accepted = deposit(key, allowedToDeposit);
            if (accepted > 0L) {
                slotStack.shrink((int) accepted);
                if (slotStack.isEmpty()) {
                    inv.items.set(i, ItemStack.EMPTY);
                }
                carriedCounts.merge(key, -(int) accepted, Integer::sum);
                modifiedKeys.add(key);
            }
        }

        if (!modifiedKeys.isEmpty()) {
            syncPlayerInventory(player);
        }
        return modifiedKeys;
    }

    @Nullable
    public VaultEntry toggleFavorite(ItemKey key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        VaultEntry entry = this.entries.computeIfAbsent(key, k -> new VaultEntry(k, 0L));
        entry.setFavorite(!entry.isFavorite());
        if (!entry.shouldRetain()) {
            this.entries.remove(key);
            return null;
        }
        return entry;
    }

    @Nullable
    public VaultEntry configureRefill(ItemKey key, boolean autoRefill, int refillTarget, boolean overflowDeposit) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        VaultEntry entry = this.entries.computeIfAbsent(key, k -> new VaultEntry(k, 0L));
        entry.setAutoRefill(autoRefill);
        entry.setRefillTarget(Math.max(1, refillTarget));
        entry.setOverflowDeposit(overflowDeposit);
        if (!entry.shouldRetain()) {
            this.entries.remove(key);
            return null;
        }
        return entry;
    }

    /**
     * Executes the 3-step Rest & Replenish transaction specified in Section 4.4 of GEMINI.md:
     * <ol>
     *   <li>Auto-Sweep Overflow from the player's 36 main inventory slots into the Vault.</li>
     *   <li>Auto-Refill Pinned Items ({@code autoRefill == true}) from the Vault up to {@code refillTarget}.</li>
     *   <li>Mark SavedData dirty, dispatch delta & toast packets, and play a muted Souls chime.</li>
     * </ol>
     */
    public RestReplenishResult performRestAndReplenish(ServerPlayer player) {
        Inventory inv = player.getInventory();
        Set<ItemKey> changedKeys = new LinkedHashSet<>();
        ItemStack lastSweptStack = ItemStack.EMPTY;
        int totalSweptToVault = 0;
        ItemStack lastRefilledStack = ItemStack.EMPTY;
        int totalRefilledToPlayer = 0;

        // Step 1 — Auto-Sweep Overflow:
        // Count total carried quantities across the 36 main inventory slots
        Map<ItemKey, Integer> carriedByKey = new LinkedHashMap<>();
        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack stack = inv.items.get(i);
            if (!stack.isEmpty()) {
                ItemKey key = ItemKey.fromStack(stack);
                carriedByKey.merge(key, stack.getCount(), Integer::sum);
            }
        }

        for (int i = 0; i < inv.items.size(); i++) {
            ItemStack slotStack = inv.items.get(i);
            if (slotStack.isEmpty()) {
                continue;
            }
            ItemKey key = ItemKey.fromStack(slotStack);
            VaultEntry entry = this.entries.get(key);
            if (entry == null || !entry.isOverflowDeposit()) {
                continue;
            }
            int carried = carriedByKey.getOrDefault(key, 0);
            int excess = carried - entry.getRefillTarget();
            if (excess <= 0) {
                continue;
            }
            int toMoveFromSlot = Math.min(slotStack.getCount(), excess);
            long accepted = deposit(key, toMoveFromSlot);
            if (accepted > 0L) {
                lastSweptStack = key.toSingleStack();
                slotStack.shrink((int) accepted);
                if (slotStack.isEmpty()) {
                    inv.items.set(i, ItemStack.EMPTY);
                }
                carriedByKey.put(key, carried - (int) accepted);
                totalSweptToVault += (int) accepted;
                changedKeys.add(key);
            }
        }

        // Step 2 — Auto-Refill Pinned Items:
        List<VaultEntry> snapshot = new ArrayList<>(this.entries.values());
        for (VaultEntry entry : snapshot) {
            if (!entry.isAutoRefill() || entry.getCount() <= 0L) {
                continue;
            }
            ItemKey key = entry.getKey();
            int currentCount = countInInventory(inv, key);
            if (currentCount < entry.getRefillTarget()) {
                int needed = entry.getRefillTarget() - currentCount;
                int toWithdraw = (int) Math.min(entry.getCount(), needed);
                if (toWithdraw > 0) {
                    int inserted = insertIntoInventory(inv, key, toWithdraw);
                    if (inserted > 0) {
                        withdraw(key, inserted);
                        lastRefilledStack = key.toSingleStack();
                        totalRefilledToPlayer += inserted;
                        changedKeys.add(key);
                    }
                }
            }
        }

        // Step 3 — Feedback:
        if (!changedKeys.isEmpty()) {
            syncPlayerInventory(player);
            Services.PLATFORM.markVaultDirty(player);
            for (ItemKey changedKey : changedKeys) {
                Services.PLATFORM.sendVaultDelta(player, changedKey, this.entries.get(changedKey));
            }
            if (totalRefilledToPlayer > 0 && !lastRefilledStack.isEmpty()) {
                Services.PLATFORM.sendOverflowToast(player, lastRefilledStack, totalRefilledToPlayer, true);
            } else if (totalSweptToVault > 0 && !lastSweptStack.isEmpty()) {
                Services.PLATFORM.sendOverflowToast(player, lastSweptStack, totalSweptToVault, false);
            }
            player.level().playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS,
                    0.65F,
                    0.85F
            );
        }

        return new RestReplenishResult(totalSweptToVault, totalRefilledToPlayer, changedKeys);
    }

    /**
     * Saves the player's currently equipped Mainhand, Offhand, 4 Armor slots, and active Curios slots
     * into a BuildEcho slot.
     */
    public BuildEcho saveBuildEcho(Player player, int index, String name) {
        int clampedIndex = Math.max(0, Math.min(Constants.MAX_BUILD_ECHOES - 1, index));
        ItemKey mainHand = ItemKey.fromStack(player.getItemBySlot(EquipmentSlot.MAINHAND));
        ItemKey offHand = ItemKey.fromStack(player.getItemBySlot(EquipmentSlot.OFFHAND));
        EnumMap<EquipmentSlot, ItemKey> armor = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            armor.put(slot, ItemKey.fromStack(player.getItemBySlot(slot)));
        }
        Map<String, List<ItemKey>> curios = Services.PLATFORM.captureCurioSlots(player);

        BuildEcho echo = new BuildEcho(clampedIndex, name, mainHand, offHand, armor, curios);
        while (this.buildEchoes.size() <= clampedIndex) {
            int fillIdx = this.buildEchoes.size();
            this.buildEchoes.add(new BuildEcho(
                    fillIdx,
                    "Echo " + (fillIdx + 1),
                    ItemKey.EMPTY,
                    ItemKey.EMPTY,
                    Collections.emptyMap(),
                    Collections.emptyMap()
            ));
        }
        this.buildEchoes.set(clampedIndex, echo);
        return echo;
    }

    /**
     * Atomically swaps the player's worn equipment and Curios with the target BuildEcho using the Vault.
     */
    public Set<ItemKey> loadBuildEcho(ServerPlayer player, int index) {
        if (index < 0 || index >= this.buildEchoes.size()) {
            return Collections.emptySet();
        }
        BuildEcho echo = this.buildEchoes.get(index);
        Set<ItemKey> modifiedKeys = new LinkedHashSet<>();

        swapEquipmentSlot(player, EquipmentSlot.MAINHAND, echo.getMainHand(), modifiedKeys);
        swapEquipmentSlot(player, EquipmentSlot.OFFHAND, echo.getOffHand(), modifiedKeys);
        for (EquipmentSlot armorSlot : ARMOR_SLOTS) {
            swapEquipmentSlot(player, armorSlot, echo.getArmor(armorSlot), modifiedKeys);
        }
        Services.PLATFORM.swapCurioSlots(player, this, echo.getCurioSlots(), modifiedKeys);

        if (!modifiedKeys.isEmpty()) {
            syncPlayerInventory(player);
            player.level().playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.ARMOR_EQUIP_NETHERITE,
                    SoundSource.PLAYERS,
                    0.75F,
                    0.95F
            );
        }
        return modifiedKeys;
    }

    private void swapEquipmentSlot(
            ServerPlayer player,
            EquipmentSlot slot,
            ItemKey desiredKey,
            Set<ItemKey> modifiedKeys
    ) {
        ItemStack currentStack = player.getItemBySlot(slot);
        ItemKey currentKey = ItemKey.fromStack(currentStack);
        if (currentKey.equals(desiredKey)) {
            return;
        }

        // Only swap if desiredKey is either EMPTY (unequip to vault) or available in the vault
        if (!desiredKey.isEmpty() && getCount(desiredKey) <= 0L) {
            return;
        }

        if (!currentStack.isEmpty()) {
            long accepted = deposit(currentKey, currentStack.getCount());
            if (accepted < currentStack.getCount()) {
                return;
            }
            modifiedKeys.add(currentKey);
            player.setItemSlot(slot, ItemStack.EMPTY);
        }

        if (!desiredKey.isEmpty()) {
            long withdrawn = withdraw(desiredKey, 1L);
            if (withdrawn > 0L) {
                player.setItemSlot(slot, desiredKey.toSingleStack());
                modifiedKeys.add(desiredKey);
            }
        }
    }

    public static void syncPlayerInventory(Player player) {
        player.getInventory().setChanged();
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.inventoryMenu.broadcastChanges();
            if (serverPlayer.containerMenu != serverPlayer.inventoryMenu) {
                serverPlayer.containerMenu.broadcastChanges();
            }
        }
    }

    public static int countInInventory(Inventory inv, ItemKey key) {
        int total = 0;
        for (ItemStack stack : inv.items) {
            if (!stack.isEmpty() && key.matches(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * Calculates how many items of {@code key} (up to {@code maxAmount}) can fit into the player's
     * 36 main inventory slots without mutating the inventory.
     */
    public static int getInsertableCapacity(Inventory inv, ItemKey key, int maxAmount) {
        if (key == null || key.isEmpty() || maxAmount <= 0) {
            return 0;
        }
        int maxStackSize = Math.max(1, key.getItem().getMaxStackSize());
        int capacity = 0;

        for (int i = 0; i < inv.items.size() && capacity < maxAmount; i++) {
            ItemStack existing = inv.items.get(i);
            if (existing.isEmpty()) {
                capacity += maxStackSize;
            } else if (key.matches(existing) && existing.getCount() < maxStackSize) {
                capacity += (maxStackSize - existing.getCount());
            }
        }

        return Math.min(maxAmount, capacity);
    }

    /**
     * Handles world item pickup overflow (EntityItemPickupEvent):
     * <ul>
     *   <li>If the item's {@link VaultEntry} has {@code overflowDeposit == true} and the pickup would exceed
     *       {@code refillTarget}, only tops up the player's inventory to {@code refillTarget} and routes the
     *       remainder directly into the Vault.</li>
     *   <li>If the player's inventory cannot fit the full stack and {@link #isOverflowOnFullInventory()} is enabled,
     *       inserts what fits into the player's inventory and routes the remainder into the Vault.</li>
     * </ul>
     * Dispatches {@link com.soulsvault.network.PacketS2CVaultDelta} and
     * {@link com.soulsvault.network.PacketS2COverflowToast} when items are routed to the Vault.
     */
    public PickupOverflowResult handleWorldItemPickup(ServerPlayer player, ItemStack entityStack) {
        if (player == null || entityStack == null || entityStack.isEmpty()) {
            return PickupOverflowResult.NONE;
        }

        ItemKey key = ItemKey.fromStack(entityStack);
        if (key.isEmpty()) {
            return PickupOverflowResult.NONE;
        }

        Inventory inv = player.getInventory();
        VaultEntry entry = this.entries.get(key);
        int stackCount = entityStack.getCount();
        int allowedToInventory;

        if (entry != null && entry.isOverflowDeposit()) {
            int carriedCount = countInInventory(inv, key);
            int capRemaining = Math.max(0, entry.getRefillTarget() - carriedCount);
            allowedToInventory = getInsertableCapacity(inv, key, Math.min(stackCount, capRemaining));
        } else if (isOverflowOnFullInventory()) {
            int fitInInventory = getInsertableCapacity(inv, key, stackCount);
            if (fitInInventory >= stackCount) {
                // Entire stack fits normally in the player's inventory; let vanilla handle it
                return PickupOverflowResult.NONE;
            }
            allowedToInventory = fitInInventory;
        } else {
            return PickupOverflowResult.NONE;
        }

        int remainderForVault = stackCount - allowedToInventory;
        if (remainderForVault <= 0) {
            return PickupOverflowResult.NONE;
        }

        long acceptedToVault = deposit(key, remainderForVault);
        if (acceptedToVault <= 0L) {
            return PickupOverflowResult.NONE;
        }

        int insertedToInventory = 0;
        if (allowedToInventory > 0) {
            insertedToInventory = insertIntoInventory(inv, key, allowedToInventory);
        }

        int totalConsumed = insertedToInventory + (int) acceptedToVault;
        entityStack.shrink(totalConsumed);

        if (insertedToInventory > 0) {
            syncPlayerInventory(player);
        }
        Services.PLATFORM.markVaultDirty(player);
        Services.PLATFORM.sendVaultDelta(player, key, this.entries.get(key));
        Services.PLATFORM.sendOverflowToast(player, key.toSingleStack(), (int) acceptedToVault, false);

        return new PickupOverflowResult(true, insertedToInventory, (int) acceptedToVault, totalConsumed);
    }

    /**
     * Inserts up to {@code amount} items of {@code key} into the given player inventory's 36 main slots,
     * respecting {@link net.minecraft.world.item.Item#getMaxStackSize()}.
     *
     * @return the number of items actually inserted
     */
    public static int insertIntoInventory(Inventory inv, ItemKey key, int amount) {
        if (key == null || key.isEmpty() || amount <= 0) {
            return 0;
        }
        int maxStackSize = Math.max(1, key.getItem().getMaxStackSize());
        int remaining = amount;

        // Pass 1: Top up existing partial stacks matching key
        for (int i = 0; i < inv.items.size() && remaining > 0; i++) {
            ItemStack existing = inv.items.get(i);
            if (!existing.isEmpty() && key.matches(existing) && existing.getCount() < maxStackSize) {
                int space = maxStackSize - existing.getCount();
                int toAdd = Math.min(space, remaining);
                existing.grow(toAdd);
                remaining -= toAdd;
            }
        }

        // Pass 2: Place into empty inventory slots
        for (int i = 0; i < inv.items.size() && remaining > 0; i++) {
            ItemStack existing = inv.items.get(i);
            if (existing.isEmpty()) {
                int toPlace = Math.min(maxStackSize, remaining);
                inv.items.set(i, key.toStack(toPlace));
                remaining -= toPlace;
            }
        }

        return amount - remaining;
    }

    public void replaceAllFromSync(Collection<VaultEntry> syncedEntries, Collection<BuildEcho> syncedEchoes) {
        this.entries.clear();
        for (VaultEntry entry : syncedEntries) {
            if (entry != null && !entry.getKey().isEmpty()) {
                this.entries.put(entry.getKey(), entry);
            }
        }
        this.buildEchoes.clear();
        if (syncedEchoes != null) {
            this.buildEchoes.addAll(syncedEchoes);
        }
    }

    public void appendEntriesFromSync(Collection<VaultEntry> chunkEntries) {
        for (VaultEntry entry : chunkEntries) {
            if (entry != null && !entry.getKey().isEmpty()) {
                this.entries.put(entry.getKey(), entry);
            }
        }
    }

    public void applyDelta(ItemKey key, @Nullable VaultEntry updatedEntry) {
        if (key == null || key.isEmpty()) {
            return;
        }
        if (updatedEntry == null || !updatedEntry.shouldRetain()) {
            this.entries.remove(key);
        } else {
            this.entries.put(key, updatedEntry);
        }
    }

    public CompoundTag save(CompoundTag root) {
        ListTag entriesTag = new ListTag();
        for (VaultEntry entry : this.entries.values()) {
            if (!entry.getKey().isEmpty() && entry.shouldRetain()) {
                entriesTag.add(entry.toTag());
            }
        }
        root.put(NBT_ENTRIES, entriesTag);

        ListTag echoesTag = new ListTag();
        for (BuildEcho echo : this.buildEchoes) {
            echoesTag.add(echo.toTag());
        }
        root.put(NBT_BUILD_ECHOES, echoesTag);
        return root;
    }

    public void load(CompoundTag root) {
        this.entries.clear();
        this.buildEchoes.clear();

        if (root.contains(NBT_ENTRIES, Tag.TAG_LIST)) {
            ListTag entriesTag = root.getList(NBT_ENTRIES, Tag.TAG_COMPOUND);
            for (int i = 0; i < entriesTag.size(); i++) {
                VaultEntry entry = VaultEntry.fromTag(entriesTag.getCompound(i));
                if (!entry.getKey().isEmpty() && entry.shouldRetain()) {
                    this.entries.put(entry.getKey(), entry);
                }
            }
        }

        if (root.contains(NBT_BUILD_ECHOES, Tag.TAG_LIST)) {
            ListTag echoesTag = root.getList(NBT_BUILD_ECHOES, Tag.TAG_COMPOUND);
            for (int i = 0; i < echoesTag.size(); i++) {
                this.buildEchoes.add(BuildEcho.fromTag(echoesTag.getCompound(i)));
            }
        }
    }

    public record RestReplenishResult(
            int sweptToVaultCount,
            int refilledToPlayerCount,
            Set<ItemKey> modifiedKeys
    ) {
    }

    public record PickupOverflowResult(
            boolean handled,
            int insertedToInventory,
            int sentToVault,
            int totalConsumed
    ) {
        public static final PickupOverflowResult NONE = new PickupOverflowResult(false, 0, 0, 0);
    }
}
