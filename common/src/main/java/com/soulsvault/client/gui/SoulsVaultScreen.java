package com.soulsvault.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.soulsvault.Constants;
import com.soulsvault.api.EchoSlotType;
import com.soulsvault.api.SoulsVaultApi;
import com.soulsvault.client.ClientVaultCache;
import com.soulsvault.client.SoulsVaultKeyMappings;
import com.soulsvault.client.gui.grid.VaultGridView;
import com.soulsvault.client.gui.widget.CategoryTabButton;
import com.soulsvault.client.gui.widget.GothicButton;
import com.soulsvault.client.gui.widget.ItemInspectionPane;
import com.soulsvault.client.gui.widget.VaultItemSlotWidget;
import com.soulsvault.client.party.ClientPartyCache;
import com.soulsvault.config.SoulsVaultClientConfig;
import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.core.BuildEcho;
import com.soulsvault.core.EquipLoad;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.VaultCategory;
import com.soulsvault.core.VaultEntry;
import com.soulsvault.core.VaultMeta;
import com.soulsvault.network.PacketC2SEchoEdit;
import com.soulsvault.network.PacketC2SVaultAction;
import com.soulsvault.platform.Services;
import com.soulsvault.world.inventory.SoulsVaultMenu;
import com.soulsvault.world.inventory.SoulsVaultMenu.LeftPaneMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Dual-pane Souls-like Inventory & Casket of Echoes container screen.
 * <p>
 * Extends {@link AbstractContainerScreen} over {@link SoulsVaultMenu} to provide:
 * <ul>
 *   <li>Native Drag-and-Drop across all 36 Player Inventory/Hotbar slots, Equipment, Curios/Aether accessories,
 *       Cosmetic Armor, and the built-in 3x3 Crafting Grid</li>
 *   <li>Out-of-the-box compatibility with Mouse Tweaks, Inventory Essentials, and Controller mods
 *       (Controllable, Controlify, Control Flex)</li>
 *   <li>8 Category Tabs starting with {@link VaultCategory#ALL} + full search ({@code @mod}, {@code #tag})</li>
 *   <li>Vault-to-Cursor pickup and Cursor-to-Vault deposit</li>
 * </ul>
 * The Vault grid is backed by an incrementally updated {@link VaultGridView}; nothing is re-filtered per tick.
 */
public class SoulsVaultScreen extends AbstractContainerScreen<SoulsVaultMenu> {
    public static final ResourceLocation VAULT_BG_TEXTURE =
            new ResourceLocation(Constants.MOD_ID, "textures/gui/vault_bg.png");

    public static final int WINDOW_WIDTH = 416;
    public static final int WINDOW_HEIGHT = 244;

    private static final int LEFT_PANE_OFFSET_X = SoulsVaultMenu.LEFT_PANE_OFFSET_X;
    private static final int LEFT_PANE_WIDTH = 176;
    private static final int RIGHT_PANE_OFFSET_X = 190;
    private static final int RIGHT_PANE_WIDTH = 218;

    private static final int SEARCH_BOX_WIDTH = 120;
    private static final int SEARCH_BOX_HEIGHT = 16;

    private static final int COLOR_CHARCOAL_BG = 0xF4121013;
    private static final int COLOR_PANE_BG = 0xEB0D0B0E;
    private static final int COLOR_BRONZE = 0xFF8C7853;
    private static final int COLOR_BRONZE_MUTED = 0xFF4A3F2C;
    private static final int COLOR_GOLD = 0xFFD4AF37;
    private static final int COLOR_TEXT_MAIN = 0xFFEAE2D6;
    private static final int COLOR_TEXT_SUB = 0xFFA89E8E;
    private static final int COLOR_WARNING = 0xFFE06C50;

    /** Crafting "→" arrow area relative to the GUI origin; recipe viewers expose it as a "show crafting recipes" click area. */
    public static final int CRAFT_ARROW_X = SoulsVaultMenu.CRAFT_GRID_X + 56;
    public static final int CRAFT_ARROW_Y = SoulsVaultMenu.CRAFT_GRID_Y + 18;
    public static final int CRAFT_ARROW_WIDTH = 18;
    public static final int CRAFT_ARROW_HEIGHT = 14;

    private static final int VAULT_COLS = 7;
    private static final int VAULT_ROWS = 6;
    private static final int VAULT_SLOT_STEP = 26;
    private static final int VAULT_GRID_OFFSET_X = RIGHT_PANE_OFFSET_X + 18;
    private static final int VAULT_GRID_OFFSET_Y = 58;
    private static final int SEAL_PANEL_WIDTH = VAULT_COLS * VAULT_SLOT_STEP - 2;
    private static final int SEAL_PANEL_HEIGHT = VAULT_ROWS * VAULT_SLOT_STEP - 2;
    private static final int SEAL_BUTTON_Y = VAULT_GRID_OFFSET_Y + 112;

    private static final int ECHO_ROWS_VISIBLE = 3;
    private static final int ECHO_ROW_HEIGHT = 22;
    private static final int ECHO_DRAWER_Y = 60;

    private static final int EQUIP_LOAD_BAR_Y = SoulsVaultMenu.EQUIP_ROW_Y + 19;
    private static final int EQUIP_LOAD_BAR_WIDTH = 96;

    private final VaultGridView gridView;
    private boolean inspectionMode = false;

    private final List<CategoryTabButton> categoryTabButtons = new ArrayList<>();
    private final List<VaultItemSlotWidget> vaultSlotWidgets = new ArrayList<>();

    private int vaultScrollRow = 0;
    private int buildEchoScroll = 0;

    private EditBox searchBox;
    private GothicButton sortButton;
    private GothicButton inspectToggleButton;
    private GothicButton helpLegendButton;
    private GothicButton partyButton;
    @Nullable
    private GothicButton waystonesButton;
    private GothicButton restReplenishButton;
    private GothicButton remoteUnlockButton;

    private GothicButton tabGearCuriosButton;
    private GothicButton tabCraftingButton;
    private GothicButton tabEchoesButton;

    private GothicButton cosmeticToggleButton;
    private GothicButton prevAccessoryPageButton;
    private GothicButton nextAccessoryPageButton;
    private GothicButton clearCraftingGridButton;

    private ItemInspectionPane inspectionPane;

    private ItemKey selectedItemKey = ItemKey.EMPTY;
    @Nullable
    private RefillMiniPopover refillPopover = null;
    @Nullable
    private EchoOptionsPopover echoPopover = null;

    private final ClientVaultCache.ChangeListener vaultListener;

    public SoulsVaultScreen(SoulsVaultMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = WINDOW_WIDTH;
        this.imageHeight = WINDOW_HEIGHT;
        this.gridView = new VaultGridView(
                ClientVaultCache.getVaultData(),
                SoulsVaultClientConfig.initialCategory(),
                SoulsVaultClientConfig.initialSortMode()
        );
        this.gridView.setChangeListener(this::bindVaultWidgets);
        this.vaultListener = this.gridView;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - WINDOW_WIDTH) / 2;
        this.topPos = (this.height - WINDOW_HEIGHT) / 2;

        int leftPaneX = this.leftPos + LEFT_PANE_OFFSET_X;
        int rightPaneX = this.leftPos + RIGHT_PANE_OFFSET_X;

        // 1. 8 Category Tabs (ALL + 7 specialized categories) aligned above the 176px Left Pane
        this.categoryTabButtons.clear();
        VaultCategory[] categories = VaultCategory.values();
        int tabWidth = 20;
        int tabHeight = 20;
        int tabGap = 2;
        int tabY = this.topPos + 20;

        for (int i = 0; i < categories.length; i++) {
            VaultCategory cat = categories[i];
            CategoryTabButton tabBtn = new CategoryTabButton(
                    leftPaneX + i * (tabWidth + tabGap),
                    tabY,
                    tabWidth,
                    tabHeight,
                    cat,
                    this::selectCategory
            );
            tabBtn.setSelected(cat == this.gridView.getCategory());
            this.categoryTabButtons.add(tabBtn);
            addRenderableWidget(tabBtn);
        }

        // 2. Search Box & Sort Selector above Right Pane
        String previousSearch = this.searchBox != null ? this.searchBox.getValue() : "";
        this.searchBox = new EditBox(
                this.font,
                rightPaneX + 5,
                this.topPos + 26,
                SEARCH_BOX_WIDTH - 10,
                10,
                Component.translatable("gui.soulsvault.search.hint")
        );
        this.searchBox.setMaxLength(64);
        this.searchBox.setBordered(false);
        this.searchBox.setTextColor(0xFFEAE2D6);
        this.searchBox.setValue(previousSearch);
        this.searchBox.setResponder(query -> {
            this.vaultScrollRow = 0;
            this.gridView.setSearch(query);
        });
        addRenderableWidget(this.searchBox);

        this.sortButton = new GothicButton(
                rightPaneX + SEARCH_BOX_WIDTH + 4,
                this.topPos + 22,
                RIGHT_PANE_WIDTH - SEARCH_BOX_WIDTH - 4,
                SEARCH_BOX_HEIGHT,
                this.gridView.getSortMode().getDisplayName(),
                btn -> {
                    this.gridView.setSortMode(this.gridView.getSortMode().next());
                    btn.setMessage(this.gridView.getSortMode().getDisplayName());
                    SoulsVaultClientConfig.rememberTab(this.gridView.getCategory(), this.gridView.getSortMode());
                }
        );
        addRenderableWidget(this.sortButton);

        // 3. Header Right Controls (right to left): [?] Help, Inspect, Waystones (if loaded), Party
        //    (Full inventory replacement is a server config decision, so there is no in-game toggle.)
        int headerY = this.topPos + 4;
        this.helpLegendButton = new GothicButton(this.leftPos + WINDOW_WIDTH - 24, headerY, 16, 14,
                Component.literal("?"), btn -> {
        });
        addRenderableWidget(this.helpLegendButton);

        this.inspectToggleButton = new GothicButton(this.leftPos + WINDOW_WIDTH - 96, headerY, 68, 14,
                getInspectButtonLabel(), btn -> toggleInspectionMode());
        this.inspectToggleButton.setHighlighted(this.inspectionMode);
        addRenderableWidget(this.inspectToggleButton);

        int headerCursorX = this.leftPos + WINDOW_WIDTH - 100;
        this.waystonesButton = null;
        if (Services.PLATFORM.isModLoaded("waystones")) {
            headerCursorX -= 48;
            this.waystonesButton = new GothicButton(headerCursorX, headerY, 48, 14,
                    Component.translatable("gui.soulsvault.action.waystones"),
                    btn -> Services.PLATFORM.triggerWaystonesButton());
            addRenderableWidget(this.waystonesButton);
            headerCursorX -= 4;
        }
        headerCursorX -= 40;
        this.partyButton = new GothicButton(headerCursorX, headerY, 40, 14,
                Component.translatable("gui.soulsvault.action.party"),
                btn -> ClientPartyCache.openScreen(this, false));
        this.partyButton.setHighlighted(ClientPartyCache.getSnapshot() != null);
        addRenderableWidget(this.partyButton);

        // 4. Left-Pane Top Sub-Mode Tabs: [Gear & Curios] | [3x3 Craft] | [Echoes]
        int subTabY = this.topPos + 44;
        this.tabGearCuriosButton = new GothicButton(leftPaneX + 4, subTabY, 60, 13,
                Component.translatable("gui.soulsvault.tab.gear_curios"), btn -> setLeftPaneMode(LeftPaneMode.GEAR_CURIOS));
        this.tabCraftingButton = new GothicButton(leftPaneX + 66, subTabY, 54, 13,
                Component.translatable("gui.soulsvault.tab.crafting"), btn -> setLeftPaneMode(LeftPaneMode.CRAFTING));
        this.tabEchoesButton = new GothicButton(leftPaneX + 122, subTabY, 50, 13,
                Component.translatable("gui.soulsvault.tab.echoes"), btn -> setLeftPaneMode(LeftPaneMode.BUILD_ECHOES));
        addRenderableWidget(this.tabGearCuriosButton);
        addRenderableWidget(this.tabCraftingButton);
        addRenderableWidget(this.tabEchoesButton);

        // 5. Sub-Mode Specific Controls (Cosmetic toggle, Accessory page arrows, Clear Crafting Grid)
        this.cosmeticToggleButton = new GothicButton(
                leftPaneX + 109,
                this.topPos + SoulsVaultMenu.EQUIP_ROW_Y,
                36,
                16,
                Component.translatable("gui.soulsvault.action.cosmetic"),
                btn -> {
                    this.menu.setCosmeticMode(!this.menu.isCosmeticMode());
                    sendMenuButton(3);
                    btn.setHighlighted(this.menu.isCosmeticMode());
                    updateLeftPaneSubControls();
                }
        );
        addRenderableWidget(this.cosmeticToggleButton);

        this.prevAccessoryPageButton = new GothicButton(leftPaneX + 147, this.topPos + SoulsVaultMenu.EQUIP_ROW_Y, 11, 16,
                Component.literal("<"), btn -> {
            this.menu.setAccessoryPage(this.menu.getAccessoryPage() - 1);
            sendMenuButton(4);
            updateLeftPaneSubControls();
        });
        this.nextAccessoryPageButton = new GothicButton(leftPaneX + 159, this.topPos + SoulsVaultMenu.EQUIP_ROW_Y, 11, 16,
                Component.literal(">"), btn -> {
            this.menu.setAccessoryPage(this.menu.getAccessoryPage() + 1);
            sendMenuButton(5);
            updateLeftPaneSubControls();
        });
        addRenderableWidget(this.prevAccessoryPageButton);
        addRenderableWidget(this.nextAccessoryPageButton);

        this.clearCraftingGridButton = new GothicButton(
                this.leftPos + SoulsVaultMenu.CRAFT_GRID_X + 58,
                this.topPos + SoulsVaultMenu.CRAFT_GRID_Y,
                14,
                14,
                Component.literal("×"),
                btn -> Services.PLATFORM.sendToServer(PacketC2SVaultAction.clearCraftingGrid())
        );
        addRenderableWidget(this.clearCraftingGridButton);

        // 6. Left Pane Bottom Action Buttons: Rest & Replenish + Quick Deposit Category
        int bottomBtnY = this.topPos + WINDOW_HEIGHT - 20;
        this.restReplenishButton = new GothicButton(leftPaneX, bottomBtnY, 86, 15,
                Component.translatable("gui.soulsvault.action.rest_replenish"),
                btn -> Services.PLATFORM.sendToServer(PacketC2SVaultAction.restReplenish()));
        addRenderableWidget(this.restReplenishButton);

        GothicButton quickDepositBtn = new GothicButton(leftPaneX + 90, bottomBtnY, 86, 15,
                Component.translatable("gui.soulsvault.action.quick_deposit"),
                btn -> Services.PLATFORM.sendToServer(PacketC2SVaultAction.depositAllCategory(this.gridView.getCategory())));
        addRenderableWidget(quickDepositBtn);

        // 7. Right Pane Virtualized Vault Slot Widgets (Registered as AbstractWidgets for Controller Support!)
        this.vaultSlotWidgets.clear();
        for (int row = 0; row < VAULT_ROWS; row++) {
            for (int col = 0; col < VAULT_COLS; col++) {
                VaultItemSlotWidget slotWidget = new VaultItemSlotWidget(
                        this.leftPos + VAULT_GRID_OFFSET_X + col * VAULT_SLOT_STEP,
                        this.topPos + VAULT_GRID_OFFSET_Y + row * VAULT_SLOT_STEP,
                        VaultItemSlotWidget.SLOT_SIZE,
                        this::onVaultWidgetClicked
                );
                this.vaultSlotWidgets.add(slotWidget);
                addRenderableWidget(slotWidget);
            }
        }

        // 8. Remote access unlock, centred on the seal panel that replaces the grid while the cost is unpaid
        Component unlockLabel = remoteUnlockLabel();
        int unlockWidth = Math.min(SEAL_PANEL_WIDTH - 16, Math.max(120, this.font.width(unlockLabel) + 16));
        this.remoteUnlockButton = new GothicButton(
                this.leftPos + VAULT_GRID_OFFSET_X + (SEAL_PANEL_WIDTH - unlockWidth) / 2,
                this.topPos + SEAL_BUTTON_Y, unlockWidth, 16,
                unlockLabel, btn -> Services.PLATFORM.sendToServer(PacketC2SVaultAction.unlockRemoteAccess()));
        addRenderableWidget(this.remoteUnlockButton);

        this.inspectionPane = new ItemInspectionPane(rightPaneX, this.topPos + 42, RIGHT_PANE_WIDTH, WINDOW_HEIGHT - 48);
        this.inspectionPane.setInspectedKey(this.selectedItemKey);

        ClientVaultCache.registerChangeListener(this.vaultListener);
        updateLeftPaneSubControls();
        updateLockStateControls();
        this.gridView.setSearch(this.searchBox.getValue());
        this.gridView.rebuild();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.searchBox.tick();
        if (this.echoPopover != null) {
            this.echoPopover.nameBox.tick();
        }
        updateLockStateControls();
    }

    @Override
    public void removed() {
        ClientVaultCache.unregisterChangeListener(this.vaultListener);
        super.removed();
    }

    private Component getInspectButtonLabel() {
        return SoulsVaultKeyMappings.keyHint(SoulsVaultKeyMappings.INSPECT).append(Component.translatable(
                this.inspectionMode ? "gui.soulsvault.action.grid_toggle" : "gui.soulsvault.action.inspect_toggle"
        ));
    }

    private static Component remoteUnlockLabel() {
        SoulsVaultConfig.RemoteAccess cost = SoulsVaultConfig.remoteAccess();
        Component price = switch (cost.costType()) {
            case XP_LEVELS -> Component.translatable("gui.soulsvault.remote.cost.levels", cost.costAmount());
            case XP_POINTS -> Component.translatable("gui.soulsvault.remote.cost.points", cost.costAmount());
            case ITEM -> {
                ResourceLocation id = ResourceLocation.tryParse(cost.costItem());
                Component itemName = id != null
                        ? BuiltInRegistries.ITEM.get(id).getDescription()
                        : Component.literal(cost.costItem());
                yield Component.translatable("gui.soulsvault.remote.cost.items", cost.costAmount(), itemName);
            }
            case NONE -> Component.empty();
        };
        return Component.translatable("gui.soulsvault.remote.unlock", price);
    }

    /**
     * Switches the left pane (client and server). Also used by recipe viewer transfers to reveal the 3x3 grid.
     */
    public void setLeftPaneMode(LeftPaneMode mode) {
        this.menu.setLeftPaneMode(mode);
        sendMenuButton(SoulsVaultMenu.leftPaneButtonId(mode));
        this.refillPopover = null;
        this.echoPopover = null;
        updateLeftPaneSubControls();
    }

    private void sendMenuButton(int buttonId) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    private void updateLeftPaneSubControls() {
        LeftPaneMode mode = this.menu.getLeftPaneMode();
        if (this.tabGearCuriosButton != null) {
            this.tabGearCuriosButton.setHighlighted(mode == LeftPaneMode.GEAR_CURIOS);
        }
        if (this.tabCraftingButton != null) {
            this.tabCraftingButton.setHighlighted(mode == LeftPaneMode.CRAFTING);
        }
        if (this.tabEchoesButton != null) {
            this.tabEchoesButton.setHighlighted(mode == LeftPaneMode.BUILD_ECHOES);
        }
        if (this.cosmeticToggleButton != null) {
            this.cosmeticToggleButton.visible = (mode == LeftPaneMode.GEAR_CURIOS) && this.menu.hasCosmeticSlots();
            this.cosmeticToggleButton.setHighlighted(this.menu.isCosmeticMode());
        }
        int totalPages = this.menu.getTotalAccessoryPages();
        if (this.prevAccessoryPageButton != null) {
            this.prevAccessoryPageButton.visible = (mode == LeftPaneMode.GEAR_CURIOS) && totalPages > 1;
            this.prevAccessoryPageButton.active = this.menu.getAccessoryPage() > 0;
        }
        if (this.nextAccessoryPageButton != null) {
            this.nextAccessoryPageButton.visible = (mode == LeftPaneMode.GEAR_CURIOS) && totalPages > 1;
            this.nextAccessoryPageButton.active = this.menu.getAccessoryPage() < totalPages - 1;
        }
        if (this.clearCraftingGridButton != null) {
            this.clearCraftingGridButton.visible = (mode == LeftPaneMode.CRAFTING);
        }
        applyVaultGridVisibility();
    }

    /**
     * The grid is hidden (not just dimmed) while the vault is sealed, so its slots can't swallow clicks meant for
     * the seal panel's unlock button.
     */
    private boolean isVaultGridShown() {
        return !this.inspectionMode && vaultDenialReason() == null;
    }

    private void applyVaultGridVisibility() {
        boolean shown = isVaultGridShown();
        for (VaultItemSlotWidget vaultSlot : this.vaultSlotWidgets) {
            vaultSlot.visible = shown;
            vaultSlot.active = shown;
        }
    }

    /**
     * Enables/disables controls that depend on the server-synced lock state (combat, remote cost, bonfire rules).
     */
    private void updateLockStateControls() {
        if (this.restReplenishButton != null) {
            this.restReplenishButton.active = restDenialReason() == null;
        }
        applyVaultGridVisibility();
        if (this.remoteUnlockButton != null) {
            this.remoteUnlockButton.visible = !this.inspectionMode && this.menu.needsRemoteUnlock() && !this.menu.isCombatLocked();
        }
        if (this.partyButton != null) {
            this.partyButton.setHighlighted(ClientPartyCache.getSnapshot() != null || !ClientPartyCache.getInvites().isEmpty());
        }
    }

    @Nullable
    private Component vaultDenialReason() {
        if (this.menu.isCombatLocked()) {
            return Component.translatable("gui.soulsvault.notice.combat_locked");
        }
        if (this.menu.needsRemoteUnlock()) {
            return Component.translatable("gui.soulsvault.notice.remote_locked");
        }
        if (!this.menu.isVaultAccessAllowed()) {
            return Component.translatable("gui.soulsvault.notice.rest_point_required");
        }
        return null;
    }

    /**
     * What lifts the seal, shown under {@link #vaultDenialReason()} on the seal panel.
     */
    private Component vaultSealHint() {
        if (this.menu.isCombatLocked()) {
            return Component.translatable("gui.soulsvault.sealed.hint.combat");
        }
        if (this.menu.needsRemoteUnlock()) {
            return Component.translatable("gui.soulsvault.sealed.hint.remote");
        }
        return Component.translatable("gui.soulsvault.sealed.hint.rest_point");
    }

    @Nullable
    private Component restDenialReason() {
        Component vault = vaultDenialReason();
        if (vault != null) {
            return vault;
        }
        if (SoulsVaultConfig.restPoints().replenishRequiresRestPoint() && !this.menu.getGrants().replenish()) {
            return Component.translatable("gui.soulsvault.notice.rest_point_required");
        }
        return null;
    }

    @Nullable
    private Component echoSwapDenialReason() {
        Component vault = vaultDenialReason();
        if (vault != null) {
            return vault;
        }
        if (SoulsVaultConfig.restPoints().echoSwapRequiresRestPoint() && !this.menu.getGrants().echoSwap()) {
            return Component.translatable("gui.soulsvault.notice.echo_rest_point_required");
        }
        return null;
    }

    private void selectCategory(VaultCategory category) {
        this.vaultScrollRow = 0;
        this.refillPopover = null;
        for (CategoryTabButton btn : this.categoryTabButtons) {
            btn.setSelected(btn.getCategory() == category);
        }
        this.gridView.setCategory(category);
        SoulsVaultClientConfig.rememberTab(category, this.gridView.getSortMode());
    }

    private void toggleInspectionMode() {
        this.inspectionMode = !this.inspectionMode;
        this.refillPopover = null;
        if (this.inspectToggleButton != null) {
            this.inspectToggleButton.setMessage(getInspectButtonLabel());
            this.inspectToggleButton.setHighlighted(this.inspectionMode);
        }
        if (this.inspectionPane != null) {
            this.inspectionPane.setInspectedKey(this.selectedItemKey);
        }
        updateLeftPaneSubControls();
        updateLockStateControls();
    }

    /**
     * Full refresh (used when the server opens/re-syncs the screen); deltas update the view incrementally.
     */
    public void refreshAllSlots() {
        this.gridView.rebuild();
    }

    private void bindVaultWidgets() {
        Map<VaultCategory, Integer> counts = this.gridView.getCategoryCounts();
        for (CategoryTabButton tabBtn : this.categoryTabButtons) {
            tabBtn.setEntryCount(counts.getOrDefault(tabBtn.getCategory(), 0));
        }

        List<VaultEntry> view = this.gridView.getView();
        int totalVaultRows = Math.max(1, (view.size() + VAULT_COLS - 1) / VAULT_COLS);
        int maxVaultScroll = Math.max(0, totalVaultRows - VAULT_ROWS);
        this.vaultScrollRow = Mth.clamp(this.vaultScrollRow, 0, maxVaultScroll);

        int startVaultIndex = this.vaultScrollRow * VAULT_COLS;
        for (int i = 0; i < this.vaultSlotWidgets.size(); i++) {
            int entryIdx = startVaultIndex + i;
            VaultItemSlotWidget widget = this.vaultSlotWidgets.get(i);
            if (entryIdx < view.size()) {
                VaultEntry entry = view.get(entryIdx);
                widget.bindVaultEntry(entry, entry.getKey().equals(this.selectedItemKey));
            } else {
                widget.bindVaultEntry(null, false);
            }
        }

        if (this.selectedItemKey.isEmpty() && !view.isEmpty()) {
            this.selectedItemKey = view.get(0).getKey();
            if (this.inspectionPane != null) {
                this.inspectionPane.setInspectedKey(this.selectedItemKey);
            }
        }
    }

    private boolean isMouseOverVaultGrid(double mouseX, double mouseY) {
        int gridX = this.leftPos + VAULT_GRID_OFFSET_X;
        int gridY = this.topPos + VAULT_GRID_OFFSET_Y;
        return !this.inspectionMode
                && mouseX >= gridX && mouseX < gridX + VAULT_COLS * VAULT_SLOT_STEP
                && mouseY >= gridY && mouseY < gridY + VAULT_ROWS * VAULT_SLOT_STEP;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Outer gothic charcoal window background + weathered bronze double-frame
        graphics.fill(this.leftPos, this.topPos, this.leftPos + WINDOW_WIDTH, this.topPos + WINDOW_HEIGHT, COLOR_CHARCOAL_BG);
        VaultItemSlotWidget.drawBorder(graphics, this.leftPos, this.topPos, WINDOW_WIDTH, WINDOW_HEIGHT, COLOR_BRONZE);
        VaultItemSlotWidget.drawBorder(graphics, this.leftPos + 2, this.topPos + 2, WINDOW_WIDTH - 4, WINDOW_HEIGHT - 4, COLOR_BRONZE_MUTED);

        int leftPaneX = this.leftPos + LEFT_PANE_OFFSET_X;
        int rightPaneX = this.leftPos + RIGHT_PANE_OFFSET_X;
        int paneTopY = this.topPos + 42;
        int leftPaneHeight = WINDOW_HEIGHT - 65;

        // Window title (with the party name when this is a shared party Casket)
        VaultMeta meta = ClientVaultCache.getMeta();
        Component windowTitle = meta.shared()
                ? Component.translatable("gui.soulsvault.title.shared", this.title, meta.partyName())
                : this.title;
        int titleMaxWidth = this.partyButton.getX() - leftPaneX - 6;
        graphics.drawString(this.font, this.font.plainSubstrByWidth(windowTitle.getString(), titleMaxWidth),
                leftPaneX, this.topPos + 7, COLOR_GOLD, true);

        // Custom Gothic Search Box Frame + Clipped Hint
        int searchY = this.topPos + 22;
        boolean searchFocused = this.searchBox != null && this.searchBox.isFocused();
        graphics.fill(rightPaneX, searchY, rightPaneX + SEARCH_BOX_WIDTH, searchY + SEARCH_BOX_HEIGHT, 0xF00D0B0E);
        VaultItemSlotWidget.drawBorder(graphics, rightPaneX, searchY, SEARCH_BOX_WIDTH, SEARCH_BOX_HEIGHT,
                searchFocused ? COLOR_GOLD : COLOR_BRONZE);
        if (this.searchBox != null && this.searchBox.getValue().isEmpty() && !searchFocused) {
            String hintText = this.font.plainSubstrByWidth(
                    Component.translatable("gui.soulsvault.search.hint").getString(),
                    SEARCH_BOX_WIDTH - 10
            );
            graphics.drawString(this.font, hintText, rightPaneX + 5, searchY + 4, 0xFF6B6257, false);
        }

        // Left Pane Recessed Stone Panel
        graphics.fill(leftPaneX, paneTopY, leftPaneX + LEFT_PANE_WIDTH, paneTopY + leftPaneHeight, COLOR_PANE_BG);
        VaultItemSlotWidget.drawBorder(graphics, leftPaneX, paneTopY, LEFT_PANE_WIDTH, leftPaneHeight, COLOR_BRONZE_MUTED);

        // Render active Left-Pane Top Section (Gear & Curios | 3x3 Crafting | Build Echoes)
        LeftPaneMode leftMode = this.menu.getLeftPaneMode();
        if (leftMode == LeftPaneMode.GEAR_CURIOS) {
            renderGearAndCuriosBg(graphics);
        } else if (leftMode == LeftPaneMode.CRAFTING) {
            renderCraftingGridBg(graphics);
        } else if (leftMode == LeftPaneMode.BUILD_ECHOES) {
            renderBuildEchoesDrawer(graphics, leftPaneX, this.topPos + ECHO_DRAWER_Y, LEFT_PANE_WIDTH, mouseX, mouseY);
        }

        // Divider above 36-Slot Player Inventory
        graphics.fill(leftPaneX + 6, this.topPos + 126, leftPaneX + LEFT_PANE_WIDTH - 6, this.topPos + 127, COLOR_BRONZE_MUTED);
        drawScaled(graphics, Component.translatable("gui.soulsvault.subtitle.inventory"), leftPaneX + 7.0F, this.topPos + 129.0F, 0.85F, COLOR_TEXT_SUB);

        // Render Gothic Slot Frames & Category/Search Highlights for all active Menu Slots
        renderMenuSlotFrames(graphics);

        // Right Pane: either Detailed ItemInspectionPane or Virtualized Vault Grid Background
        if (this.inspectionMode) {
            this.inspectionPane.setInspectedKey(this.selectedItemKey);
            this.inspectionPane.render(graphics, mouseX, mouseY, partialTick);
        } else {
            renderVaultPaneBg(graphics, rightPaneX, paneTopY, meta);
        }
    }

    private void renderVaultPaneBg(GuiGraphics graphics, int rightPaneX, int paneTopY, VaultMeta meta) {
        int vaultPaneH = WINDOW_HEIGHT - 48;
        graphics.fill(rightPaneX, paneTopY, rightPaneX + RIGHT_PANE_WIDTH, paneTopY + vaultPaneH, COLOR_PANE_BG);
        VaultItemSlotWidget.drawBorder(graphics, rightPaneX, paneTopY, RIGHT_PANE_WIDTH, vaultPaneH, COLOR_BRONZE_MUTED);

        List<VaultEntry> view = this.gridView.getView();
        String headerLeft = this.gridView.getCategory().getDisplayName().getString() + " (" + view.size() + ")";
        graphics.drawString(this.font, headerLeft, rightPaneX + 18, paneTopY + 5, COLOR_GOLD, false);

        Component denial = vaultDenialReason();
        if (denial != null) {
            renderSealPanel(graphics, denial);
        }

        // Vault scrollbar track
        int totalVaultRows = Math.max(1, (view.size() + VAULT_COLS - 1) / VAULT_COLS);
        if (denial == null && totalVaultRows > VAULT_ROWS) {
            int trackX = rightPaneX + RIGHT_PANE_WIDTH - 8;
            int trackY = this.topPos + VAULT_GRID_OFFSET_Y;
            int trackH = VAULT_ROWS * VAULT_SLOT_STEP - 2;
            graphics.fill(trackX, trackY, trackX + 2, trackY + trackH, 0x802A241C);
            int maxScroll = totalVaultRows - VAULT_ROWS;
            int thumbH = Math.max(14, (trackH * VAULT_ROWS) / totalVaultRows);
            int thumbY = trackY + ((trackH - thumbH) * this.vaultScrollRow) / maxScroll;
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, COLOR_GOLD);
        }

        // Footer: capacity, or the private Casket set aside while a shared party Casket is in use
        int footerY = this.topPos + VAULT_GRID_OFFSET_Y + VAULT_ROWS * VAULT_SLOT_STEP + 4;
        Component footer;
        if (meta.shared() && meta.setAsideItems() > 0L) {
            footer = Component.translatable("gui.soulsvault.footer.set_aside",
                    VaultItemSlotWidget.formatCompactCount(meta.setAsideItems()));
        } else if (meta.maxUniqueEntries() > 0) {
            footer = Component.translatable("gui.soulsvault.footer.capacity_kinds",
                    VaultItemSlotWidget.formatCompactCount(meta.maxPerItem()), meta.uniqueEntries(), meta.maxUniqueEntries());
        } else {
            footer = Component.translatable("gui.soulsvault.footer.capacity",
                    VaultItemSlotWidget.formatCompactCount(meta.maxPerItem()));
        }
        drawScaled(graphics, footer, rightPaneX + 18.0F, footerY, 0.7F, COLOR_TEXT_SUB);
    }

    /**
     * Stands in for the grid while the vault is sealed: a padlock, why it is sealed, what lifts the seal and (for an
     * unpaid remote cost) the unlock button centred underneath.
     */
    private void renderSealPanel(GuiGraphics graphics, Component reason) {
        int x = this.leftPos + VAULT_GRID_OFFSET_X;
        int y = this.topPos + VAULT_GRID_OFFSET_Y;
        int centerX = x + SEAL_PANEL_WIDTH / 2;
        graphics.fill(x, y, x + SEAL_PANEL_WIDTH, y + SEAL_PANEL_HEIGHT, 0xF0080709);
        VaultItemSlotWidget.drawBorder(graphics, x, y, SEAL_PANEL_WIDTH, SEAL_PANEL_HEIGHT, COLOR_BRONZE_MUTED);
        VaultItemSlotWidget.drawBorder(graphics, x + 3, y + 3, SEAL_PANEL_WIDTH - 6, SEAL_PANEL_HEIGHT - 6, 0xFF2A241C);

        // Padlock sigil
        int lockY = y + 18;
        graphics.fill(centerX - 5, lockY, centerX + 5, lockY + 2, COLOR_BRONZE);
        graphics.fill(centerX - 5, lockY, centerX - 3, lockY + 8, COLOR_BRONZE);
        graphics.fill(centerX + 3, lockY, centerX + 5, lockY + 8, COLOR_BRONZE);
        graphics.fill(centerX - 8, lockY + 7, centerX + 8, lockY + 19, COLOR_BRONZE);
        graphics.fill(centerX - 7, lockY + 8, centerX + 7, lockY + 18, 0xFF3A3020);
        graphics.fill(centerX - 1, lockY + 11, centerX + 1, lockY + 15, COLOR_GOLD);

        int textY = lockY + 27;
        Component title = Component.translatable("gui.soulsvault.sealed.title");
        graphics.drawString(this.font, title, centerX - this.font.width(title) / 2, textY, COLOR_GOLD, true);
        textY += 14;
        int wrapWidth = SEAL_PANEL_WIDTH - 20;
        for (FormattedCharSequence line : this.font.split(reason, wrapWidth)) {
            graphics.drawString(this.font, line, centerX - this.font.width(line) / 2, textY, COLOR_WARNING, false);
            textY += 10;
        }
        textY += 2;
        for (FormattedCharSequence line : this.font.split(vaultSealHint(), wrapWidth)) {
            if (textY + 9 > this.topPos + SEAL_BUTTON_Y - 2) {
                break;
            }
            graphics.drawString(this.font, line, centerX - this.font.width(line) / 2, textY, COLOR_TEXT_SUB, false);
            textY += 10;
        }
    }

    private void drawScaled(GuiGraphics graphics, Component text, float x, float y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(this.font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void renderGearAndCuriosBg(GuiGraphics graphics) {
        int leftPaneX = this.leftPos + LEFT_PANE_OFFSET_X;
        List<SoulsVaultMenu.AccessoryMenuSlot> accSlots = this.menu.getVisibleAccessorySlots();
        if (accSlots.isEmpty()) {
            String msg = this.font.plainSubstrByWidth(
                    Component.translatable("gui.soulsvault.notice.no_curios").getString(),
                    LEFT_PANE_WIDTH - 14
            );
            graphics.drawString(this.font, msg, leftPaneX + 7, this.topPos + 96, 0xFF6B6257, false);
        }

        // Equip Load meter under the equipment row
        EquipLoad.Result load = ClientVaultCache.getEquipLoad();
        if (SoulsVaultConfig.equipLoad().enabled() && load.capacity() > 0.0D) {
            int barX = this.leftPos + SoulsVaultMenu.INV_START_X;
            int barY = this.topPos + EQUIP_LOAD_BAR_Y;
            graphics.fill(barX, barY, barX + EQUIP_LOAD_BAR_WIDTH, barY + 3, 0xFF1B171E);
            int filled = (int) Math.round(Math.min(1.0D, load.ratio()) * EQUIP_LOAD_BAR_WIDTH);
            graphics.fill(barX, barY, barX + filled, barY + 3, equipLoadColor(load.tier()));
            for (double threshold : new double[]{0.3D, 0.7D}) {
                int tickX = barX + (int) Math.round(threshold * EQUIP_LOAD_BAR_WIDTH);
                graphics.fill(tickX, barY - 1, tickX + 1, barY + 4, COLOR_BRONZE_MUTED);
            }
        }
    }

    private static int equipLoadColor(EquipLoad.Tier tier) {
        return switch (tier) {
            case LIGHT -> 0xFF7FB069;
            case MEDIUM -> COLOR_GOLD;
            case HEAVY -> 0xFFE0904F;
            case OVERLOADED -> 0xFFE05050;
        };
    }

    private void renderCraftingGridBg(GuiGraphics graphics) {
        int arrowX = this.leftPos + SoulsVaultMenu.CRAFT_GRID_X + 60;
        int arrowY = this.topPos + SoulsVaultMenu.CRAFT_GRID_Y + 21;
        graphics.drawString(this.font, "→", arrowX, arrowY, COLOR_GOLD, true);

        // Ornate 24x24 frame around the Crafting Result Slot
        int resBoxX = this.leftPos + SoulsVaultMenu.CRAFT_RESULT_X - 4;
        int resBoxY = this.topPos + SoulsVaultMenu.CRAFT_RESULT_Y - 4;
        graphics.fill(resBoxX, resBoxY, resBoxX + 24, resBoxY + 24, 0xF01C1612);
        VaultItemSlotWidget.drawBorder(graphics, resBoxX, resBoxY, 24, 24, COLOR_GOLD);

        drawScaled(graphics, Component.translatable("gui.soulsvault.crafting.auto_refill"),
                this.leftPos + SoulsVaultMenu.CRAFT_GRID_X + 58.0F, this.topPos + SoulsVaultMenu.CRAFT_GRID_Y + 42.0F, 0.68F, COLOR_TEXT_SUB);
    }

    private void renderMenuSlotFrames(GuiGraphics graphics) {
        boolean hasFilter = this.gridView.hasFilter();
        Inventory playerInventory = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getInventory() : null;

        for (Slot slot : this.menu.slots) {
            if (!slot.isActive()) {
                continue;
            }
            int sx = this.leftPos + slot.x - 1;
            int sy = this.topPos + slot.y - 1;

            int borderColor = 0xFF3E3526;
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                ItemKey key = ItemKey.fromStack(stack);
                if (key.equals(this.selectedItemKey)) {
                    borderColor = COLOR_GOLD;
                } else if (hasFilter && slot.container == playerInventory && this.gridView.matches(key)) {
                    borderColor = COLOR_GOLD;
                }
            }

            graphics.fill(sx, sy, sx + 18, sy + 18, 0xE60D0B0E);
            VaultItemSlotWidget.drawBorder(graphics, sx, sy, 18, 18, borderColor);

            // Render empty slot pixel-art silhouette if empty
            if (stack.isEmpty()) {
                ResourceLocation emptyIcon = null;
                if (slot instanceof SoulsVaultMenu.EquipmentMenuSlot eqSlot) {
                    emptyIcon = eqSlot.getEmptyIcon();
                } else if (slot instanceof SoulsVaultMenu.OffhandMenuSlot offSlot) {
                    emptyIcon = offSlot.getEmptyIcon();
                } else if (slot instanceof SoulsVaultMenu.AccessoryMenuSlot accSlot) {
                    emptyIcon = accSlot.getEmptyIcon();
                }
                if (emptyIcon != null) {
                    graphics.blit(emptyIcon, this.leftPos + slot.x, this.topPos + slot.y, 0, 0, 16, 16, 16, 16);
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Labels are rendered inside renderBg with exact layout coordinates
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Like AE2 / Refined Storage: never re-sort the grid under the cursor
        this.gridView.setPaused(isMouseOverVaultGrid(mouseX, mouseY));
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        if (this.echoPopover != null) {
            this.echoPopover.render(graphics, this.font, mouseX, mouseY, partialTick);
        } else if (this.refillPopover != null) {
            this.refillPopover.render(graphics, this.font, mouseX, mouseY);
        } else {
            this.renderTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        // 1. Standard Container Slot tooltips (including empty Curio/Accessory slot names)
        if (this.menu.getCarried().isEmpty() && this.hoveredSlot != null) {
            if (this.hoveredSlot.hasItem()) {
                ItemStack stack = this.hoveredSlot.getItem();
                graphics.renderTooltip(this.font, this.getTooltipFromContainerItem(stack), stack.getTooltipImage(), mouseX, mouseY);
                return;
            } else if (this.hoveredSlot instanceof SoulsVaultMenu.AccessoryMenuSlot accSlot) {
                graphics.renderTooltip(this.font, accSlot.getAccess().getDisplayName(), mouseX, mouseY);
                return;
            }
        }

        // 2. [?] Controls Legend button tooltip
        if (this.helpLegendButton != null && this.helpLegendButton.isMouseOver(mouseX, mouseY)) {
            graphics.renderComponentTooltip(this.font, buildHelpLines(), mouseX, mouseY);
            return;
        }

        // 3. Disabled action explanations
        if (this.restReplenishButton != null && this.restReplenishButton.isMouseOver(mouseX, mouseY)) {
            Component reason = restDenialReason();
            if (reason != null) {
                graphics.renderTooltip(this.font, reason.copy().withStyle(ChatFormatting.RED), mouseX, mouseY);
                return;
            }
        }

        // 4. Equip Load meter
        if (this.menu.getLeftPaneMode() == LeftPaneMode.GEAR_CURIOS && SoulsVaultConfig.equipLoad().enabled()) {
            int barX = this.leftPos + SoulsVaultMenu.INV_START_X;
            int barY = this.topPos + EQUIP_LOAD_BAR_Y;
            if (mouseX >= barX && mouseX < barX + EQUIP_LOAD_BAR_WIDTH && mouseY >= barY - 2 && mouseY < barY + 5) {
                EquipLoad.Result load = ClientVaultCache.getEquipLoad();
                graphics.renderComponentTooltip(this.font, List.of(
                        Component.translatable("gui.soulsvault.equip_load.title").withStyle(ChatFormatting.GOLD),
                        Component.translatable("gui.soulsvault.equip_load.value",
                                String.format(Locale.ROOT, "%.1f", load.load()),
                                String.format(Locale.ROOT, "%.1f", load.capacity())),
                        Component.translatable("gui.soulsvault.equip_load.tier."
                                + load.tier().name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GRAY)
                ), mouseX, mouseY);
                return;
            }
        }

        // 5. Category tab hover tooltips
        for (CategoryTabButton tab : this.categoryTabButtons) {
            if (tab.isMouseOver(mouseX, mouseY)) {
                graphics.renderTooltip(this.font, tab.getCategory().getDisplayName(), mouseX, mouseY);
                return;
            }
        }

        // 6. Build Echo rows
        if (this.menu.getLeftPaneMode() == LeftPaneMode.BUILD_ECHOES && renderEchoTooltip(graphics, mouseX, mouseY)) {
            return;
        }

        // 7. Vault slot hover tooltips (only on mouse hover, never on sticky focus, and without wide footer text)
        if (!this.inspectionMode) {
            for (VaultItemSlotWidget slot : this.vaultSlotWidgets) {
                if (!slot.isEmpty() && slot.isMouseOver(mouseX, mouseY)) {
                    VaultEntry entry = slot.getEntry();
                    ItemStack stack = slot.getDisplayStack();
                    List<Component> tooltip = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), stack));
                    if (entry != null) {
                        tooltip.add(Component.translatable("gui.soulsvault.inspect.stored_count", entry.getCount())
                                .withStyle(ChatFormatting.GOLD));
                        if (entry.isAutoRefill() || entry.isOverflowDeposit()) {
                            tooltip.add(Component.translatable("gui.soulsvault.inspect.refill_target", entry.getRefillTarget())
                                    .withStyle(ChatFormatting.AQUA));
                        }
                    }
                    graphics.renderTooltip(this.font, tooltip, stack.getTooltipImage(), mouseX, mouseY);
                    return;
                }
            }
        }
    }

    private List<Component> buildHelpLines() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.soulsvault.help.title").withStyle(ChatFormatting.GOLD));
        lines.add(helpLine("lclick", ChatFormatting.WHITE));
        lines.add(helpLine("rclick", ChatFormatting.WHITE));
        lines.add(helpLine("shift_lclick", ChatFormatting.WHITE));
        lines.add(helpLine("mclick", ChatFormatting.YELLOW));
        lines.add(helpLine("shift_rclick", ChatFormatting.AQUA));
        lines.add(helpLine("search", ChatFormatting.WHITE));
        lines.add(Component.translatable("gui.soulsvault.help.hotkeys").withStyle(ChatFormatting.GRAY)
                .append(SoulsVaultKeyMappings.keyHint(SoulsVaultKeyMappings.INSPECT)
                        .append(Component.translatable("gui.soulsvault.help.hotkey.inspect"))
                        .append("  ")
                        .append(SoulsVaultKeyMappings.keyHint(SoulsVaultKeyMappings.CRAFTING))
                        .append(Component.translatable("gui.soulsvault.help.hotkey.craft"))
                        .append("  ")
                        .append(SoulsVaultKeyMappings.keyHint(SoulsVaultKeyMappings.BUILD_ECHOES))
                        .append(Component.translatable("gui.soulsvault.help.hotkey.echoes"))
                        .withStyle(ChatFormatting.GOLD)));
        lines.add(Component.translatable("gui.soulsvault.help.rebind").withStyle(ChatFormatting.DARK_GRAY));
        return lines;
    }

    private static Component helpLine(String key, ChatFormatting descriptionColor) {
        return Component.literal("• ").withStyle(ChatFormatting.GRAY)
                .append(Component.translatable("gui.soulsvault.help." + key).withStyle(ChatFormatting.GRAY))
                .append(" ")
                .append(Component.translatable("gui.soulsvault.help." + key + ".desc").withStyle(descriptionColor));
    }

    // ------------------------------------------------------------------------------------------------ build echoes

    private int echoSlotCount() {
        return Math.max(1, ClientVaultCache.getVaultData().getEchoSlots());
    }

    @Nullable
    private BuildEcho echoAt(int index) {
        List<BuildEcho> echoes = ClientVaultCache.getVaultData().getBuildEchoes();
        return index < echoes.size() ? echoes.get(index) : null;
    }

    private void renderBuildEchoesDrawer(GuiGraphics graphics, int paneX, int startY, int paneW, int mouseX, int mouseY) {
        int slots = echoSlotCount();
        int maxScroll = Math.max(0, slots - ECHO_ROWS_VISIBLE);
        this.buildEchoScroll = Mth.clamp(this.buildEchoScroll, 0, maxScroll);
        boolean swapAllowed = echoSwapDenialReason() == null;

        int cardX = paneX + 5;
        int cardW = paneW - 10;

        for (int v = 0; v < ECHO_ROWS_VISIBLE; v++) {
            int i = this.buildEchoScroll + v;
            if (i >= slots) {
                break;
            }
            int ry = startY + v * ECHO_ROW_HEIGHT;
            BuildEcho echo = echoAt(i);

            graphics.fill(cardX, ry, cardX + cardW, ry + ECHO_ROW_HEIGHT - 2, 0xF016131A);
            VaultItemSlotWidget.drawBorder(graphics, cardX, ry, cardW, ECHO_ROW_HEIGHT - 2, COLOR_BRONZE_MUTED);

            graphics.drawString(this.font, "#" + (i + 1), cardX + 3, ry + 6, COLOR_GOLD, false);

            int contentX = cardX + 18;
            String name = echo != null ? echo.getName() : BuildEcho.defaultName(i);
            graphics.pose().pushPose();
            graphics.pose().translate(contentX, ry + 2.0F, 0.0F);
            graphics.pose().scale(0.62F, 0.62F, 1.0F);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(name, 120), 0, 0, COLOR_TEXT_MAIN, false);
            graphics.pose().popPose();

            if (echo != null && !echo.isEmpty()) {
                List<ItemKey> previewKeys = new ArrayList<>(6);
                previewKeys.add(echo.getMainHand());
                previewKeys.add(echo.getOffHand());
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    previewKeys.add(echo.getArmor(slot));
                }
                for (int k = 0; k < previewKeys.size(); k++) {
                    ItemKey ik = previewKeys.get(k);
                    int ix = contentX + k * 12;
                    int iy = ry + 8;
                    graphics.fill(ix, iy, ix + 11, iy + 11, 0xFF0D0B0E);
                    if (!ik.isEmpty()) {
                        graphics.pose().pushPose();
                        graphics.pose().translate(ix, iy, 100.0F);
                        graphics.pose().scale(0.68F, 0.68F, 1.0F);
                        graphics.renderItem(ik.toSingleStack(), 0, 0);
                        graphics.pose().popPose();
                    }
                }
            } else {
                drawScaled(graphics, Component.translatable("gui.soulsvault.echo.empty"), contentX, ry + 10.0F, 0.7F, 0xFF685F54);
            }

            int saveBtnX = cardX + cardW - 64;
            int loadBtnX = cardX + cardW - 32;
            int btnY = ry + 3;
            drawEchoButton(graphics, saveBtnX, btnY, Component.translatable("gui.soulsvault.action.echo_save"), true, mouseX, mouseY);
            drawEchoButton(graphics, loadBtnX, btnY, Component.translatable("gui.soulsvault.action.echo_load"),
                    swapAllowed && echo != null && !echo.isEmpty(), mouseX, mouseY);
        }

        if (slots > ECHO_ROWS_VISIBLE) {
            drawScaled(graphics, Component.literal((this.buildEchoScroll + 1) + "-"
                            + Math.min(slots, this.buildEchoScroll + ECHO_ROWS_VISIBLE) + " / " + slots),
                    paneX + paneW - 34.0F, startY + ECHO_ROWS_VISIBLE * ECHO_ROW_HEIGHT - 1.0F, 0.6F, COLOR_TEXT_SUB);
        }
    }

    private void drawEchoButton(GuiGraphics graphics, int x, int y, Component label, boolean enabled, int mouseX, int mouseY) {
        int w = 30;
        int h = 13;
        boolean hovered = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        graphics.fill(x, y, x + w, y + h, hovered ? 0xFF2D2418 : 0xFF1B171E);
        VaultItemSlotWidget.drawBorder(graphics, x, y, w, h, hovered ? COLOR_GOLD : (enabled ? COLOR_BRONZE : COLOR_BRONZE_MUTED));
        String text = label.getString();
        graphics.pose().pushPose();
        graphics.pose().translate(x + (w - this.font.width(text) * 0.75F) / 2.0F, y + 3.5F, 200.0F);
        graphics.pose().scale(0.75F, 0.75F, 1.0F);
        graphics.drawString(this.font, text, 0, 0, !enabled ? 0xFF70685E : (hovered ? COLOR_GOLD : COLOR_TEXT_MAIN), false);
        graphics.pose().popPose();
    }

    private boolean renderEchoTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int cardX = this.leftPos + LEFT_PANE_OFFSET_X + 5;
        int cardW = LEFT_PANE_WIDTH - 10;
        int startY = this.topPos + ECHO_DRAWER_Y;
        for (int v = 0; v < ECHO_ROWS_VISIBLE; v++) {
            int i = this.buildEchoScroll + v;
            if (i >= echoSlotCount()) {
                break;
            }
            int ry = startY + v * ECHO_ROW_HEIGHT;
            if (mouseX < cardX || mouseX >= cardX + cardW || mouseY < ry || mouseY >= ry + ECHO_ROW_HEIGHT - 2) {
                continue;
            }
            int loadBtnX = cardX + cardW - 32;
            Component swapReason = echoSwapDenialReason();
            if (swapReason != null && mouseX >= loadBtnX && mouseX < loadBtnX + 30) {
                graphics.renderTooltip(this.font, swapReason.copy().withStyle(ChatFormatting.RED), mouseX, mouseY);
                return true;
            }
            BuildEcho echo = echoAt(i);
            graphics.renderComponentTooltip(this.font, List.of(
                    Component.literal(echo != null ? echo.getName() : BuildEcho.defaultName(i)).withStyle(ChatFormatting.GOLD),
                    Component.translatable("gui.soulsvault.echo.options_hint").withStyle(ChatFormatting.DARK_GRAY)
            ), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private boolean handleBuildEchoesDrawerClick(double mouseX, double mouseY, int button) {
        int cardX = this.leftPos + LEFT_PANE_OFFSET_X + 5;
        int cardW = LEFT_PANE_WIDTH - 10;
        int saveBtnX = cardX + cardW - 64;
        int loadBtnX = cardX + cardW - 32;
        int btnW = 30;
        int btnH = 13;

        for (int v = 0; v < ECHO_ROWS_VISIBLE; v++) {
            int i = this.buildEchoScroll + v;
            if (i >= echoSlotCount()) {
                break;
            }
            int ry = this.topPos + ECHO_DRAWER_Y + v * ECHO_ROW_HEIGHT;
            int btnY = ry + 3;
            boolean inRow = mouseX >= cardX && mouseX < cardX + cardW && mouseY >= ry && mouseY < ry + ECHO_ROW_HEIGHT - 2;
            if (!inRow) {
                continue;
            }
            if (button == 1) {
                this.echoPopover = new EchoOptionsPopover(i, cardX, ry + ECHO_ROW_HEIGHT);
                return true;
            }
            if (button != 0) {
                return false;
            }
            if (mouseX >= saveBtnX && mouseX < saveBtnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                playClick();
                Services.PLATFORM.sendToServer(PacketC2SVaultAction.saveBuildEcho(i));
                return true;
            }
            if (mouseX >= loadBtnX && mouseX < loadBtnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                BuildEcho echo = echoAt(i);
                if (echoSwapDenialReason() == null && echo != null && !echo.isEmpty()) {
                    playClick();
                    Services.PLATFORM.sendToServer(PacketC2SVaultAction.loadBuildEcho(i));
                }
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------------------ input

    private static void playClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. Active popovers capture clicks
        if (this.echoPopover != null) {
            if (this.echoPopover.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            this.echoPopover = null;
        }
        if (this.refillPopover != null) {
            if (this.refillPopover.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            this.refillPopover = null;
        }

        // Rebindable shortcuts that the player bound to a mouse button (e.g. Inspect on Mouse 4)
        if (handleShortcut(mapping -> SoulsVaultKeyMappings.matchesMouse(mapping, button))) {
            return true;
        }

        // 2. Search box focus
        int rightPaneX = this.leftPos + RIGHT_PANE_OFFSET_X;
        int searchY = this.topPos + 22;
        if (this.searchBox != null
                && mouseX >= rightPaneX && mouseX < rightPaneX + SEARCH_BOX_WIDTH
                && mouseY >= searchY && mouseY < searchY + SEARCH_BOX_HEIGHT) {
            this.searchBox.setFocused(true);
            this.setFocused(this.searchBox);
            if (button == 1) {
                this.searchBox.setValue("");
            }
            return true;
        } else if (this.searchBox != null) {
            this.searchBox.setFocused(false);
        }

        // 3. Handle Inspection Pane clicks when active
        if (this.inspectionMode && this.inspectionPane != null) {
            if (this.inspectionPane.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        // 4. If carrying an item on the cursor and clicking inside the Right Vault Pane, deposit carried item!
        if (!this.inspectionMode
                && !this.menu.getCarried().isEmpty()
                && mouseX >= rightPaneX && mouseX < rightPaneX + RIGHT_PANE_WIDTH
                && mouseY >= this.topPos + 42 && mouseY < this.topPos + WINDOW_HEIGHT - 6) {
            if (button == 0 || button == 1) {
                playClick();
                Services.PLATFORM.sendToServer(PacketC2SVaultAction.depositCarried(button == 1));
                return true;
            }
        }

        // 5. Handle Build Echoes Drawer clicks when active
        if (this.menu.getLeftPaneMode() == LeftPaneMode.BUILD_ECHOES) {
            if (handleBuildEchoesDrawerClick(mouseX, mouseY, button)) {
                return true;
            }
        }

        // 6. Update selected item for Inspection when clicking any container slot
        Slot clickedSlot = this.findSlot(mouseX, mouseY);
        if (clickedSlot != null && clickedSlot.hasItem()) {
            this.selectedItemKey = ItemKey.fromStack(clickedSlot.getItem());
            if (this.inspectionPane != null) {
                this.inspectionPane.setInspectedKey(this.selectedItemKey);
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Nullable
    private Slot findSlot(double mouseX, double mouseY) {
        for (Slot slot : this.menu.slots) {
            if (slot.isActive() && this.isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    /**
     * Returns the visible Vault grid widget under the mouse, if any. Vault entries are widgets rather than
     * container slots, so recipe viewers (JEI / REI / EMI) use this for recipe / usage / bookmark lookups.
     */
    @Nullable
    public VaultItemSlotWidget getVaultSlotAt(double mouseX, double mouseY) {
        if (this.inspectionMode) {
            return null;
        }
        for (VaultItemSlotWidget slot : this.vaultSlotWidgets) {
            if (slot.visible && !slot.isEmpty() && slot.isMouseOver(mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

    public boolean isCraftingPaneActive() {
        return this.menu.getLeftPaneMode() == LeftPaneMode.CRAFTING;
    }

    private void onVaultWidgetClicked(VaultItemSlotWidget widget, int button) {
        // If holding a cursor stack, deposit it into the Vault
        if (!this.menu.getCarried().isEmpty() && (button == 0 || button == 1)) {
            playClick();
            Services.PLATFORM.sendToServer(PacketC2SVaultAction.depositCarried(button == 1));
            return;
        }

        VaultEntry entry = widget.getEntry();
        if (entry == null || entry.getKey().isEmpty()) {
            return;
        }
        ItemKey key = entry.getKey();
        this.selectedItemKey = key;
        if (this.inspectionPane != null) {
            this.inspectionPane.setInspectedKey(key);
        }
        playClick();

        // Middle-Click (button 2): Toggle Favorite
        if (button == 2) {
            Services.PLATFORM.sendToServer(PacketC2SVaultAction.toggleFavorite(key));
            return;
        }

        // Shift + Right-Click: Open inline mini-popover for autoRefill & refillTarget
        if (button == 1 && hasShiftDown()) {
            int popX = Mth.clamp(widget.getX() - 40, this.leftPos + RIGHT_PANE_OFFSET_X + 2, this.leftPos + WINDOW_WIDTH - 138);
            int popY = Mth.clamp(widget.getY() + 24, this.topPos + 46, this.topPos + WINDOW_HEIGHT - 68);
            this.refillPopover = new RefillMiniPopover(popX, popY, key);
            bindVaultWidgets();
            return;
        }

        int maxStack = Math.max(1, key.getItem().getMaxStackSize());
        if (button == 0) {
            if (hasShiftDown()) {
                // Shift + Left-Click: Quick-withdraw a full stack directly into the player's inventory
                Services.PLATFORM.sendToServer(PacketC2SVaultAction.withdraw(key, maxStack));
            } else {
                // Left-Click: Pick up a stack onto the cursor for drag-and-drop into Crafting/Equipment/Inventory
                Services.PLATFORM.sendToServer(PacketC2SVaultAction.pickupToCursor(key, maxStack));
            }
        } else if (button == 1) {
            // Right-Click: Pick up half a stack (or 1) onto the cursor
            int half = Math.max(1, (int) Math.min(maxStack, entry.getCount()) / 2);
            Services.PLATFORM.sendToServer(PacketC2SVaultAction.pickupToCursor(key, half));
        }
        bindVaultWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.inspectionMode && this.inspectionPane != null) {
            if (this.inspectionPane.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        }

        int step = delta > 0 ? -1 : 1;
        if (mouseX >= this.leftPos + RIGHT_PANE_OFFSET_X) {
            this.vaultScrollRow += step;
            bindVaultWidgets();
            return true;
        } else if (this.menu.getLeftPaneMode() == LeftPaneMode.BUILD_ECHOES
                && mouseY >= this.topPos + 58 && mouseY < this.topPos + 130) {
            int maxEchoScroll = Math.max(0, echoSlotCount() - ECHO_ROWS_VISIBLE);
            this.buildEchoScroll = Mth.clamp(this.buildEchoScroll + step, 0, maxEchoScroll);
            this.echoPopover = null;
            return true;
        } else if (this.menu.getLeftPaneMode() == LeftPaneMode.GEAR_CURIOS
                && mouseY >= this.topPos + 58 && mouseY < this.topPos + 130
                && this.menu.getTotalAccessoryPages() > 1) {
            this.menu.setAccessoryPage(this.menu.getAccessoryPage() + step);
            updateLeftPaneSubControls();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.echoPopover != null) {
            return this.echoPopover.keyPressed(keyCode, scanCode, modifiers);
        }
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                this.searchBox.setFocused(false);
                return true;
            }
            return this.searchBox.keyPressed(keyCode, scanCode, modifiers);
        }

        if (handleShortcut(mapping -> SoulsVaultKeyMappings.matchesKey(mapping, keyCode, scanCode))) {
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.echoPopover != null) {
            return this.echoPopover.nameBox.charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

    /**
     * Runs the screen action bound to the first matching rebindable {@link SoulsVaultKeyMappings} entry
     * (Inspect, 3x3 Crafting, Build Echoes). Works for both keyboard and mouse bindings.
     */
    private boolean handleShortcut(Predicate<KeyMapping> matcher) {
        if (matcher.test(SoulsVaultKeyMappings.INSPECT)) {
            // Toggles the Detailed Item Inspection Pane for the hovered or selected item
            updateHoveredSelectionBeforeInspect();
            toggleInspectionMode();
        } else if (matcher.test(SoulsVaultKeyMappings.CRAFTING)) {
            setLeftPaneMode(this.menu.getLeftPaneMode() == LeftPaneMode.CRAFTING
                    ? LeftPaneMode.GEAR_CURIOS
                    : LeftPaneMode.CRAFTING);
        } else if (matcher.test(SoulsVaultKeyMappings.BUILD_ECHOES)) {
            setLeftPaneMode(this.menu.getLeftPaneMode() == LeftPaneMode.BUILD_ECHOES
                    ? LeftPaneMode.GEAR_CURIOS
                    : LeftPaneMode.BUILD_ECHOES);
        } else {
            return false;
        }
        playClick();
        return true;
    }

    private void updateHoveredSelectionBeforeInspect() {
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            this.selectedItemKey = ItemKey.fromStack(this.hoveredSlot.getItem());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() * (double) this.width / (double) mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * (double) this.height / (double) mc.getWindow().getScreenHeight();

        for (VaultItemSlotWidget slot : this.vaultSlotWidgets) {
            if (!slot.isEmpty() && (slot.isMouseOver(mouseX, mouseY) || slot.isFocused()) && slot.getEntry() != null) {
                this.selectedItemKey = slot.getEntry().getKey();
                return;
            }
        }
    }

    /**
     * Popover opened via Right-Click on a Build Echo row: rename it and choose which extra slot groups
     * (registered {@link EchoSlotType}s, e.g. the hotbar) it restores.
     */
    private final class EchoOptionsPopover {
        private static final int WIDTH = 166;
        private static final int ROW_H = 12;

        private final int index;
        private final int x;
        private final int y;
        private final int height;
        private final EditBox nameBox;
        private final Map<String, Boolean> toggles = new LinkedHashMap<>();
        private final List<EchoSlotType> types;

        EchoOptionsPopover(int index, int x, int y) {
            this.index = index;
            this.types = new ArrayList<>(SoulsVaultApi.getEchoSlotTypes());
            this.height = 32 + this.types.size() * ROW_H + 16;
            this.x = x;
            this.y = Math.min(y, SoulsVaultScreen.this.topPos + WINDOW_HEIGHT - this.height - 4);
            BuildEcho echo = echoAt(index);
            this.nameBox = new EditBox(SoulsVaultScreen.this.font, this.x + 6, this.y + 16, WIDTH - 12, 12,
                    Component.translatable("gui.soulsvault.echo.name"));
            this.nameBox.setMaxLength(BuildEcho.MAX_NAME_LENGTH);
            this.nameBox.setValue(echo != null ? echo.getName() : BuildEcho.defaultName(index));
            this.nameBox.setFocused(true);
            for (EchoSlotType type : this.types) {
                this.toggles.put(type.id().toString(), echo != null ? echo.isTypeEnabled(type) : type.enabledByDefault());
            }
        }

        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (mouseX < this.x || mouseX >= this.x + WIDTH || mouseY < this.y || mouseY >= this.y + this.height) {
                return false;
            }
            if (this.nameBox.mouseClicked(mouseX, mouseY, button)) {
                this.nameBox.setFocused(true);
                return true;
            }
            int rowY = this.y + 32;
            for (EchoSlotType type : this.types) {
                if (mouseY >= rowY && mouseY < rowY + ROW_H) {
                    this.toggles.merge(type.id().toString(), true, (a, b) -> !a);
                    playClick();
                    return true;
                }
                rowY += ROW_H;
            }
            int buttonY = this.y + this.height - 15;
            if (mouseY >= buttonY && mouseY < buttonY + 12) {
                if (mouseX < this.x + WIDTH / 2.0D) {
                    save();
                } else {
                    SoulsVaultScreen.this.echoPopover = null;
                }
                playClick();
            }
            return true;
        }

        boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                SoulsVaultScreen.this.echoPopover = null;
                return true;
            }
            if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
                save();
                return true;
            }
            this.nameBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }

        private void save() {
            Services.PLATFORM.sendToServer(new PacketC2SEchoEdit(this.index, this.nameBox.getValue(), this.toggles));
            SoulsVaultScreen.this.echoPopover = null;
        }

        void render(GuiGraphics graphics, Font font, int mouseX, int mouseY, float partialTick) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 400.0F);
            graphics.fill(this.x, this.y, this.x + WIDTH, this.y + this.height, 0xFA121013);
            VaultItemSlotWidget.drawBorder(graphics, this.x, this.y, WIDTH, this.height, COLOR_GOLD);
            graphics.drawString(font, Component.translatable("gui.soulsvault.echo.options", this.index + 1),
                    this.x + 5, this.y + 4, COLOR_GOLD, true);

            graphics.fill(this.x + 4, this.y + 14, this.x + WIDTH - 4, this.y + 30, 0xFF0D0B0E);
            VaultItemSlotWidget.drawBorder(graphics, this.x + 4, this.y + 14, WIDTH - 8, 16, COLOR_BRONZE);
            this.nameBox.setBordered(false);
            this.nameBox.setTextColor(COLOR_TEXT_MAIN);
            this.nameBox.setY(this.y + 18);
            this.nameBox.render(graphics, mouseX, mouseY, partialTick);

            int rowY = this.y + 32;
            for (EchoSlotType type : this.types) {
                boolean enabled = this.toggles.getOrDefault(type.id().toString(), type.enabledByDefault());
                boolean hovered = mouseY >= rowY && mouseY < rowY + ROW_H && mouseX >= this.x && mouseX < this.x + WIDTH;
                graphics.fill(this.x + 4, rowY, this.x + WIDTH - 4, rowY + ROW_H - 1, hovered ? 0xFF2D2418 : 0xFF1E1A22);
                MutableComponent label = Component.translatable(
                        enabled ? "gui.soulsvault.echo.restore_on" : "gui.soulsvault.echo.restore_off", type.displayName());
                graphics.drawString(font, label, this.x + 7, rowY + 2, enabled ? COLOR_GOLD : COLOR_TEXT_SUB, false);
                rowY += ROW_H;
            }

            int buttonY = this.y + this.height - 15;
            int half = (WIDTH - 12) / 2;
            drawPopoverButton(graphics, font, this.x + 4, buttonY, half, Component.translatable("gui.soulsvault.action.save"), mouseX, mouseY);
            drawPopoverButton(graphics, font, this.x + 8 + half, buttonY, half, Component.translatable("gui.cancel"), mouseX, mouseY);
            graphics.pose().popPose();
        }

        private void drawPopoverButton(GuiGraphics graphics, Font font, int bx, int by, int bw, Component label, int mouseX, int mouseY) {
            boolean hovered = mouseX >= bx && mouseX < bx + bw && mouseY >= by && mouseY < by + 12;
            graphics.fill(bx, by, bx + bw, by + 12, hovered ? 0xFF33291C : 0xFF1B171E);
            VaultItemSlotWidget.drawBorder(graphics, bx, by, bw, 12, hovered ? COLOR_GOLD : COLOR_BRONZE_MUTED);
            int tw = font.width(label);
            graphics.drawString(font, label, bx + (bw - tw) / 2, by + 2, COLOR_TEXT_MAIN, false);
        }
    }

    /**
     * Mini-popover opened via Shift + Right-Click on a Vault item to configure autoRefill,
     * overflowDeposit, and refillTarget inline.
     */
    private static final class RefillMiniPopover {
        private static final int WIDTH = 132;
        private static final int HEIGHT = 58;

        private final int x;
        private final int y;
        private final ItemKey key;

        RefillMiniPopover(int x, int y, ItemKey key) {
            this.x = x;
            this.y = y;
            this.key = key;
        }

        @Nullable
        private VaultEntry getEntry() {
            return ClientVaultCache.getVaultData().getEntry(this.key);
        }

        boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (mouseX < this.x || mouseX >= this.x + WIDTH || mouseY < this.y || mouseY >= this.y + HEIGHT) {
                return false;
            }
            if (button != 0) {
                return true;
            }

            VaultEntry entry = getEntry();
            boolean autoRefill = entry != null && entry.isAutoRefill();
            boolean overflow = entry != null && entry.isOverflowDeposit();
            int target = entry != null ? entry.getRefillTarget() : Math.max(1, this.key.getItem().getMaxStackSize());

            if (mouseY >= this.y + 16 && mouseY < this.y + 28) {
                Services.PLATFORM.sendToServer(PacketC2SVaultAction.setRefillTarget(this.key, target, !autoRefill, overflow));
                return true;
            }
            if (mouseY >= this.y + 29 && mouseY < this.y + 41) {
                Services.PLATFORM.sendToServer(PacketC2SVaultAction.setRefillTarget(this.key, target, autoRefill, !overflow));
                return true;
            }
            if (mouseY >= this.y + 42 && mouseY < this.y + 55) {
                int relX = (int) mouseX - (this.x + 4);
                int segW = (WIDTH - 8) / 4;
                int delta = switch (Mth.clamp(relX / segW, 0, 3)) {
                    case 0 -> -16;
                    case 1 -> -1;
                    case 2 -> 1;
                    default -> 16;
                };
                int nextTarget = Mth.clamp(target + delta, 1, 999);
                Services.PLATFORM.sendToServer(PacketC2SVaultAction.setRefillTarget(this.key, nextTarget, true, overflow));
                return true;
            }
            return true;
        }

        void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, 400.0F);

            graphics.fill(this.x, this.y, this.x + WIDTH, this.y + HEIGHT, 0xFA121013);
            VaultItemSlotWidget.drawBorder(graphics, this.x, this.y, WIDTH, HEIGHT, COLOR_GOLD);

            VaultEntry entry = getEntry();
            boolean autoRefill = entry != null && entry.isAutoRefill();
            boolean overflow = entry != null && entry.isOverflowDeposit();
            int target = entry != null ? entry.getRefillTarget() : Math.max(1, this.key.getItem().getMaxStackSize());

            String header = Component.translatable("gui.soulsvault.inspect.refill_target", target).getString();
            graphics.drawString(font, header, this.x + 5, this.y + 4, COLOR_GOLD, true);

            int r1Color = autoRefill ? 0xFFE06C50 : COLOR_TEXT_SUB;
            graphics.fill(this.x + 4, this.y + 16, this.x + WIDTH - 4, this.y + 27, 0xFF1E1A22);
            graphics.drawString(
                    font,
                    Component.translatable(autoRefill ? "gui.soulsvault.inspect.autorefill.on" : "gui.soulsvault.inspect.autorefill.off"),
                    this.x + 6,
                    this.y + 18,
                    r1Color,
                    false
            );

            int r2Color = overflow ? 0xFF4FA8B8 : COLOR_TEXT_SUB;
            graphics.fill(this.x + 4, this.y + 29, this.x + WIDTH - 4, this.y + 40, 0xFF1E1A22);
            graphics.drawString(
                    font,
                    Component.translatable(overflow ? "gui.soulsvault.inspect.overflow.on" : "gui.soulsvault.inspect.overflow.off"),
                    this.x + 6,
                    this.y + 31,
                    r2Color,
                    false
            );

            String[] labels = {"-16", "-1", "+1", "+16"};
            int segW = (WIDTH - 8) / 4;
            for (int i = 0; i < 4; i++) {
                int bx = this.x + 4 + i * segW;
                int by = this.y + 42;
                boolean hov = mouseX >= bx && mouseX < bx + segW - 1 && mouseY >= by && mouseY < by + 12;
                graphics.fill(bx, by, bx + segW - 1, by + 12, hov ? 0xFF33291C : 0xFF1B171E);
                VaultItemSlotWidget.drawBorder(graphics, bx, by, segW - 1, 12, hov ? COLOR_GOLD : COLOR_BRONZE_MUTED);
                int tw = font.width(labels[i]);
                graphics.drawString(font, labels[i], bx + (segW - tw) / 2, by + 2, COLOR_TEXT_MAIN, false);
            }

            graphics.pose().popPose();
        }
    }
}
