package com.soulsvault.world.inventory;

import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.core.AccessorySlotAccess;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultAccess;
import com.soulsvault.core.VaultRules;
import com.soulsvault.core.VaultSync;
import com.soulsvault.core.rest.RestPointProfile;
import com.soulsvault.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Server-synchronized container menu for the Souls-like Inventory & Casket of Echoes UI.
 * <p>
 * Provides real {@link Slot} instances for:
 * <ul>
 *   <li>3x3 Crafting Grid + Result Slot with automatic ingredient refill from the player's Vault</li>
 *   <li>5 Primary Equipment slots (Head, Chest, Legs, Feet, Offhand)</li>
 *   <li>36 Player Inventory slots (27 Main Inventory + 9 Hotbar) for full Drag-and-Drop, Mouse Tweaks,
 *       Inventory Essentials, and Controller snapping</li>
 *   <li>Dynamic Curios API, The Aether accessory, and Cosmetic Armor Reworked slots</li>
 * </ul>
 */
public class SoulsVaultMenu extends AbstractContainerMenu {
    public static final int LEFT_PANE_OFFSET_X = 8;
    public static final int INV_START_X = LEFT_PANE_OFFSET_X + 7;
    public static final int INV_MAIN_START_Y = 140;
    public static final int INV_HOTBAR_Y = 198;

    public static final int EQUIP_ROW_Y = 62;
    public static final int ACCESSORY_GRID_START_Y = 86;
    public static final int ACCESSORY_COLS = 8;
    public static final int ACCESSORY_ROWS = 2;
    public static final int ACCESSORIES_PER_PAGE = ACCESSORY_COLS * ACCESSORY_ROWS;

    public static final int CRAFT_GRID_X = LEFT_PANE_OFFSET_X + 16;
    public static final int CRAFT_GRID_Y = 66;
    public static final int CRAFT_RESULT_X = LEFT_PANE_OFFSET_X + 118;
    public static final int CRAFT_RESULT_Y = 84;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    public enum LeftPaneMode {
        GEAR_CURIOS,
        CRAFTING,
        BUILD_ECHOES
    }

    private final Player player;
    private final RestPointProfile.Grants grants;
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();

    private LeftPaneMode leftPaneMode = LeftPaneMode.GEAR_CURIOS;
    private boolean cosmeticMode = false;
    private int accessoryPage = 0;

    private final List<AccessoryMenuSlot> activeAccessorySlots = new ArrayList<>();
    private final List<AccessoryMenuSlot> cosmeticAccessorySlots = new ArrayList<>();
    private final List<AccessoryMenuSlot> cosmeticArmorSlots = new ArrayList<>();

    private int craftResultSlotIndex = -1;
    private int craftMatrixStartIndex = -1;
    private int craftMatrixEndIndex = -1;
    private int equipStartIndex = -1;
    private int equipEndIndex = -1;
    private int playerInvStartIndex = -1;
    private int playerInvEndIndex = -1;

    private static final int LOCK_COMBAT = 1;
    private static final int LOCK_REMOTE_UNLOCKED = 1 << 1;
    private static final int LOCK_REFRESH_TICKS = 10;
    /** Synced to the client: combat lock and whether the remote access cost was paid this session. */
    private final DataSlot lockState = DataSlot.standalone();
    private int lockRefreshCountdown = 0;

