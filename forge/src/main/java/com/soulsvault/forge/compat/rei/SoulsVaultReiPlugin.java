package com.soulsvault.forge.compat.rei;

import com.soulsvault.client.VaultRecipeTransfer;
import com.soulsvault.client.gui.SoulsVaultScreen;
import com.soulsvault.client.gui.widget.VaultItemSlotWidget;
import com.soulsvault.forge.registry.ForgeModBlocks;
import com.soulsvault.world.inventory.CraftingGridPlanner;
import com.soulsvault.world.inventory.SoulsVaultMenu;
import dev.architectury.event.CompoundEventResult;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.screen.ScreenRegistry;
import me.shedaniel.rei.api.client.registry.screen.SimpleClickArea;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandler;
import me.shedaniel.rei.api.client.registry.transfer.TransferHandlerRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import me.shedaniel.rei.plugin.common.BuiltinPlugin;
import me.shedaniel.rei.plugin.common.displays.crafting.DefaultCraftingDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * REI integration (discovered via {@link REIPluginClient}; never loaded without REI):
 * <ul>
 *   <li>"+" recipe transfer into the Casket's 3x3 grid, pulling ingredients from the Vault and inventory</li>
 *   <li>Recipe / usage / favorite lookups on hovered Vault entries</li>
 *   <li>Clickable crafting arrow and the Bottomless Casket as a crafting workstation</li>
 * </ul>
 */
@REIPluginClient
public class SoulsVaultReiPlugin implements REIClientPlugin {

    @Override
    public void registerCategories(CategoryRegistry registry) {
        registry.addWorkstations(BuiltinPlugin.CRAFTING, EntryStacks.of(ForgeModBlocks.BOTTOMLESS_CASKET_ITEM.get()));
    }

    @Override
    public void registerScreens(ScreenRegistry registry) {
        registry.registerFocusedStack((screen, mouse) -> {
            if (screen instanceof SoulsVaultScreen vaultScreen) {
                VaultItemSlotWidget slot = vaultScreen.getVaultSlotAt(mouse.x, mouse.y);
                if (slot != null) {
                    return CompoundEventResult.interruptTrue(EntryStacks.of(slot.getDisplayStack().copyWithCount(1)));
                }
            }
            return CompoundEventResult.pass();
        });

        SimpleClickArea<SoulsVaultScreen> craftingArrow = screen -> screen.isCraftingPaneActive()
                ? new Rectangle(
                        SoulsVaultScreen.CRAFT_ARROW_X,
                        SoulsVaultScreen.CRAFT_ARROW_Y,
                        SoulsVaultScreen.CRAFT_ARROW_WIDTH,
                        SoulsVaultScreen.CRAFT_ARROW_HEIGHT)
                : new Rectangle();
        registry.registerContainerClickArea(craftingArrow, SoulsVaultScreen.class, BuiltinPlugin.CRAFTING);
    }

    @Override
    public void registerTransferHandlers(TransferHandlerRegistry registry) {
        registry.register(new VaultCraftingTransferHandler());
    }

    private static final class VaultCraftingTransferHandler implements TransferHandler {
        @Override
        public Result handle(Context context) {
            if (!(context.getMenu() instanceof SoulsVaultMenu menu)
                    || !(context.getDisplay() instanceof DefaultCraftingDisplay<?> display)) {
                return Result.createNotApplicable();
            }

            List<EntryIngredient> inputs = display.getOrganisedInputEntries(3, 3);
            List<List<ItemStack>> grid = new ArrayList<>(inputs.size());
            for (EntryIngredient input : inputs) {
                List<ItemStack> alternatives = new ArrayList<>();
                for (EntryStack<?> entry : input) {
                    if (entry.getValue() instanceof ItemStack itemStack && !itemStack.isEmpty()) {
                        alternatives.add(itemStack);
                    }
                }
                grid.add(alternatives);
            }

            CraftingGridPlanner.Plan plan = VaultRecipeTransfer.plan(menu, grid);
            if (!plan.missingSlots().isEmpty()) {
                List<EntryIngredient> missing = new ArrayList<>();
                for (int slot : plan.missingSlots()) {
                    if (slot < inputs.size()) {
                        missing.add(inputs.get(slot));
                    }
                }
                return Result.createFailed(Component.translatable("gui.soulsvault.transfer.missing")).tooltipMissing(missing);
            }
            if (!plan.canFill()) {
                return Result.createNotApplicable();
            }
            if (context.isActuallyCrafting()) {
                VaultRecipeTransfer.transfer(menu, grid, context.isStackedCrafting() ? VaultRecipeTransfer.MAX_CRAFTS : 1);
            }
            return Result.createSuccessful().blocksFurtherHandling();
        }
    }
}
