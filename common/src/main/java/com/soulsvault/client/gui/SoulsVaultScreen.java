package com.soulsvault.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.soulsvault.Constants;
import com.soulsvault.client.ClientVaultCache;
import com.soulsvault.client.gui.widget.CategoryTabButton;
import com.soulsvault.client.gui.widget.GothicButton;
import com.soulsvault.client.gui.widget.ItemInspectionPane;
import com.soulsvault.client.gui.widget.VaultItemSlotWidget;
import com.soulsvault.config.SoulsVaultConfig;
import com.soulsvault.core.BuildEcho;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultCategory;
import com.soulsvault.core.VaultEntry;
import com.soulsvault.core.VaultSortMode;
import com.soulsvault.network.PacketC2SVaultAction;
import com.soulsvault.platform.Services;
import com.soulsvault.world.inventory.SoulsVaultMenu;
import com.soulsvault.world.inventory.SoulsVaultMenu.LeftPaneMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private static final int VAULT_COLS = 7;
    private static final int VAULT_ROWS = 6;
    private static final int VAULT_SLOT_STEP = 26;

    private VaultCategory selectedCategory = VaultCategory.ALL;
    private VaultSortMode sortMode = VaultSortMode.RECENTLY_ACQUIRED;
    private boolean inspectionMode = false;

    private final List<CategoryTabButton> categoryTabButtons = new ArrayList<>();
    private final List<VaultItemSlotWidget> vaultSlotWidgets = new ArrayList<>();

    private List<VaultEntry> filteredVaultEntries = new ArrayList<>();
    private int vaultScrollRow = 0;
    private int buildEchoScroll = 0;

    private EditBox searchBox;
    private GothicButton sortButton;
    private GothicButton inspectToggleButton;
    private GothicButton invModeConfigButton;
    private GothicButton helpLegendButton;
    @Nullable
    private GothicButton waystonesButton;

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

    private final Runnable syncListener = this::refreshAllSlots;

    public SoulsVaultScreen(SoulsVaultMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = WINDOW_WIDTH;
        this.imageHeight = WINDOW_HEIGHT;
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
            tabBtn.setSelected(cat == this.selectedCategory);
            this.categoryTabButtons.add(tabBtn);
            addRenderableWidget(tabBtn);
        }

        // 2. Search Box & Sort Selector above Right Pane
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
        this.searchBox.setResponder(query -> {
            this.vaultScrollRow = 0;
            refreshAllSlots();
        });
        addRenderableWidget(this.searchBox);

        this.sortButton = new GothicButton(
                rightPaneX + SEARCH_BOX_WIDTH + 4,
                this.topPos + 22,
                RIGHT_PANE_WIDTH - SEARCH_BOX_WIDTH - 4,
                SEARCH_BOX_HEIGHT,
                this.sortMode.getDisplayName(),
                btn -> {
                    this.sortMode = this.sortMode.next();
                    btn.setMessage(this.sortMode.getDisplayName());
                    refreshAllSlots();
                }
        );
        addRenderableWidget(this.sortButton);

        // 3. Header Right Controls: Waystones Button (if loaded), Full-Inventory Config Toggle, Inspect Mode, [?] Help Legend
        if (Services.PLATFORM.isModLoaded("waystones")) {
            this.waystonesButton = new GothicButton(
                    this.leftPos + WINDOW_WIDTH - 224,
                    this.topPos + 4,
                    48,
                    14,
                    Component.translatable("gui.soulsvault.action.waystones"),
                    btn -> Services.PLATFORM.triggerWaystonesButton()
            );
            addRenderableWidget(this.waystonesButton);
        }

        this.invModeConfigButton = new GothicButton(
                this.leftPos + WINDOW_WIDTH - 172,
                this.topPos + 4,
                72,
                14,
                getInvModeConfigLabel(),
                btn -> {
                    SoulsVaultConfig.setFullInventoryReplacement(!SoulsVaultConfig.isFullInventoryReplacement());
                    btn.setMessage(getInvModeConfigLabel());
                    btn.setHighlighted(SoulsVaultConfig.isFullInventoryReplacement());
                }
        );
        this.invModeConfigButton.setHighlighted(SoulsVaultConfig.isFullInventoryReplacement());
        addRenderableWidget(this.invModeConfigButton);

        this.inspectToggleButton = new GothicButton(
                this.leftPos + WINDOW_WIDTH - 96,
                this.topPos + 4,
                68,
                14,
                getInspectButtonLabel(),
                btn -> toggleInspectionMode()
        );
        this.inspectToggleButton.setHighlighted(this.inspectionMode);
        addRenderableWidget(this.inspectToggleButton);

        this.helpLegendButton = new GothicButton(
                this.leftPos + WINDOW_WIDTH - 24,
                this.topPos + 4,
                16,
                14,
                Component.literal("?"),
                btn -> {
                }
        );
        addRenderableWidget(this.helpLegendButton);

        // 4. Left-Pane Top Sub-Mode Tabs: [Gear & Curios] | [3x3 Craft] | [Echoes]
        int subTabY = this.topPos + 44;
        this.tabGearCuriosButton = new GothicButton(
                leftPaneX + 4,
                subTabY,
                60,
                13,
                Component.translatable("gui.soulsvault.tab.gear_curios"),
                btn -> setLeftPaneMode(LeftPaneMode.GEAR_CURIOS)
        );
        this.tabCraftingButton = new GothicButton(
                leftPaneX + 66,
                subTabY,
                54,
                13,
                Component.translatable("gui.soulsvault.tab.crafting"),
                btn -> setLeftPaneMode(LeftPaneMode.CRAFTING)
        );
        this.tabEchoesButton = new GothicButton(
                leftPaneX + 122,
                subTabY,
                50,
                13,
                Component.translatable("gui.soulsvault.tab.echoes"),
                btn -> setLeftPaneMode(LeftPaneMode.BUILD_ECHOES)
        );
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

        this.prevAccessoryPageButton = new GothicButton(
                leftPaneX + 147,
                this.topPos + SoulsVaultMenu.EQUIP_ROW_Y,
                11,
                16,
                Component.literal("<"),
                btn -> {
                    this.menu.setAccessoryPage(this.menu.getAccessoryPage() - 1);
                    sendMenuButton(4);
                    updateLeftPaneSubControls();
                }
        );
        this.nextAccessoryPageButton = new GothicButton(
                leftPaneX + 159,
                this.topPos + SoulsVaultMenu.EQUIP_ROW_Y,
                11,
                16,
                Component.literal(">"),
                btn -> {
                    this.menu.setAccessoryPage(this.menu.getAccessoryPage() + 1);
                    sendMenuButton(5);
                    updateLeftPaneSubControls();
                }
        );
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
        GothicButton restReplenishBtn = new GothicButton(
                leftPaneX,
                bottomBtnY,
                86,
                15,
                Component.translatable("gui.soulsvault.action.rest_replenish"),
                btn -> Services.PLATFORM.sendToServer(PacketC2SVaultAction.restReplenish())
        );
        addRenderableWidget(restReplenishBtn);

        GothicButton quickDepositBtn = new GothicButton(
                leftPaneX + 90,
                bottomBtnY,
                86,
                15,
                Component.translatable("gui.soulsvault.action.quick_deposit"),
                btn -> Services.PLATFORM.sendToServer(PacketC2SVaultAction.depositAllCategory(this.selectedCategory))
        );
        addRenderableWidget(quickDepositBtn);

        // 7. Right Pane Virtualized Vault Slot Widgets (Registered as AbstractWidgets for Controller Support!)
        this.vaultSlotWidgets.clear();
        int vaultGridStartX = rightPaneX + 18;
        int vaultGridStartY = this.topPos + 58;
        for (int row = 0; row < VAULT_ROWS; row++) {
            for (int col = 0; col < VAULT_COLS; col++) {
                VaultItemSlotWidget slotWidget = new VaultItemSlotWidget(
                        vaultGridStartX + col * VAULT_SLOT_STEP,
                        vaultGridStartY + row * VAULT_SLOT_STEP,
                        VaultItemSlotWidget.SLOT_SIZE,
                        this::onVaultWidgetClicked
                );
                this.vaultSlotWidgets.add(slotWidget);
                addRenderableWidget(slotWidget);
            }
        }

        this.inspectionPane = new ItemInspectionPane(
                rightPaneX,
                this.topPos + 42,
                RIGHT_PANE_WIDTH,
                WINDOW_HEIGHT - 48
        );
        this.inspectionPane.setInspectedKey(this.selectedItemKey);

        ClientVaultCache.registerSyncListener(this.syncListener);
        updateLeftPaneSubControls();
        refreshAllSlots();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        refreshAllSlots();
    }

    @Override
    public void removed() {
        ClientVaultCache.unregisterSyncListener(this.syncListener);
        super.removed();
    }

    private Component getInspectButtonLabel() {
        return Component.translatable(
                this.inspectionMode ? "gui.soulsvault.action.grid_toggle" : "gui.soulsvault.action.inspect_toggle"
        );
    }

    private Component getInvModeConfigLabel() {
        return Component.translatable(
                SoulsVaultConfig.isFullInventoryReplacement()
                        ? "gui.soulsvault.config.inv_souls"
                        : "gui.soulsvault.config.inv_vanilla"
        );
    }

    private void setLeftPaneMode(LeftPaneMode mode) {
        this.menu.setLeftPaneMode(mode);
        sendMenuButton(switch (mode) {
            case GEAR_CURIOS -> 0;
            case CRAFTING -> 1;
            case BUILD_ECHOES -> 2;
        });
        this.refillPopover = null;
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
        for (VaultItemSlotWidget vaultSlot : this.vaultSlotWidgets) {
            vaultSlot.visible = !this.inspectionMode;
            vaultSlot.active = !this.inspectionMode;
        }
    }

    private void selectCategory(VaultCategory category) {
        this.selectedCategory = category;
        this.vaultScrollRow = 0;
        this.refillPopover = null;
        for (CategoryTabButton btn : this.categoryTabButtons) {
            btn.setSelected(btn.getCategory() == category);
        }
        refreshAllSlots();
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
    }

    public void refreshAllSlots() {
        Minecraft mc = Minecraft.getInstance();
        PlayerVaultData vaultData = ClientVaultCache.getVaultData();

        // Update category tab badge counts (including ALL)
        Map<VaultCategory, Integer> countsByCategory = new EnumMap<>(VaultCategory.class);
        int totalUniqueEntries = 0;
        for (VaultEntry entry : vaultData.getEntries()) {
            if (entry.shouldRetain()) {
                totalUniqueEntries++;
                countsByCategory.merge(entry.getCategory(), 1, Integer::sum);
            }
        }
        countsByCategory.put(VaultCategory.ALL, totalUniqueEntries);

        for (CategoryTabButton tabBtn : this.categoryTabButtons) {
            tabBtn.setEntryCount(countsByCategory.getOrDefault(tabBtn.getCategory(), 0));
        }

        // Filter & sort vault entries for the active category
        List<VaultEntry> rawCategoryEntries = vaultData.getEntriesByCategory(this.selectedCategory);
        String query = this.searchBox != null ? this.searchBox.getValue().trim() : "";
        List<VaultEntry> matching = new ArrayList<>();
        for (VaultEntry entry : rawCategoryEntries) {
            if (matchesSearchQuery(mc, entry.getKey(), entry.createDisplayStack(), query)) {
                matching.add(entry);
            }
        }
        matching.sort(this.sortMode.getComparator());
        this.filteredVaultEntries = matching;

        // Clamp vault scroll
        int totalVaultRows = Math.max(1, (this.filteredVaultEntries.size() + VAULT_COLS - 1) / VAULT_COLS);
        int maxVaultScroll = Math.max(0, totalVaultRows - VAULT_ROWS);
        this.vaultScrollRow = Mth.clamp(this.vaultScrollRow, 0, maxVaultScroll);

        // Bind visible vault slot widgets
        int startVaultIndex = this.vaultScrollRow * VAULT_COLS;
        for (int i = 0; i < this.vaultSlotWidgets.size(); i++) {
            int entryIdx = startVaultIndex + i;
            VaultItemSlotWidget widget = this.vaultSlotWidgets.get(i);
            widget.visible = !this.inspectionMode;
            if (entryIdx < this.filteredVaultEntries.size()) {
                VaultEntry entry = this.filteredVaultEntries.get(entryIdx);
                widget.bindVaultEntry(entry, entry.getKey().equals(this.selectedItemKey));
            } else {
                widget.bindVaultEntry(null, false);
            }
        }

        if (this.selectedItemKey.isEmpty() && !this.filteredVaultEntries.isEmpty()) {
            this.selectedItemKey = this.filteredVaultEntries.get(0).getKey();
            if (this.inspectionPane != null) {
                this.inspectionPane.setInspectedKey(this.selectedItemKey);
            }
        }
    }

    private boolean matchesSearchQuery(Minecraft mc, ItemKey key, ItemStack stack, String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return true;
        }
        if (key.isEmpty() || stack.isEmpty()) {
            return false;
        }
        String query = rawQuery.toLowerCase(Locale.ROOT);

        if (query.startsWith("@")) {
            String modQuery = query.substring(1).trim();
            return key.getNamespace().toLowerCase(Locale.ROOT).contains(modQuery);
        }

        if (query.startsWith("#")) {
            String tagOrLoreQuery = query.substring(1).trim();
            if (tagOrLoreQuery.isEmpty()) {
                return true;
            }
            boolean tagMatch = stack.getTags().anyMatch(
                    tagKey -> tagKey.location().toString().toLowerCase(Locale.ROOT).contains(tagOrLoreQuery)
            );
            if (tagMatch) {
                return true;
            }
            List<Component> tooltipLines = stack.getTooltipLines(mc.player, TooltipFlag.Default.NORMAL);
            for (Component line : tooltipLines) {
                if (line.getString().toLowerCase(Locale.ROOT).contains(tagOrLoreQuery)) {
                    return true;
                }
            }
            return false;
        }

        String displayName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (displayName.contains(query)) {
            return true;
        }
        return key.getRegistryId().getPath().toLowerCase(Locale.ROOT).contains(query);
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

        // Window title
        graphics.drawString(this.font, this.title, leftPaneX, this.topPos + 7, COLOR_GOLD, true);

        // Custom Gothic Search Box Frame + Clipped Hint
        int searchY = this.topPos + 22;
        boolean searchFocused = this.searchBox != null && this.searchBox.isFocused();
        graphics.fill(rightPaneX, searchY, rightPaneX + SEARCH_BOX_WIDTH, searchY + SEARCH_BOX_HEIGHT, 0xF00D0B0E);
        VaultItemSlotWidget.drawBorder(
                graphics,
                rightPaneX,
                searchY,
                SEARCH_BOX_WIDTH,
                SEARCH_BOX_HEIGHT,
                searchFocused ? COLOR_GOLD : COLOR_BRONZE
        );
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
            renderCraftingGridBg(graphics, leftPaneX);
        } else if (leftMode == LeftPaneMode.BUILD_ECHOES) {
            renderBuildEchoesDrawer(graphics, leftPaneX, this.topPos + 60, LEFT_PANE_WIDTH, mouseX, mouseY);
        }

        // Divider above 36-Slot Player Inventory
        graphics.fill(leftPaneX + 6, this.topPos + 126, leftPaneX + LEFT_PANE_WIDTH - 6, this.topPos + 127, COLOR_BRONZE_MUTED);
        graphics.pose().pushPose();
        graphics.pose().translate(leftPaneX + 7.0F, this.topPos + 129.0F, 0.0F);
        graphics.pose().scale(0.85F, 0.85F, 1.0F);
        graphics.drawString(
                this.font,
                Component.translatable("gui.soulsvault.subtitle.inventory"),
                0,
                0,
                COLOR_TEXT_SUB,
                false
        );
        graphics.pose().popPose();

        // Render Gothic Slot Frames & Category/Search Highlights for all active Menu Slots
        renderMenuSlotFrames(graphics);

        // Right Pane: either Detailed ItemInspectionPane or Virtualized Vault Grid Background
        if (this.inspectionMode) {
            this.inspectionPane.setInspectedKey(this.selectedItemKey);
            this.inspectionPane.render(graphics, mouseX, mouseY, partialTick);
        } else {
            int vaultPaneH = WINDOW_HEIGHT - 48;
            graphics.fill(rightPaneX, paneTopY, rightPaneX + RIGHT_PANE_WIDTH, paneTopY + vaultPaneH, COLOR_PANE_BG);
            VaultItemSlotWidget.drawBorder(graphics, rightPaneX, paneTopY, RIGHT_PANE_WIDTH, vaultPaneH, COLOR_BRONZE_MUTED);

            String headerRight = this.selectedCategory.getDisplayName().getString()
                    + " (" + this.filteredVaultEntries.size() + ")";
            graphics.drawString(this.font, headerRight, rightPaneX + 18, paneTopY + 5, COLOR_GOLD, false);

            if (!this.menu.isVaultAccessAllowed()) {
                String lockedNotice = Component.translatable("gui.soulsvault.notice.rest_point_required").getString();
                int nw = this.font.width(lockedNotice);
                graphics.drawString(this.font, lockedNotice, rightPaneX + RIGHT_PANE_WIDTH - nw - 8, paneTopY + 5, 0xFFE06C50, false);
            }

            // Vault scrollbar track
            int totalVaultRows = Math.max(1, (this.filteredVaultEntries.size() + VAULT_COLS - 1) / VAULT_COLS);
            if (totalVaultRows > VAULT_ROWS) {
                int trackX = rightPaneX + RIGHT_PANE_WIDTH - 8;
                int trackY = this.topPos + 58;
                int trackH = VAULT_ROWS * VAULT_SLOT_STEP - 2;
                graphics.fill(trackX, trackY, trackX + 2, trackY + trackH, 0x802A241C);
                int maxScroll = totalVaultRows - VAULT_ROWS;
                int thumbH = Math.max(14, (trackH * VAULT_ROWS) / totalVaultRows);
                int thumbY = trackY + ((trackH - thumbH) * this.vaultScrollRow) / maxScroll;
                graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, COLOR_GOLD);
            }
        }
    }

    private void renderGearAndCuriosBg(GuiGraphics graphics) {
        int leftPaneX = this.leftPos + LEFT_PANE_OFFSET_X;
        List<SoulsVaultMenu.AccessoryMenuSlot> accSlots = this.menu.getVisibleAccessorySlots();
        if (accSlots.isEmpty()) {
            String msg = this.font.plainSubstrByWidth(
                    Component.translatable("gui.soulsvault.notice.no_curios").getString(),
                    LEFT_PANE_WIDTH - 14
            );
            graphics.drawString(
                    this.font,
                    msg,
                    leftPaneX + 7,
                    this.topPos + 96,
                    0xFF6B6257,
                    false
            );
        }
    }

    private void renderCraftingGridBg(GuiGraphics graphics, int leftPaneX) {
        int arrowX = this.leftPos + SoulsVaultMenu.CRAFT_GRID_X + 60;
        int arrowY = this.topPos + SoulsVaultMenu.CRAFT_GRID_Y + 21;
        graphics.drawString(this.font, "→", arrowX, arrowY, COLOR_GOLD, true);

        // Ornate 24x24 frame around the Crafting Result Slot
        int resBoxX = this.leftPos + SoulsVaultMenu.CRAFT_RESULT_X - 4;
        int resBoxY = this.topPos + SoulsVaultMenu.CRAFT_RESULT_Y - 4;
        graphics.fill(resBoxX, resBoxY, resBoxX + 24, resBoxY + 24, 0xF01C1612);
        VaultItemSlotWidget.drawBorder(graphics, resBoxX, resBoxY, 24, 24, COLOR_GOLD);

        String craftHint = Component.translatable("gui.soulsvault.crafting.auto_refill").getString();
        graphics.pose().pushPose();
        graphics.pose().translate(this.leftPos + SoulsVaultMenu.CRAFT_GRID_X + 58.0F, this.topPos + SoulsVaultMenu.CRAFT_GRID_Y + 42.0F, 0.0F);
        graphics.pose().scale(0.68F, 0.68F, 1.0F);
        graphics.drawString(this.font, craftHint, 0, 0, COLOR_TEXT_SUB, false);
        graphics.pose().popPose();
    }

    private void renderMenuSlotFrames(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        String query = this.searchBox != null ? this.searchBox.getValue().trim() : "";
        boolean hasFilter = (this.selectedCategory != VaultCategory.ALL) || !query.isEmpty();

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
                } else if (hasFilter && slot.container == this.menu.slots.get(15).container) {
                    boolean matches = this.selectedCategory.matches(stack) && matchesSearchQuery(mc, key, stack, query);
                    if (matches) {
                        borderColor = COLOR_GOLD;
                    }
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
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        if (this.refillPopover != null) {
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
            List<Component> helpLines = List.of(
                    Component.translatable("gui.soulsvault.help.title").withStyle(ChatFormatting.GOLD),
                    Component.literal("• L-Click Vault: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("Pick up stack to cursor").withStyle(ChatFormatting.WHITE)),
                    Component.literal("• R-Click Vault: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("Pick up half stack").withStyle(ChatFormatting.WHITE)),
                    Component.literal("• Shift + L-Click: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("Quick Deposit / Withdraw").withStyle(ChatFormatting.WHITE)),
                    Component.literal("• M-Click Vault: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("Pin Favorite (★)").withStyle(ChatFormatting.YELLOW)),
                    Component.literal("• Shift + R-Click Vault: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("Auto-Refill & Carry Cap").withStyle(ChatFormatting.AQUA)),
                    Component.literal("• Hotkeys: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal("[R] Inspect  [C] Craft  [B] Echoes").withStyle(ChatFormatting.GOLD))
            );
            graphics.renderComponentTooltip(this.font, helpLines, mouseX, mouseY);
            return;
        }

        // 3. Category tab hover tooltips
        for (CategoryTabButton tab : this.categoryTabButtons) {
            if (tab.isMouseOver(mouseX, mouseY)) {
                graphics.renderTooltip(this.font, tab.getCategory().getDisplayName(), mouseX, mouseY);
                return;
            }
        }

        // 4. Vault slot hover tooltips (only on mouse hover, never on sticky focus, and without wide footer text)
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

    private void renderBuildEchoesDrawer(
            GuiGraphics graphics,
            int paneX,
            int startY,
            int paneW,
            int mouseX,
            int mouseY
    ) {
        List<BuildEcho> echoes = ClientVaultCache.getVaultData().getBuildEchoes();
        int visibleRows = 3;
        int maxScroll = Math.max(0, Constants.MAX_BUILD_ECHOES - visibleRows);
        this.buildEchoScroll = Mth.clamp(this.buildEchoScroll, 0, maxScroll);

        int rowStartY = startY;
        int rowHeight = 22;
        int cardX = paneX + 5;
        int cardW = paneW - 10;

        for (int v = 0; v < visibleRows; v++) {
            int i = this.buildEchoScroll + v;
            if (i >= Constants.MAX_BUILD_ECHOES) {
                break;
            }
            int ry = rowStartY + v * rowHeight;
            BuildEcho echo = i < echoes.size() ? echoes.get(i) : null;

            graphics.fill(cardX, ry, cardX + cardW, ry + rowHeight - 2, 0xF016131A);
            VaultItemSlotWidget.drawBorder(graphics, cardX, ry, cardW, rowHeight - 2, COLOR_BRONZE_MUTED);

            String label = "#" + (i + 1);
            graphics.drawString(this.font, label, cardX + 3, ry + 6, COLOR_GOLD, false);

            int iconBaseX = cardX + 18;
            if (echo != null) {
                List<ItemKey> previewKeys = new ArrayList<>(6);
                previewKeys.add(echo.getMainHand());
                previewKeys.add(echo.getOffHand());
                for (EquipmentSlot slot : new EquipmentSlot[]{
                        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
                }) {
                    previewKeys.add(echo.getArmor(slot));
                }

                for (int k = 0; k < previewKeys.size(); k++) {
                    ItemKey ik = previewKeys.get(k);
                    int ix = iconBaseX + k * 13;
                    int iy = ry + 3;
                    graphics.fill(ix, iy, ix + 12, iy + 13, 0xFF0D0B0E);
                    if (!ik.isEmpty()) {
                        graphics.pose().pushPose();
                        graphics.pose().translate(ix, iy + 0.5F, 100.0F);
                        graphics.pose().scale(0.7F, 0.7F, 1.0F);
                        graphics.renderItem(ik.toSingleStack(), 0, 0);
                        graphics.pose().popPose();
                    }
                }
            } else {
                graphics.drawString(this.font, "Empty Preset", iconBaseX + 2, ry + 6, 0xFF685F54, false);
            }

            int saveBtnX = cardX + cardW - 64;
            int loadBtnX = cardX + cardW - 32;
            int btnY = ry + 3;
            int btnW = 30;
            int btnH = 13;

            boolean saveHov = mouseX >= saveBtnX && mouseX < saveBtnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;
            boolean loadHov = mouseX >= loadBtnX && mouseX < loadBtnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;

            graphics.fill(saveBtnX, btnY, saveBtnX + btnW, btnY + btnH, saveHov ? 0xFF2D2418 : 0xFF1B171E);
            VaultItemSlotWidget.drawBorder(graphics, saveBtnX, btnY, btnW, btnH, saveHov ? COLOR_GOLD : COLOR_BRONZE);
            String saveStr = Component.translatable("gui.soulsvault.action.echo_save").getString();
            graphics.pose().pushPose();
            graphics.pose().translate(saveBtnX + (btnW - this.font.width(saveStr) * 0.75F) / 2.0F, btnY + 3.5F, 200.0F);
            graphics.pose().scale(0.75F, 0.75F, 1.0F);
            graphics.drawString(this.font, saveStr, 0, 0, saveHov ? COLOR_GOLD : COLOR_TEXT_MAIN, false);
            graphics.pose().popPose();

            graphics.fill(loadBtnX, btnY, loadBtnX + btnW, btnY + btnH, loadHov ? 0xFF2D2418 : 0xFF1B171E);
            VaultItemSlotWidget.drawBorder(graphics, loadBtnX, btnY, btnW, btnH, loadHov ? COLOR_GOLD : COLOR_BRONZE);
            String loadStr = Component.translatable("gui.soulsvault.action.echo_load").getString();
            graphics.pose().pushPose();
            graphics.pose().translate(loadBtnX + (btnW - this.font.width(loadStr) * 0.75F) / 2.0F, btnY + 3.5F, 200.0F);
            graphics.pose().scale(0.75F, 0.75F, 1.0F);
            graphics.drawString(this.font, loadStr, 0, 0, loadHov ? COLOR_GOLD : COLOR_TEXT_MAIN, false);
            graphics.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. Handle active Refill Mini-Popover clicks
        if (this.refillPopover != null) {
            if (this.refillPopover.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            this.refillPopover = null;
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
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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

    private boolean handleBuildEchoesDrawerClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int paneX = this.leftPos + LEFT_PANE_OFFSET_X;
        int paneW = LEFT_PANE_WIDTH;
        int rowStartY = this.topPos + 60;
        int rowHeight = 22;
        int cardX = paneX + 5;
        int cardW = paneW - 10;

        int saveBtnX = cardX + cardW - 64;
        int loadBtnX = cardX + cardW - 32;
        int btnW = 30;
        int btnH = 13;

        for (int v = 0; v < 3; v++) {
            int i = this.buildEchoScroll + v;
            if (i >= Constants.MAX_BUILD_ECHOES) {
                break;
            }
            int ry = rowStartY + v * rowHeight;
            int btnY = ry + 3;

            if (mouseX >= saveBtnX && mouseX < saveBtnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                Services.PLATFORM.sendToServer(PacketC2SVaultAction.saveBuildEcho(i));
                return true;
            }
            if (mouseX >= loadBtnX && mouseX < loadBtnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                Services.PLATFORM.sendToServer(PacketC2SVaultAction.loadBuildEcho(i));
                return true;
            }
        }
        return false;
    }

    private void onVaultWidgetClicked(VaultItemSlotWidget widget, int button) {
        // If holding a cursor stack, deposit it into the Vault
        if (!this.menu.getCarried().isEmpty() && (button == 0 || button == 1)) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));

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
            refreshAllSlots();
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
        refreshAllSlots();
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
            int totalVaultRows = Math.max(1, (this.filteredVaultEntries.size() + VAULT_COLS - 1) / VAULT_COLS);
            int maxVaultScroll = Math.max(0, totalVaultRows - VAULT_ROWS);
            this.vaultScrollRow = Mth.clamp(this.vaultScrollRow + step, 0, maxVaultScroll);
            refreshAllSlots();
            return true;
        } else if (this.menu.getLeftPaneMode() == LeftPaneMode.BUILD_ECHOES
                && mouseY >= this.topPos + 58 && mouseY < this.topPos + 130) {
            int maxEchoScroll = Math.max(0, Constants.MAX_BUILD_ECHOES - 3);
            this.buildEchoScroll = Mth.clamp(this.buildEchoScroll + step, 0, maxEchoScroll);
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
        if (this.searchBox != null && this.searchBox.isFocused()) {
            if (keyCode == InputConstants.KEY_ESCAPE) {
                this.searchBox.setFocused(false);
                return true;
            }
            return this.searchBox.keyPressed(keyCode, scanCode, modifiers);
        }

        // 'R' key toggles the Detailed Item Inspection Pane for the hovered or selected item
        if (keyCode == InputConstants.KEY_R) {
            updateHoveredSelectionBeforeInspect();
            toggleInspectionMode();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        // 'C' key toggles the 3x3 Crafting Terminal
        if (keyCode == InputConstants.KEY_C) {
            setLeftPaneMode(this.menu.getLeftPaneMode() == LeftPaneMode.CRAFTING
                    ? LeftPaneMode.GEAR_CURIOS
                    : LeftPaneMode.CRAFTING);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        // 'B' key toggles the Build Echoes Loadout Drawer
        if (keyCode == InputConstants.KEY_B) {
            setLeftPaneMode(this.menu.getLeftPaneMode() == LeftPaneMode.BUILD_ECHOES
                    ? LeftPaneMode.GEAR_CURIOS
                    : LeftPaneMode.BUILD_ECHOES);
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
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

        void render(GuiGraphics graphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
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
