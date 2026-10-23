package com.soulsvault.client;

import com.soulsvault.client.gui.SoulsVaultScreen;
import com.soulsvault.core.ItemKey;
import com.soulsvault.network.PacketC2SFillCraftingGrid;
import com.soulsvault.platform.Services;
import com.soulsvault.world.inventory.CraftingGridPlanner;
import com.soulsvault.world.inventory.SoulsVaultMenu;
import com.soulsvault.world.inventory.SoulsVaultMenu.LeftPaneMode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Client-side bridge used by the JEI / REI / EMI plugins to fill the Casket's 3x3 crafting grid from a recipe.
 * <p>
 * Plugins only translate their recipe display into per-slot ingredient alternatives (row-major 3x3, one list of
 * acceptable stacks per slot, empty for slots without an ingredient); availability checks and the actual transfer
 * (Vault first when allowed, then inventory) are shared here and in {@link SoulsVaultMenu}.
 */
public final class VaultRecipeTransfer {
    /** Crafts requested for "max" transfers (JEI/REI shift-click); further capped by ingredients and stack sizes. */
    public static final int MAX_CRAFTS = PacketC2SFillCraftingGrid.MAX_REQUESTED_CRAFTS;

    private VaultRecipeTransfer() {
    }

    /**
     * Everything the player could fill the grid with right now: inventory, grid contents, and the Vault
     * (from the client cache) when the open menu allows Vault access.
     */
    public static CraftingGridPlanner.Availability availability(SoulsVaultMenu menu, Player player) {
        return CraftingGridPlanner.collectAvailable(
                player,
                menu.getCraftingGrid(),
                menu.isVaultAccessAllowed() ? ClientVaultCache.getVaultData() : null
        );
    }

    /**
     * Plans a single craft so recipe viewers can show which slots are missing.
     */
    public static CraftingGridPlanner.Plan plan(SoulsVaultMenu menu, List<List<ItemStack>> slotAlternatives) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return CraftingGridPlanner.plan(new CraftingGridPlanner.Availability(), List.of(), 1);
        }
        CraftingGridPlanner.Availability availability = availability(menu, player);
        return CraftingGridPlanner.plan(
                availability,
                CraftingGridPlanner.resolveCandidates(availability, slotAlternatives),
                1
        );
    }

    /**
     * Switches the left pane to the 3x3 grid and asks the server to fill it for {@code requestedCrafts} crafts.
     *
     * @return {@code false} if the player cannot currently fill the recipe
     */
    public static boolean transfer(SoulsVaultMenu menu, List<List<ItemStack>> slotAlternatives, int requestedCrafts) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return false;
        }
        CraftingGridPlanner.Availability availability = availability(menu, mc.player);
        List<List<ItemKey>> candidates = CraftingGridPlanner.resolveCandidates(availability, slotAlternatives);
        if (!CraftingGridPlanner.plan(availability, candidates, requestedCrafts).canFill()) {
            return false;
        }

        if (mc.screen instanceof SoulsVaultScreen screen && screen.getMenu() == menu) {
            screen.setLeftPaneMode(LeftPaneMode.CRAFTING);
        } else if (menu.getLeftPaneMode() != LeftPaneMode.CRAFTING) {
            // The recipe viewer's own screen is in front; the Casket screen re-reads the mode when it returns.
            menu.setLeftPaneMode(LeftPaneMode.CRAFTING);
            mc.gameMode.handleInventoryButtonClick(menu.containerId, SoulsVaultMenu.leftPaneButtonId(LeftPaneMode.CRAFTING));
        }
        Services.PLATFORM.sendToServer(new PacketC2SFillCraftingGrid(candidates, requestedCrafts));
        return true;
    }
}