    public SoulsVaultMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData != null && extraData.readableBytes() > 0
                ? RestPointProfile.Grants.decode(extraData)
                : RestPointProfile.Grants.NONE);
    }

    /**
     * @param grants session powers from the Rest Point profile it was opened at ({@code NONE} when opened remotely)
     */
    public SoulsVaultMenu(int containerId, Inventory playerInventory, RestPointProfile.Grants grants) {
        super(Services.PLATFORM.getVaultMenuType(), containerId);
        this.player = playerInventory.player;
        this.grants = grants != null ? grants : RestPointProfile.Grants.NONE;

        // 1. Crafting Result Slot (Index 0) + 3x3 Crafting Matrix (Indices 1..9)
        this.craftResultSlotIndex = this.slots.size();
        this.addSlot(new VaultCraftingResultSlot(
                this.player,
                this.craftSlots,
                this.resultSlots,
                0,
                CRAFT_RESULT_X,
                CRAFT_RESULT_Y
        ));

        this.craftMatrixStartIndex = this.slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int idx = col + row * 3;
                int sx = CRAFT_GRID_X + col * 18;
                int sy = CRAFT_GRID_Y + row * 18;
                this.addSlot(new Slot(this.craftSlots, idx, sx, sy) {
                    @Override
                    public boolean isActive() {
                        return SoulsVaultMenu.this.leftPaneMode == LeftPaneMode.CRAFTING;
                    }
                });
            }
        }
        this.craftMatrixEndIndex = this.slots.size();

        // 2. Primary Equipment Slots: HEAD, CHEST, LEGS, FEET, OFFHAND (Indices 10..14)
        this.equipStartIndex = this.slots.size();
        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            final EquipmentSlot eqSlot = ARMOR_SLOTS[i];
            int invIndex = 39 - i;
            int sx = INV_START_X + i * 20;
            this.addSlot(new EquipmentMenuSlot(playerInventory, invIndex, sx, EQUIP_ROW_Y, eqSlot));
        }
        // Offhand Slot (Inventory index 40)
        this.addSlot(new OffhandMenuSlot(playerInventory, 40, INV_START_X + 4 * 20, EQUIP_ROW_Y));
        this.equipEndIndex = this.slots.size();

        // 3. Player Main Inventory (27 slots, indices 9..35)
        this.playerInvStartIndex = this.slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int invIndex = col + (row + 1) * 9;
                int sx = INV_START_X + col * 18;
                int sy = INV_MAIN_START_Y + row * 18;
                this.addSlot(new Slot(playerInventory, invIndex, sx, sy));
            }
        }

        // 4. Player Hotbar (9 slots, indices 0..8)
        for (int col = 0; col < 9; col++) {
            int sx = INV_START_X + col * 18;
            this.addSlot(new Slot(playerInventory, col, sx, INV_HOTBAR_Y));
        }
        this.playerInvEndIndex = this.slots.size();

        // 5. Dynamic Curios / Aether Accessories & Cosmetic Armor Slots
        initAccessorySlots();

        this.addDataSlot(this.lockState);
    }

    private void initAccessorySlots() {
        // Cosmetic Armor Reworked slots (aligned over the 4 Armor slot positions when cosmeticMode is active)
        List<AccessorySlotAccess> cosArmor = Services.PLATFORM.getCosmeticArmorSlots(this.player);
        for (int i = 0; i < cosArmor.size() && i < 4; i++) {
            AccessorySlotAccess access = cosArmor.get(i);
            int sx = INV_START_X + i * 20;
            AccessoryMenuSlot menuSlot = new AccessoryMenuSlot(access, sx, EQUIP_ROW_Y, 0, true, true);
            this.cosmeticArmorSlots.add(menuSlot);
            this.addSlot(menuSlot);
        }

        // Active Curios & Aether accessory slots (paged 8x2 grid)
        List<AccessorySlotAccess> normalCurios = Services.PLATFORM.getAccessorySlots(this.player, false);
        for (int i = 0; i < normalCurios.size(); i++) {
            AccessorySlotAccess access = normalCurios.get(i);
            int page = i / ACCESSORIES_PER_PAGE;
            int idxOnPage = i % ACCESSORIES_PER_PAGE;
            int col = idxOnPage % ACCESSORY_COLS;
            int row = idxOnPage / ACCESSORY_COLS;
            int sx = INV_START_X + col * 20;
            int sy = ACCESSORY_GRID_START_Y + row * 20;
            AccessoryMenuSlot menuSlot = new AccessoryMenuSlot(access, sx, sy, page, false, false);
            this.activeAccessorySlots.add(menuSlot);
            this.addSlot(menuSlot);
        }

        // Cosmetic Curios slots (paged 8x2 grid when cosmeticMode is active)
        List<AccessorySlotAccess> cosmeticCurios = Services.PLATFORM.getAccessorySlots(this.player, true);
        for (int i = 0; i < cosmeticCurios.size(); i++) {
            AccessorySlotAccess access = cosmeticCurios.get(i);
            int page = i / ACCESSORIES_PER_PAGE;
            int idxOnPage = i % ACCESSORIES_PER_PAGE;
            int col = idxOnPage % ACCESSORY_COLS;
            int row = idxOnPage / ACCESSORY_COLS;
            int sx = INV_START_X + col * 20;
            int sy = ACCESSORY_GRID_START_Y + row * 20;
            AccessoryMenuSlot menuSlot = new AccessoryMenuSlot(access, sx, sy, page, true, false);
            this.cosmeticAccessorySlots.add(menuSlot);
            this.addSlot(menuSlot);
        }
    }

    /**
     * Whether Vault deposits/withdrawals are allowed right now: at a Rest Point, or remotely when the server allows
     * it and any remote access cost was paid; never while combat-locked. Mirrors on the client via a data slot.
     */
    public boolean isVaultAccessAllowed() {
        if (isCombatLocked()) {
            return false;
        }
        if (this.grants.vault()) {
            return true;
        }
        return SoulsVaultConfig.isAllowRemoteVaultInFullInventory() && !needsRemoteUnlock();
    }

    public boolean isCombatLocked() {
        return (this.lockState.get() & LOCK_COMBAT) != 0;
    }

    /**
     * Whether the Vault pane shows the "unlock for a cost" button (remote session, cost configured, not yet paid).
     */
    public boolean needsRemoteUnlock() {
        return !this.grants.vault()
                && SoulsVaultConfig.isAllowRemoteVaultInFullInventory()
                && SoulsVaultConfig.remoteAccess().hasCost()
                && (this.lockState.get() & LOCK_REMOTE_UNLOCKED) == 0;
    }

    /**
     * Server-side reason Vault deposits/withdrawals are refused, or {@code null}.
     */
    @Nullable
    public Component getVaultDenialReason(ServerPlayer serverPlayer) {
        if (!this.grants.vault() && !SoulsVaultConfig.isAllowRemoteVaultInFullInventory()) {
            return Component.translatable("gui.soulsvault.notice.rest_point_required");
        }
        if (needsRemoteUnlock()) {
            return Component.translatable("gui.soulsvault.notice.remote_locked");
        }
        return VaultAccess.check(serverPlayer, VaultAccess.Type.VAULT, this.grants.vault());
    }

    /**
     * Charges the configured remote access cost once for this Casket session.
     */
    public void tryUnlockRemoteAccess(ServerPlayer serverPlayer) {
        if (!needsRemoteUnlock()) {
            return;
        }
        SoulsVaultConfig.RemoteAccess cost = SoulsVaultConfig.remoteAccess();
        boolean paid = serverPlayer.getAbilities().instabuild || switch (cost.costType()) {
            case XP_LEVELS -> {
                if (serverPlayer.experienceLevel < cost.costAmount()) {
                    yield false;
                }
                serverPlayer.giveExperienceLevels(-cost.costAmount());
                yield true;
            }
            case XP_POINTS -> {
                if (serverPlayer.totalExperience < cost.costAmount()) {
                    yield false;
                }
                serverPlayer.giveExperiencePoints(-cost.costAmount());
                yield true;
            }
            case ITEM -> payWithItems(serverPlayer, cost);
            case NONE -> true;
        };
        if (!paid) {
            serverPlayer.displayClientMessage(Component.translatable("gui.soulsvault.notice.remote_cost_unaffordable")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        this.lockState.set(this.lockState.get() | LOCK_REMOTE_UNLOCKED);
        this.broadcastChanges();
    }

    private static boolean payWithItems(ServerPlayer serverPlayer, SoulsVaultConfig.RemoteAccess cost) {
        ResourceLocation id = ResourceLocation.tryParse(cost.costItem());
        Item item = id != null ? BuiltInRegistries.ITEM.get(id) : null;
        if (item == null) {
            return false;
        }
        Inventory inventory = serverPlayer.getInventory();
        if (inventory.countItem(item) < cost.costAmount()) {
            return false;
        }
        int remaining = cost.costAmount();
        for (int i = 0; i < inventory.items.size() && remaining > 0; i++) {
            ItemStack stack = inventory.items.get(i);
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
        PlayerVaultData.syncPlayerInventory(serverPlayer);
        return true;
    }

    @Override
    public void broadcastChanges() {
        if (this.player instanceof ServerPlayer serverPlayer && --this.lockRefreshCountdown <= 0) {
            this.lockRefreshCountdown = LOCK_REFRESH_TICKS;
            boolean combat = VaultAccess.getCombatReason(serverPlayer) != null;
            int state = this.lockState.get();
            this.lockState.set(combat ? state | LOCK_COMBAT : state & ~LOCK_COMBAT);
        }
        super.broadcastChanges();
    }

    public boolean isOpenedAtRestPoint() {
        return this.grants.any();
    }

    /**
     * Powers granted by the Rest Point profile this session was opened at.
     */
    public RestPointProfile.Grants getGrants() {
        return this.grants;
    }

    public LeftPaneMode getLeftPaneMode() {
        return this.leftPaneMode;
    }

    /**
     * Menu button id handled by {@link #clickMenuButton} that switches the left pane to {@code mode}.
     */
    public static int leftPaneButtonId(LeftPaneMode mode) {
        return switch (mode) {
            case GEAR_CURIOS -> 0;
            case CRAFTING -> 1;
            case BUILD_ECHOES -> 2;
        };
    }

    public void setLeftPaneMode(LeftPaneMode mode) {
        this.leftPaneMode = mode != null ? mode : LeftPaneMode.GEAR_CURIOS;
    }

    public boolean isCosmeticMode() {
        return this.cosmeticMode;
    }

    public void setCosmeticMode(boolean cosmeticMode) {
        this.cosmeticMode = cosmeticMode;
        this.accessoryPage = 0;
    }

    public boolean hasCosmeticSlots() {
        return !this.cosmeticArmorSlots.isEmpty() || !this.cosmeticAccessorySlots.isEmpty();
    }

    public int getAccessoryPage() {
        return this.accessoryPage;
    }

    public int getTotalAccessoryPages() {
        List<AccessoryMenuSlot> list = (this.cosmeticMode && !this.cosmeticAccessorySlots.isEmpty())
                ? this.cosmeticAccessorySlots
                : this.activeAccessorySlots;
        return Math.max(1, (list.size() + ACCESSORIES_PER_PAGE - 1) / ACCESSORIES_PER_PAGE);
    }

    public void setAccessoryPage(int page) {
        int maxPage = Math.max(0, getTotalAccessoryPages() - 1);
        this.accessoryPage = Math.max(0, Math.min(page, maxPage));
    }

    public List<AccessoryMenuSlot> getVisibleAccessorySlots() {
        if (this.leftPaneMode != LeftPaneMode.GEAR_CURIOS) {
            return List.of();
        }
        List<AccessoryMenuSlot> result = new ArrayList<>();
        if (this.cosmeticMode && !this.cosmeticArmorSlots.isEmpty()) {
            result.addAll(this.cosmeticArmorSlots);
        }
        List<AccessoryMenuSlot> source = (this.cosmeticMode && !this.cosmeticAccessorySlots.isEmpty())
                ? this.cosmeticAccessorySlots
                : this.activeAccessorySlots;
        for (AccessoryMenuSlot slot : source) {
            if (slot.getPage() == this.accessoryPage) {
                result.add(slot);
            }
        }
        return result;
    }

    @Override
    public void slotsChanged(Container container) {
        if (container == this.craftSlots) {
            slotChangedCraftingGrid(this.player.level());
        }
        super.slotsChanged(container);
    }

    private void slotChangedCraftingGrid(Level level) {
        if (!level.isClientSide && this.player instanceof ServerPlayer serverPlayer) {
            ItemStack result = ItemStack.EMPTY;
            Optional<CraftingRecipe> optional = level.getServer()
                    .getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, this.craftSlots, level);
            if (optional.isPresent()) {
                CraftingRecipe recipe = optional.get();
                if (this.resultSlots.setRecipeUsed(level, serverPlayer, recipe)) {
                    ItemStack assembled = recipe.assemble(this.craftSlots, level.registryAccess());
                    if (assembled.isItemEnabled(level.enabledFeatures())) {
                        result = assembled;
                    }
                }
            }
            this.resultSlots.setItem(0, result);
            this.setRemoteSlot(this.craftResultSlotIndex, result);
            serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(
                    this.containerId,
                    this.incrementStateId(),
                    this.craftResultSlotIndex,
                    result
            ));
        }
    }

    /**
     * Sweeps all items currently inside the 3x3 Crafting Grid into the player's Vault (if allowed)
     * or back into the player's inventory.
     */
    public void clearCraftingGridToVaultOrInventory(ServerPlayer serverPlayer) {
        PlayerVaultData vaultData = Services.PLATFORM.getPlayerVaultData(serverPlayer);
        Set<ItemKey> modifiedVaultKeys = new LinkedHashSet<>();

        for (int i = 0; i < this.craftSlots.getContainerSize(); i++) {
            ItemStack stack = this.craftSlots.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (isVaultAccessAllowed()) {
                ItemKey key = ItemKey.fromStack(stack);
                long accepted = vaultData.deposit(key, stack.getCount());
                if (accepted > 0L) {
                    stack.shrink((int) accepted);
                    modifiedVaultKeys.add(key);
                }
            }
            if (!stack.isEmpty()) {
                serverPlayer.getInventory().placeItemBackInInventory(stack);
            }
            this.craftSlots.setItem(i, ItemStack.EMPTY);
        }

        this.slotsChanged(this.craftSlots);
        this.broadcastChanges();

        VaultSync.commit(serverPlayer, modifiedVaultKeys);
    }

    /**
     * The 3x3 crafting grid container (read by recipe viewer adapters to count what is already in the grid).
     */
    public CraftingContainer getCraftingGrid() {
        return this.craftSlots;
    }

    /**
     * Fills the 3x3 grid for a recipe chosen in a recipe viewer (JEI / REI / EMI), like a storage-terminal
     * crafting grid: the grid is first swept back, then each ingredient is pulled from the Vault (when access
     * is allowed) and then from the player's inventory. Does nothing if the server-side plan cannot be filled.
     */
    public void fillCraftingGridForRecipe(ServerPlayer serverPlayer, List<List<ItemKey>> slotCandidates, int requestedCrafts) {
        PlayerVaultData vaultData = isVaultAccessAllowed() ? Services.PLATFORM.getPlayerVaultData(serverPlayer) : null;
        CraftingGridPlanner.Plan plan = CraftingGridPlanner.plan(
                CraftingGridPlanner.collectAvailable(serverPlayer, this.craftSlots, vaultData),
                slotCandidates,
                requestedCrafts
        );
        if (!plan.canFill()) {
            return;
        }

        setLeftPaneMode(LeftPaneMode.CRAFTING);
        clearCraftingGridToVaultOrInventory(serverPlayer);

        Set<ItemKey> modifiedVaultKeys = new LinkedHashSet<>();
        for (int slot = 0; slot < CraftingGridPlanner.GRID_SIZE; slot++) {
            ItemKey key = plan.chosen()[slot];
            if (key.isEmpty()) {
                continue;
            }
            int needed = plan.craftCount();
            int taken = 0;
            if (vaultData != null) {
                taken = (int) vaultData.withdraw(key, needed);
                if (taken > 0) {
                    modifiedVaultKeys.add(key);
                }
            }
            if (taken < needed) {
                taken += takeFromInventory(serverPlayer.getInventory(), key, needed - taken);
            }
            if (taken > 0) {
                this.craftSlots.setItem(slot, key.toStack(taken));
            }
        }

        VaultSync.commit(serverPlayer, modifiedVaultKeys);
        PlayerVaultData.syncPlayerInventory(serverPlayer);
        this.slotsChanged(this.craftSlots);
        this.broadcastChanges();
    }

    private static int takeFromInventory(Inventory inventory, ItemKey key, int amount) {
        int taken = 0;
        for (int i = 0; i < inventory.items.size() && taken < amount; i++) {
            ItemStack stack = inventory.items.get(i);
            if (!stack.isEmpty() && key.matches(stack)) {
                int take = Math.min(amount - taken, stack.getCount());
                stack.shrink(take);
                taken += take;
            }
        }
        return taken;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            clearCraftingGridToVaultOrInventory(serverPlayer);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case 0 -> setLeftPaneMode(LeftPaneMode.GEAR_CURIOS);
            case 1 -> setLeftPaneMode(LeftPaneMode.CRAFTING);
            case 2 -> setLeftPaneMode(LeftPaneMode.BUILD_ECHOES);
            case 3 -> setCosmeticMode(!this.cosmeticMode);
            case 4 -> setAccessoryPage(this.accessoryPage - 1);
            case 5 -> setAccessoryPage(this.accessoryPage + 1);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.isAlive();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack slotStack = slot.getItem();
        ItemStack copy = slotStack.copy();

        // 1. Shift-Clicking Crafting Result Slot (Index 0) -> Move to Player Inventory (Hotbar -> Main)
        if (index == this.craftResultSlotIndex) {
            slotStack.getItem().onCraftedBy(slotStack, player.level(), player);
            if (!this.moveItemStackTo(slotStack, this.playerInvStartIndex, this.playerInvEndIndex, true)) {
                return ItemStack.EMPTY;
            }
            slot.onQuickCraft(slotStack, copy);
        }
        // 2. Shift-Clicking 3x3 Crafting Matrix (1..9) or Equipment/Accessory Slots -> Move to Player Inventory
        else if ((index >= this.craftMatrixStartIndex && index < this.craftMatrixEndIndex)
                || (index >= this.equipStartIndex && index < this.equipEndIndex)
                || index >= this.playerInvEndIndex) {
            if (!this.moveItemStackTo(slotStack, this.playerInvStartIndex, this.playerInvEndIndex, false)) {
                return ItemStack.EMPTY;
            }
        }
        // 3. Shift-Clicking Player Inventory (Main 27 + Hotbar 9):
        //    If in Crafting mode, move to 3x3 grid or Vault; if Vault access is allowed, deposit straight to Vault!
        else if (index >= this.playerInvStartIndex && index < this.playerInvEndIndex) {
            // First check if item can be equipped into an empty Armor / Offhand / Accessory slot when in GEAR_CURIOS mode
            if (this.leftPaneMode == LeftPaneMode.GEAR_CURIOS) {
                EquipmentSlot eqTarget = Mob.getEquipmentSlotForItem(slotStack);
                if (eqTarget.getType() == EquipmentSlot.Type.ARMOR) {
                    int armorSlotIdx = this.equipStartIndex + (3 - eqTarget.getIndex());
                    if (!this.slots.get(armorSlotIdx).hasItem()
                            && this.moveItemStackTo(slotStack, armorSlotIdx, armorSlotIdx + 1, false)) {
                        slot.setChanged();
                        return copy;
                    }
                }
                // Try active Curios/Aether accessory slots if empty
                if (this.moveItemStackTo(slotStack, this.playerInvEndIndex, this.slots.size(), false)) {
                    slot.setChanged();
                    return copy;
                }
            }

            // Deposit into Vault when Vault access is active on the server
            if (isVaultAccessAllowed()) {
                if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                    PlayerVaultData vaultData = Services.PLATFORM.getPlayerVaultData(serverPlayer);
                    ItemKey key = ItemKey.fromStack(slotStack);
                    long accepted = vaultData.deposit(key, slotStack.getCount());
                    if (accepted > 0L) {
                        slotStack.shrink((int) accepted);
                        if (slotStack.isEmpty()) {
                            slot.set(ItemStack.EMPTY);
                        } else {
                            slot.setChanged();
                        }
                        PlayerVaultData.syncPlayerInventory(serverPlayer);
                        VaultSync.commit(serverPlayer, List.of(key));
                        return copy;
                    }
                    Component rejected = VaultRules.getRejectionReason(key);
                    if (rejected != null) {
                        serverPlayer.displayClientMessage(rejected.copy().withStyle(ChatFormatting.RED), true);
                    }
                } else if (player.level().isClientSide) {
                    return ItemStack.EMPTY;
                }
            }

            // Fallback: swap between Main Inventory (27) and Hotbar (9)
            int hotbarStart = this.playerInvEndIndex - 9;
            if (index < hotbarStart) {
                if (!this.moveItemStackTo(slotStack, hotbarStart, this.playerInvEndIndex, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(slotStack, this.playerInvStartIndex, hotbarStart, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (slotStack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        if (slotStack.getCount() == copy.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, slotStack);
        if (index == this.craftResultSlotIndex) {
            player.drop(slotStack, false);
        }
        return copy;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return slot.container != this.resultSlots && super.canTakeItemForPickAll(stack, slot);
    }

    /**
     * Crafting Result Slot that automatically pulls replacement ingredients from the player's
     * Casket of Echoes Vault whenever a 3x3 crafting grid slot is depleted during crafting!
     */
    private final class VaultCraftingResultSlot extends ResultSlot {
        private final Player craftPlayer;

        VaultCraftingResultSlot(
                Player player,
                CraftingContainer craftSlots,
                Container resultContainer,
                int slot,
                int x,
                int y
        ) {
            super(player, craftSlots, resultContainer, slot, x, y);
            this.craftPlayer = player;
        }

        @Override
        public boolean isActive() {
            return SoulsVaultMenu.this.leftPaneMode == LeftPaneMode.CRAFTING;
        }

        @Override
        public void onTake(Player player, ItemStack craftedStack) {
            // Snapshot ingredient ItemKeys in the 3x3 grid prior to consumption
            ItemKey[] priorKeys = new ItemKey[SoulsVaultMenu.this.craftSlots.getContainerSize()];
            for (int i = 0; i < priorKeys.length; i++) {
                priorKeys[i] = ItemKey.fromStack(SoulsVaultMenu.this.craftSlots.getItem(i));
            }

            super.onTake(player, craftedStack);

            // Server-side Auto-Refill from Vault (Refined Storage / AE2 style)
            if (!player.level().isClientSide
                    && player instanceof ServerPlayer serverPlayer
                    && SoulsVaultMenu.this.isVaultAccessAllowed()) {
                PlayerVaultData vaultData = Services.PLATFORM.getPlayerVaultData(serverPlayer);
                Set<ItemKey> modifiedVaultKeys = new LinkedHashSet<>();

                for (int i = 0; i < priorKeys.length; i++) {
                    ItemKey ingredientKey = priorKeys[i];
                    if (ingredientKey == null || ingredientKey.isEmpty()) {
                        continue;
                    }
                    ItemStack remainingInSlot = SoulsVaultMenu.this.craftSlots.getItem(i);
                    if (remainingInSlot.isEmpty() && vaultData.getCount(ingredientKey) > 0L) {
                        long withdrawn = vaultData.withdraw(ingredientKey, 1L);
                        if (withdrawn > 0L) {
                            SoulsVaultMenu.this.craftSlots.setItem(i, ingredientKey.toSingleStack());
                            modifiedVaultKeys.add(ingredientKey);
                        }
                    }
                }

                VaultSync.commit(serverPlayer, modifiedVaultKeys);
            }

            SoulsVaultMenu.this.slotsChanged(SoulsVaultMenu.this.craftSlots);
        }
    }

    /**
     * Vanilla Armor Slot active when in Gear & Curios mode (and not overridden by Cosmetic Armor).
     */
    public final class EquipmentMenuSlot extends Slot {
        private final EquipmentSlot equipmentSlot;
        private final ResourceLocation emptyIcon;

        EquipmentMenuSlot(Container container, int slot, int x, int y, EquipmentSlot equipmentSlot) {
            super(container, slot, x, y);
            this.equipmentSlot = equipmentSlot;
            this.emptyIcon = switch (equipmentSlot) {
                case HEAD -> AccessorySlotAccess.ICON_HEAD;
                case CHEST -> AccessorySlotAccess.ICON_CHEST;
                case LEGS -> AccessorySlotAccess.ICON_LEGS;
                case FEET -> AccessorySlotAccess.ICON_FEET;
                default -> AccessorySlotAccess.ICON_SHIELD;
            };
        }

        public ResourceLocation getEmptyIcon() {
            return this.emptyIcon;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return Mob.getEquipmentSlotForItem(stack) == this.equipmentSlot;
        }

        @Override
        public boolean mayPickup(Player player) {
            ItemStack itemstack = this.getItem();
            return (itemstack.isEmpty() || player.isCreative() || !EnchantmentHelper.hasBindingCurse(itemstack))
                    && super.mayPickup(player);
        }

        @Override
        public boolean isActive() {
            if (SoulsVaultMenu.this.leftPaneMode != LeftPaneMode.GEAR_CURIOS) {
                return false;
            }
            return !(SoulsVaultMenu.this.cosmeticMode && !SoulsVaultMenu.this.cosmeticArmorSlots.isEmpty());
        }
    }

    /**
     * Vanilla Offhand Slot active when in Gear & Curios mode.
     */
    public final class OffhandMenuSlot extends Slot {
        OffhandMenuSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        public ResourceLocation getEmptyIcon() {
            return AccessorySlotAccess.ICON_SHIELD;
        }

        @Override
        public boolean isActive() {
            return SoulsVaultMenu.this.leftPaneMode == LeftPaneMode.GEAR_CURIOS;
        }
    }

    /**
     * Dynamic Slot backed by {@link AccessorySlotAccess} (Curios API, Aether Accessories, or Cosmetic Armor).
     */
    public final class AccessoryMenuSlot extends Slot {
        private final AccessorySlotAccess access;
        private final int page;
        private final boolean cosmeticGroup;
        private final boolean isArmorOverride;

        AccessoryMenuSlot(
                AccessorySlotAccess access,
                int x,
                int y,
                int page,
                boolean cosmeticGroup,
                boolean isArmorOverride
        ) {
            super(new SimpleContainer(1), 0, x, y);
            this.access = access;
            this.page = page;
            this.cosmeticGroup = cosmeticGroup;
            this.isArmorOverride = isArmorOverride;
        }

        public AccessorySlotAccess getAccess() {
            return this.access;
        }

        public int getPage() {
            return this.page;
        }

        @Nullable
        public ResourceLocation getEmptyIcon() {
            return this.access.getEmptyIcon();
        }

        @Override
        public ItemStack getItem() {
            return this.access.getStack();
        }

        @Override
        public void set(ItemStack stack) {
            this.access.setStack(stack);
            this.setChanged();
        }

        @Override
        public void setByPlayer(ItemStack stack) {
            this.set(stack);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.access.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return this.access.mayPickup(player);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public ItemStack remove(int amount) {
            ItemStack current = getItem();
            if (current.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack split = current.copy();
            ItemStack taken = split.split(amount);
            set(split.isEmpty() ? ItemStack.EMPTY : split);
            return taken;
        }

        @Override
        public boolean isActive() {
            if (SoulsVaultMenu.this.leftPaneMode != LeftPaneMode.GEAR_CURIOS) {
                return false;
            }
            if (this.isArmorOverride) {
                return SoulsVaultMenu.this.cosmeticMode;
            }
            boolean useCosmeticCurios = SoulsVaultMenu.this.cosmeticMode
                    && !SoulsVaultMenu.this.cosmeticAccessorySlots.isEmpty();
            return (this.cosmeticGroup == useCosmeticCurios) && (this.page == SoulsVaultMenu.this.accessoryPage);
        }
    }
}
