package com.soulsvault.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.soulsvault.Constants;
import com.soulsvault.client.ClientVaultCache;
import com.soulsvault.client.gui.widget.CategoryTabButton;
import com.soulsvault.client.gui.widget.GothicButton;
import com.soulsvault.client.gui.widget.ItemInspectionPane;
import com.soulsvault.client.gui.widget.VaultItemSlotWidget;
import com.soulsvault.core.BuildEcho;
import com.soulsvault.core.ItemKey;
import com.soulsvault.core.PlayerVaultData;
import com.soulsvault.core.VaultCategory;
import com.soulsvault.core.VaultEntry;
import com.soulsvault.core.VaultSortMode;
import com.soulsvault.network.PacketC2SVaultAction;
import com.soulsvault.platform.Services;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dual-pane Souls-like Casket of Echoes screen.
 * <p>
 * Obeys the dark gothic visual palette:
 * <ul>
 *   <li>Deep charcoal stone background: {@code #121013}</li>
 *   <li>Weathered bronze borders: {@code #8C7853}</li>
 *   <li>Muted gold selection glow: {@code #D4AF37}</li>
 * </ul>
 */
public class SoulsVaultScreen extends Screen {
    public static final ResourceLocation VAULT_BG_TEXTURE =
            new ResourceLocation(Constants.MOD_ID, "textures/gui/vault_bg.png");

    private static final int WINDOW_WIDTH = 400;
    private static final int WINDOW_HEIGHT = 236;

    private static final int LEFT_PANE_OFFSET_X = 8;
    private static final int LEFT_PANE_WIDTH = 166;
    private static final int RIGHT_PANE_OFFSET_X = 182;
    private static final int RIGHT_PANE_WIDTH = 210;

    private static final int SEARCH_BOX_WIDTH = 114;
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
    private static final int INV_COLS = 6;
    private static final int INV_ROWS = 4;
    private static final int INV_SLOT_STEP = 25;
    private static final int VAULT_SLOT_STEP = 26;

    private static final EquipmentSlot[] EQUIP_SLOTS = {
            EquipmentSlot.MAINHAND,
            EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };

    private static final String[] EQUIP_SLOT_HINTS = {
            "MH", "OH", "HD", "CH", "LG", "FT"
    };

    private int leftPos;
    private int topPos;

    private VaultCategory selectedCategory = VaultCategory.WEAPONS;
    private VaultSortMode sortMode = VaultSortMode.RECENTLY_ACQUIRED;
    private boolean inspectionMode = false;
    private boolean echoesDrawerOpen = false;
    private boolean filterInventoryByCategory = true;

    private final List<CategoryTabButton> categoryTabButtons = new ArrayList<>();
    private final List<VaultItemSlotWidget> vaultSlotWidgets = new ArrayList<>();
    private final List<VaultItemSlotWidget> equipmentSlotWidgets = new ArrayList<>();
    private final List<VaultItemSlotWidget> playerInvSlotWidgets = new ArrayList<>();
    private final List<Integer> filteredPlayerInvSlots = new ArrayList<>();

    private List<VaultEntry> filteredVaultEntries = new ArrayList<>();
    private int vaultScrollRow = 0;
    private int invScrollRow = 0;

    private EditBox searchBox;
    private GothicButton sortButton;
    private GothicButton inspectToggleButton;
    private GothicButton echoesToggleButton;
    private GothicButton invFilterButton;
    private ItemInspectionPane inspectionPane;

    private ItemKey selectedItemKey = ItemKey.EMPTY;
    @Nullable
    private RefillMiniPopover refillPopover = null;

    private final Runnable syncListener = this::refreshAllSlots;

    public SoulsVaultScreen() {
        super(Component.translatable("gui.soulsvault.title"));
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - WINDOW_WIDTH) / 2;
        this.topPos = (this.height - WINDOW_HEIGHT) / 2;

        int leftPaneX = this.leftPos + LEFT_PANE_OFFSET_X;
        int rightPaneX = this.leftPos + RIGHT_PANE_OFFSET_X;

        // 7 Category Tabs aligned directly above the 166px Left Pane (7 * 22 + 6 * 2 = 166px)
        this.categoryTabButtons.clear();
        VaultCategory[] categories = VaultCategory.values();
        int tabWidth = 22;
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

        // Borderless inner EditBox inside our custom gothic search bar frame
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

        // Sort Selector Button (GothicButton)
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

        // Header Build Echoes Drawer Button ('B' shortcut)
        this.echoesToggleButton = new GothicButton(
                this.leftPos + WINDOW_WIDTH - 172,
                this.topPos + 4,
                84,
                14,
                getEchoesButtonLabel(),
                btn -> toggleEchoesDrawer()
        );
        this.echoesToggleButton.setHighlighted(this.echoesDrawerOpen);
        addRenderableWidget(this.echoesToggleButton);

        // Header Inspect Mode Button ('R' shortcut)
        this.inspectToggleButton = new GothicButton(
                this.leftPos + WINDOW_WIDTH - 84,
                this.topPos + 4,
                76,
                14,
                getInspectButtonLabel(),
                btn -> toggleInspectionMode()
        );
        this.inspectToggleButton.setHighlighted(this.inspectionMode);
        addRenderableWidget(this.inspectToggleButton);

        // Left Pane: Inventory Filter Toggle Button (Category vs All Slots)
        this.invFilterButton = new GothicButton(
                leftPaneX + 84,
                this.topPos + 83,
                76,
                13,
                getInvFilterButtonLabel(),
                btn -> {
                    this.filterInventoryByCategory = !this.filterInventoryByCategory;
                    this.invScrollRow = 0;
                    btn.setMessage(getInvFilterButtonLabel());
                    refreshAllSlots();
                }
        );
        this.invFilterButton.visible = !this.echoesDrawerOpen;
        addRenderableWidget(this.invFilterButton);

        // Left Pane Bottom Action Buttons: Rest & Replenish + Quick Deposit Category
        int bottomBtnY = this.topPos + WINDOW_HEIGHT - 22;
        GothicButton restReplenishBtn = new GothicButton(
                leftPaneX,
                bottomBtnY,
                81,
                15,
                Component.translatable("gui.soulsvault.action.rest_replenish"),
                btn -> Services.PLATFORM.sendToServer(PacketC2SVaultAction.restReplenish())
        );
        addRenderableWidget(restReplenishBtn);

        GothicButton quickDepositBtn = new GothicButton(
                leftPaneX + 85,
                bottomBtnY,
                81,
                15,
                Component.translatable("gui.soulsvault.action.quick_deposit"),
                btn -> Services.PLATFORM.sendToServer(PacketC2SVaultAction.depositAllCategory(this.selectedCategory))
        );
        addRenderableWidget(quickDepositBtn);

        // Build virtualized slot widgets
        this.equipmentSlotWidgets.clear();
        int equipY = this.topPos + 54;
        for (int i = 0; i < EQUIP_SLOTS.length; i++) {
            VaultItemSlotWidget eqWidget = new VaultItemSlotWidget(
                    leftPaneX + 9 + i * INV_SLOT_STEP,
                    equipY,
                    VaultItemSlotWidget.SLOT_SIZE
            );
            eqWidget.setEmptyHint(EQUIP_SLOT_HINTS[i]);
            this.equipmentSlotWidgets.add(eqWidget);
        }

        this.playerInvSlotWidgets.clear();
        int invGridStartY = this.topPos + 99;
        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                this.playerInvSlotWidgets.add(new VaultItemSlotWidget(
                        leftPaneX + 9 + col * INV_SLOT_STEP,
                        invGridStartY + row * INV_SLOT_STEP,
                        VaultItemSlotWidget.SLOT_SIZE
                ));
            }
        }

        this.vaultSlotWidgets.clear();
        int vaultGridStartX = rightPaneX + 12;
        int vaultGridStartY = this.topPos + 56;
        for (int row = 0; row < VAULT_ROWS; row++) {
            for (int col = 0; col < VAULT_COLS; col++) {
                this.vaultSlotWidgets.add(new VaultItemSlotWidget(
                        vaultGridStartX + col * VAULT_SLOT_STEP,
                        vaultGridStartY + row * VAULT_SLOT_STEP,
                        VaultItemSlotWidget.SLOT_SIZE
                ));
            }
        }

        this.inspectionPane = new ItemInspectionPane(
                rightPaneX,
                this.topPos + 42,
                RIGHT_PANE_WIDTH,
                WINDOW_HEIGHT - 50
        );
        this.inspectionPane.setInspectedKey(this.selectedItemKey);

        ClientVaultCache.registerSyncListener(this.syncListener);
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

    private Component getEchoesButtonLabel() {
        return Component.translatable(
                this.echoesDrawerOpen ? "gui.soulsvault.action.echoes_close" : "gui.soulsvault.action.echoes_open"
        );
    }

    private Component getInvFilterButtonLabel() {
        return Component.translatable(
                this.filterInventoryByCategory ? "gui.soulsvault.action.filter_cat" : "gui.soulsvault.action.filter_all"
        );
    }

    private void selectCategory(VaultCategory category) {
        this.selectedCategory = category;
        this.vaultScrollRow = 0;
        this.invScrollRow = 0;
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
    }

    private void toggleEchoesDrawer() {
        this.echoesDrawerOpen = !this.echoesDrawerOpen;
        this.refillPopover = null;
        if (this.echoesToggleButton != null) {
            this.echoesToggleButton.setMessage(getEchoesButtonLabel());
            this.echoesToggleButton.setHighlighted(this.echoesDrawerOpen);
        }
        if (this.invFilterButton != null) {
            this.invFilterButton.visible = !this.echoesDrawerOpen;
        }
        refreshAllSlots();
    }

    public void refreshAllSlots() {
        Minecraft mc = Minecraft.getInstance();
        PlayerVaultData vaultData = ClientVaultCache.getVaultData();

        // Update category tab badge counts
        Map<VaultCategory, Integer> countsByCategory = new EnumMap<>(VaultCategory.class);
        for (VaultEntry entry : vaultData.getEntries()) {
            if (entry.shouldRetain()) {
                countsByCategory.merge(entry.getCategory(), 1, Integer::sum);
            }
        }
        for (CategoryTabButton tabBtn : this.categoryTabButtons) {
            tabBtn.setEntryCount(countsByCategory.getOrDefault(tabBtn.getCategory(), 0));
        }

        // Filter & sort vault entries for the active category
        List<VaultEntry> rawCategoryEntries = vaultData.getEntriesByCategory(this.selectedCategory);
        String query = this.searchBox != null ? this.searchBox.getValue().trim() : "";
        List<VaultEntry> matching = new ArrayList<>();
        for (VaultEntry entry : rawCategoryEntries) {
            if (matchesSearchQuery(mc, entry, query)) {
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

        // Bind player equipment & filtered inventory slots
        if (mc.player != null) {
            for (int i = 0; i < EQUIP_SLOTS.length && i < this.equipmentSlotWidgets.size(); i++) {
                ItemStack equipped = mc.player.getItemBySlot(EQUIP_SLOTS[i]);
                ItemKey eqKey = ItemKey.fromStack(equipped);
                this.equipmentSlotWidgets.get(i).bindItemStack(equipped, !eqKey.isEmpty() && eqKey.equals(this.selectedItemKey));
            }

            Inventory inv = mc.player.getInventory();
            this.filteredPlayerInvSlots.clear();
            for (int slotIdx = 0; slotIdx < inv.items.size(); slotIdx++) {
                ItemStack stack = inv.items.get(slotIdx);
                if (stack.isEmpty()) {
                    continue;
                }
                if (!this.filterInventoryByCategory || VaultCategory.classify(stack) == this.selectedCategory) {
                    this.filteredPlayerInvSlots.add(slotIdx);
                }
            }

            int totalInvRows = Math.max(1, (this.filteredPlayerInvSlots.size() + INV_COLS - 1) / INV_COLS);
            int maxInvScroll = Math.max(0, totalInvRows - INV_ROWS);
            this.invScrollRow = Mth.clamp(this.invScrollRow, 0, maxInvScroll);

            int startInvIdx = this.invScrollRow * INV_COLS;
            for (int i = 0; i < this.playerInvSlotWidgets.size(); i++) {
                VaultItemSlotWidget widget = this.playerInvSlotWidgets.get(i);
                int listIdx = startInvIdx + i;
                if (listIdx < this.filteredPlayerInvSlots.size()) {
                    int invSlot = this.filteredPlayerInvSlots.get(listIdx);
                    ItemStack stack = inv.items.get(invSlot);
                    ItemKey key = ItemKey.fromStack(stack);
                    widget.bindItemStack(stack, !key.isEmpty() && key.equals(this.selectedItemKey));
                } else {
                    widget.bindItemStack(ItemStack.EMPTY, false);
                }
            }
        }
    }

    private boolean matchesSearchQuery(Minecraft mc, VaultEntry entry, String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return true;
        }
        String query = rawQuery.toLowerCase(Locale.ROOT);
        ItemKey key = entry.getKey();
        ItemStack stack = entry.createDisplayStack();

        // @mod namespace search
        if (query.startsWith("@")) {
            String modQuery = query.substring(1).trim();
            return key.getNamespace().toLowerCase(Locale.ROOT).contains(modQuery);
        }

        // #tag or #tooltip lore search
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

        // Default item name & registry path search
        String displayName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        if (displayName.contains(query)) {
            return true;
        }
        return key.getRegistryId().getPath().toLowerCase(Locale.ROOT).contains(query);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        // Outer gothic charcoal window background + weathered bronze double-frame
        graphics.fill(this.leftPos, this.topPos, this.leftPos + WINDOW_WIDTH, this.topPos + WINDOW_HEIGHT, COLOR_CHARCOAL_BG);
        VaultItemSlotWidget.drawBorder(graphics, this.leftPos, this.topPos, WINDOW_WIDTH, WINDOW_HEIGHT, COLOR_BRONZE);
        VaultItemSlotWidget.drawBorder(graphics, this.leftPos + 2, this.topPos + 2, WINDOW_WIDTH - 4, WINDOW_HEIGHT - 4, COLOR_BRONZE_MUTED);

        int leftPaneX = this.leftPos + LEFT_PANE_OFFSET_X;
        int rightPaneX = this.leftPos + RIGHT_PANE_OFFSET_X;
        int paneTopY = this.topPos + 42;
        int leftPaneHeight = WINDOW_HEIGHT - 68;

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

        String leftSub = this.font.plainSubstrByWidth(
                Component.translatable("gui.soulsvault.subtitle.inventory").getString(),
                LEFT_PANE_WIDTH - 14
        );
        graphics.drawString(this.font, leftSub, leftPaneX + 7, paneTopY + 3, COLOR_TEXT_SUB, false);

        // Render Equipment Strip & either Build Echoes Drawer or Player Inventory Grid
        for (VaultItemSlotWidget eqSlot : this.equipmentSlotWidgets) {
            eqSlot.render(graphics, mouseX, mouseY);
        }

        graphics.fill(leftPaneX + 6, this.topPos + 80, leftPaneX + LEFT_PANE_WIDTH - 6, this.topPos + 81, COLOR_BRONZE_MUTED);
        if (this.echoesDrawerOpen) {
            renderBuildEchoesDrawer(graphics, leftPaneX, this.topPos + 83, LEFT_PANE_WIDTH, mouseX, mouseY);
        } else {
            String catLabel = this.font.plainSubstrByWidth(this.selectedCategory.getDisplayName().getString(), 74);
            graphics.drawString(this.font, catLabel, leftPaneX + 7, this.topPos + 86, COLOR_GOLD, false);

            for (VaultItemSlotWidget invSlot : this.playerInvSlotWidgets) {
                invSlot.render(graphics, mouseX, mouseY);
            }
        }

        // Right Pane: either Virtualized Vault Grid or Detailed ItemInspectionPane
        if (this.inspectionMode) {
            this.inspectionPane.setInspectedKey(this.selectedItemKey);
            this.inspectionPane.render(graphics, mouseX, mouseY, partialTick);
        } else {
            int vaultPaneH = WINDOW_HEIGHT - 50;
            graphics.fill(rightPaneX, paneTopY, rightPaneX + RIGHT_PANE_WIDTH, paneTopY + vaultPaneH, COLOR_PANE_BG);
            VaultItemSlotWidget.drawBorder(graphics, rightPaneX, paneTopY, RIGHT_PANE_WIDTH, vaultPaneH, COLOR_BRONZE_MUTED);

            String headerRight = this.selectedCategory.getDisplayName().getString()
                    + " (" + this.filteredVaultEntries.size() + ")";
            graphics.drawString(this.font, headerRight, rightPaneX + 10, paneTopY + 4, COLOR_GOLD, false);

            for (VaultItemSlotWidget vaultSlot : this.vaultSlotWidgets) {
                vaultSlot.render(graphics, mouseX, mouseY);
            }

            // Vault scrollbar track
            int totalVaultRows = Math.max(1, (this.filteredVaultEntries.size() + VAULT_COLS - 1) / VAULT_COLS);
            if (totalVaultRows > VAULT_ROWS) {
                int trackX = rightPaneX + RIGHT_PANE_WIDTH - 6;
                int trackY = this.topPos + 56;
                int trackH = VAULT_ROWS * VAULT_SLOT_STEP - 2;
                graphics.fill(trackX, trackY, trackX + 2, trackY + trackH, 0x802A241C);
                int maxScroll = totalVaultRows - VAULT_ROWS;
                int thumbH = Math.max(14, (trackH * VAULT_ROWS) / totalVaultRows);
                int thumbY = trackY + ((trackH - thumbH) * this.vaultScrollRow) / maxScroll;
                graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, COLOR_GOLD);
            }
        }

        // Render standard widgets (Category Tabs, Search Box, GothicButtons)
        super.render(graphics, mouseX, mouseY, partialTick);

        // Render Shift + Right-Click Refill Popover if open
        if (this.refillPopover != null) {
            this.refillPopover.render(graphics, this.font, mouseX, mouseY);
        } else {
            renderHoveredTooltips(graphics, mouseX, mouseY);
        }
    }

    private void renderHoveredTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        // Category tab hover tooltips
        for (CategoryTabButton tab : this.categoryTabButtons) {
            if (tab.isMouseOver(mouseX, mouseY)) {
                graphics.renderTooltip(this.font, tab.getCategory().getDisplayName(), mouseX, mouseY);
                return;
            }
        }

        // Vault slot hover tooltips
        if (!this.inspectionMode) {
            for (VaultItemSlotWidget slot : this.vaultSlotWidgets) {
                if (!slot.isEmpty() && slot.isMouseOver(mouseX, mouseY)) {
                    VaultEntry entry = slot.getEntry();
                    ItemStack stack = slot.getDisplayStack();
                    List<Component> tooltip = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), stack));
                    if (entry != null) {
                        tooltip.add(Component.empty());
                        tooltip.add(Component.translatable("gui.soulsvault.inspect.stored_count", entry.getCount())
                                .withStyle(ChatFormatting.GOLD));
                        if (entry.isAutoRefill() || entry.isOverflowDeposit()) {
                            tooltip.add(Component.translatable("gui.soulsvault.inspect.refill_target", entry.getRefillTarget())
                                    .withStyle(ChatFormatting.AQUA));
                        }
                        tooltip.add(Component.translatable("gui.soulsvault.tooltip.vault_controls")
                                .withStyle(ChatFormatting.DARK_GRAY));
                    }
                    graphics.renderTooltip(this.font, tooltip, stack.getTooltipImage(), mouseX, mouseY);
                    return;
                }
            }
        }

        // Player inventory slot hover tooltips
        if (!this.echoesDrawerOpen) {
            for (VaultItemSlotWidget slot : this.playerInvSlotWidgets) {
                if (!slot.isEmpty() && slot.isMouseOver(mouseX, mouseY)) {
                    ItemStack stack = slot.getDisplayStack();
                    List<Component> tooltip = new ArrayList<>(getTooltipFromItem(Minecraft.getInstance(), stack));
                    tooltip.add(Component.empty());
                    tooltip.add(Component.translatable("gui.soulsvault.tooltip.inv_controls")
                            .withStyle(ChatFormatting.DARK_GRAY));
                    graphics.renderTooltip(this.font, tooltip, stack.getTooltipImage(), mouseX, mouseY);
                    return;
                }
            }
        }

        // Equipment slot hover tooltips
        for (VaultItemSlotWidget slot : this.equipmentSlotWidgets) {
            if (!slot.isEmpty() && slot.isMouseOver(mouseX, mouseY)) {
                ItemStack stack = slot.getDisplayStack();
                graphics.renderTooltip(this.font, stack, mouseX, mouseY);
                return;
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
        graphics.drawString(
                this.font,
                Component.translatable("gui.soulsvault.subtitle.echoes"),
                paneX + 7,
                startY + 2,
                COLOR_GOLD,
                false
        );

        List<BuildEcho> echoes = ClientVaultCache.getVaultData().getBuildEchoes();
        int rowStartY = startY + 14;
        int rowHeight = 21;
        int cardX = paneX + 5;
        int cardW = paneW - 10;

        for (int i = 0; i < Constants.MAX_BUILD_ECHOES; i++) {
            int ry = rowStartY + i * rowHeight;
            BuildEcho echo = i < echoes.size() ? echoes.get(i) : null;

            graphics.fill(cardX, ry, cardX + cardW, ry + rowHeight - 2, 0xF016131A);
            VaultItemSlotWidget.drawBorder(graphics, cardX, ry, cardW, rowHeight - 2, COLOR_BRONZE_MUTED);

            String label = "#" + (i + 1);
            graphics.drawString(this.font, label, cardX + 3, ry + 5, COLOR_GOLD, false);

            // Render mini 11x11 icons for Mainhand, Offhand, and 4 Armor pieces
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
                    int ix = iconBaseX + k * 12;
                    int iy = ry + 3;
                    graphics.fill(ix, iy, ix + 11, iy + 13, 0xFF0D0B0E);
                    if (!ik.isEmpty()) {
                        graphics.pose().pushPose();
                        graphics.pose().translate(ix - 0.5F, iy + 0.5F, 100.0F);
                        graphics.pose().scale(0.68F, 0.68F, 1.0F);
                        graphics.renderItem(ik.toSingleStack(), 0, 0);
                        graphics.pose().popPose();
                    }
                }
            } else {
                graphics.drawString(this.font, "Empty Preset", iconBaseX + 2, ry + 5, 0xFF685F54, false);
            }

            // Render [Save] and [Swap] buttons on the right side of the row
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
        // First handle active Refill Mini-Popover clicks
        if (this.refillPopover != null) {
            if (this.refillPopover.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
            this.refillPopover = null;
        }

        // Clicking anywhere inside the custom search box frame focuses the search box
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

        // Handle Inspection Pane clicks when active
        if (this.inspectionMode && this.inspectionPane != null) {
            if (this.inspectionPane.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }

        // Handle Vault Grid Slot clicks when in Grid Mode
        if (!this.inspectionMode) {
            for (VaultItemSlotWidget slot : this.vaultSlotWidgets) {
                if (!slot.isEmpty() && slot.isMouseOver(mouseX, mouseY)) {
                    VaultEntry entry = slot.getEntry();
                    if (entry != null) {
                        handleVaultSlotClick(entry, slot.getX(), slot.getY(), button);
                        return true;
                    }
                }
            }
        }

        // Handle Build Echoes Drawer clicks or Player Inventory Slot clicks (LeftPane)
        if (this.echoesDrawerOpen) {
            if (handleBuildEchoesDrawerClick(mouseX, mouseY, button)) {
                return true;
            }
        } else {
            for (VaultItemSlotWidget slot : this.playerInvSlotWidgets) {
                if (!slot.isEmpty() && slot.isMouseOver(mouseX, mouseY)) {
                    handlePlayerInventorySlotClick(slot.getDisplayStack(), button);
                    return true;
                }
            }
        }

        // Handle Equipment Slot selection for Inspection
        for (VaultItemSlotWidget slot : this.equipmentSlotWidgets) {
            if (!slot.isEmpty() && slot.isMouseOver(mouseX, mouseY)) {
                this.selectedItemKey = ItemKey.fromStack(slot.getDisplayStack());
                if (this.inspectionPane != null) {
                    this.inspectionPane.setInspectedKey(this.selectedItemKey);
                }
                refreshAllSlots();
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleBuildEchoesDrawerClick(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        int paneX = this.leftPos + LEFT_PANE_OFFSET_X;
        int paneW = LEFT_PANE_WIDTH;
        int startY = this.topPos + 83;
        int rowStartY = startY + 14;
        int rowHeight = 21;
        int cardX = paneX + 5;
        int cardW = paneW - 10;

        int saveBtnX = cardX + cardW - 64;
        int loadBtnX = cardX + cardW - 32;
        int btnW = 30;
        int btnH = 13;

        for (int i = 0; i < Constants.MAX_BUILD_ECHOES; i++) {
            int ry = rowStartY + i * rowHeight;
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

    private void handleVaultSlotClick(VaultEntry entry, int slotX, int slotY, int button) {
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
            int popX = Mth.clamp(slotX - 40, this.leftPos + RIGHT_PANE_OFFSET_X + 2, this.leftPos + WINDOW_WIDTH - 138);
            int popY = Mth.clamp(slotY + 24, this.topPos + 46, this.topPos + WINDOW_HEIGHT - 68);
            this.refillPopover = new RefillMiniPopover(popX, popY, key);
            refreshAllSlots();
            return;
        }

        int maxStack = Math.max(1, key.getItem().getMaxStackSize());
        if (button == 0) {
            // Left-Click: Withdraw 1 (or full stack if Shift held)
            long amount = hasShiftDown() ? maxStack : 1L;
            Services.PLATFORM.sendToServer(PacketC2SVaultAction.withdraw(key, amount));
        } else if (button == 1) {
            // Right-Click: Withdraw half stack
            long amount = Math.max(1, maxStack / 2);
            Services.PLATFORM.sendToServer(PacketC2SVaultAction.withdraw(key, amount));
        }
        refreshAllSlots();
    }

    private void handlePlayerInventorySlotClick(ItemStack stack, int button) {
        ItemKey key = ItemKey.fromStack(stack);
        if (key.isEmpty()) {
            return;
        }
        this.selectedItemKey = key;
        if (this.inspectionPane != null) {
            this.inspectionPane.setInspectedKey(key);
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));

        if (button == 2) {
            Services.PLATFORM.sendToServer(PacketC2SVaultAction.toggleFavorite(key));
            return;
        }

        if (button == 0) {
            long amount = hasShiftDown() ? Constants.DEFAULT_MAX_STACK_CAPACITY : stack.getCount();
            Services.PLATFORM.sendToServer(PacketC2SVaultAction.deposit(key, amount));
        } else if (button == 1) {
            long amount = Math.max(1, stack.getCount() / 2);
            Services.PLATFORM.sendToServer(PacketC2SVaultAction.deposit(key, amount));
        }
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
        } else {
            int totalInvRows = Math.max(1, (this.filteredPlayerInvSlots.size() + INV_COLS - 1) / INV_COLS);
            int maxInvScroll = Math.max(0, totalInvRows - INV_ROWS);
            this.invScrollRow = Mth.clamp(this.invScrollRow + step, 0, maxInvScroll);
            refreshAllSlots();
            return true;
        }
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

        // 'R' key toggles the Detailed Item Inspection Pane
        if (keyCode == InputConstants.KEY_R) {
            updateHoveredSelectionBeforeInspect();
            toggleInspectionMode();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        // 'B' key toggles the Build Echoes Loadout Drawer
        if (keyCode == InputConstants.KEY_B) {
            toggleEchoesDrawer();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void updateHoveredSelectionBeforeInspect() {
        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() * (double) this.width / (double) mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * (double) this.height / (double) mc.getWindow().getScreenHeight();

        for (VaultItemSlotWidget slot : this.vaultSlotWidgets) {
            if (!slot.isEmpty() && slot.isMouseOver(mouseX, mouseY) && slot.getEntry() != null) {
                this.selectedItemKey = slot.getEntry().getKey();
                return;
            }
        }
        for (VaultItemSlotWidget slot : this.playerInvSlotWidgets) {
            if (!slot.isEmpty() && slot.isMouseOver(mouseX, mouseY)) {
                this.selectedItemKey = ItemKey.fromStack(slot.getDisplayStack());
                return;
            }
        }
        for (VaultItemSlotWidget slot : this.equipmentSlotWidgets) {
            if (!slot.isEmpty() && slot.isMouseOver(mouseX, mouseY)) {
                this.selectedItemKey = ItemKey.fromStack(slot.getDisplayStack());
                return;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
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

            // Row 1: Toggle Auto-Refill
            if (mouseY >= this.y + 16 && mouseY < this.y + 28) {
                Services.PLATFORM.sendToServer(PacketC2SVaultAction.setRefillTarget(this.key, target, !autoRefill, overflow));
                return true;
            }
            // Row 2: Toggle Pickup Overflow
            if (mouseY >= this.y + 29 && mouseY < this.y + 41) {
                Services.PLATFORM.sendToServer(PacketC2SVaultAction.setRefillTarget(this.key, target, autoRefill, !overflow));
                return true;
            }
            // Row 3: -16 | -1 | +1 | +16
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

            // Auto-Refill toggle row
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

            // Overflow toggle row
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

            // Target adjustment buttons (-16, -1, +1, +16)
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
