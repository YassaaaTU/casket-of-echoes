package com.soulsvault.forge.compat.emi;

import com.soulsvault.client.VaultRecipeTransfer;
import com.soulsvault.client.gui.SoulsVaultScreen;
import com.soulsvault.client.gui.widget.VaultItemSlotWidget;
import com.soulsvault.core.ItemKey;
import com.soulsvault.forge.registry.ForgeModBlocks;
import com.soulsvault.world.inventory.SoulsVaultMenu;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EMI integration (discovered via {@link EmiEntrypoint}; never loaded without EMI):
 * <ul>
 *   <li>Recipe fill into the Casket's 3x3 grid, pulling ingredients from the Vault and inventory</li>
 *   <li>EMI's "craftable" filter and recipe tree count Vault contents as owned items</li>
 *   <li>Recipe / usage / favorite lookups on hovered Vault entries</li>
 *   <li>The Bottomless Casket as a crafting workstation</li>
 * </ul>
 */
@EmiEntrypoint
public class SoulsVaultEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        registry.addWorkstation(VanillaEmiRecipeCategories.CRAFTING, EmiStack.of(ForgeModBlocks.BOTTOMLESS_CASKET_ITEM.get()));
        registry.addRecipeHandler(ForgeModBlocks.VAULT_MENU.get(), new VaultCraftingRecipeHandler());
        registry.addStackProvider(SoulsVaultScreen.class, (screen, mouseX, mouseY) -> {
            VaultItemSlotWidget slot = screen.getVaultSlotAt(mouseX, mouseY);
            return slot == null
                    ? EmiStackInteraction.EMPTY
                    : new EmiStackInteraction(EmiStack.of(slot.getDisplayStack().copyWithCount(1)));
        });
    }

    private static final class VaultCraftingRecipeHandler implements EmiRecipeHandler<SoulsVaultMenu> {

        @Override
        public EmiPlayerInventory getInventory(AbstractContainerScreen<SoulsVaultMenu> screen) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
                return new EmiPlayerInventory(List.of());
            }
            List<EmiStack> stacks = new ArrayList<>();
            for (Map.Entry<ItemKey, Long> owned : VaultRecipeTransfer.availability(screen.getMenu(), mc.player).counts().entrySet()) {
                stacks.add(EmiStack.of(owned.getKey().toSingleStack(), owned.getValue()));
            }
            return new EmiPlayerInventory(stacks);
        }

        @Override
        public boolean supportsRecipe(EmiRecipe recipe) {
            return recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING && recipe.supportsRecipeTree();
        }

        @Override
        public boolean canCraft(EmiRecipe recipe, EmiCraftContext<SoulsVaultMenu> context) {
            return VaultRecipeTransfer.plan(context.getScreenHandler(), toGrid(recipe)).canFill();
        }

        @Override
        public boolean craft(EmiRecipe recipe, EmiCraftContext<SoulsVaultMenu> context) {
            boolean filled = VaultRecipeTransfer.transfer(
                    context.getScreenHandler(),
                    toGrid(recipe),
                    Math.max(1, context.getAmount())
            );
            if (filled) {
                Minecraft.getInstance().setScreen(context.getScreen());
            }
            return filled;
        }

        /**
         * EMI pads shaped crafting inputs to 3x3, so input index equals grid slot.
         */
        private static List<List<ItemStack>> toGrid(EmiRecipe recipe) {
            List<List<ItemStack>> grid = new ArrayList<>(9);
            for (EmiIngredient input : recipe.getInputs()) {
                List<ItemStack> alternatives = new ArrayList<>();
                for (EmiStack stack : input.getEmiStacks()) {
                    ItemStack itemStack = stack.getItemStack();
                    if (!itemStack.isEmpty()) {
                        alternatives.add(itemStack);
                    }
                }
                grid.add(alternatives);
            }
            return grid;
        }
    }
}
