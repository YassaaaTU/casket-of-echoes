package com.soulsvault.forge.compat.jei;

import com.soulsvault.Constants;
import com.soulsvault.client.VaultRecipeTransfer;
import com.soulsvault.client.gui.SoulsVaultScreen;
import com.soulsvault.client.gui.widget.VaultItemSlotWidget;
import com.soulsvault.forge.registry.ForgeModBlocks;
import com.soulsvault.world.inventory.CraftingGridPlanner;
import com.soulsvault.world.inventory.SoulsVaultMenu;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiClickableArea;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JEI integration (discovered via {@link JeiPlugin}; never loaded without JEI):
 * <ul>
 *   <li>"+" recipe transfer into the Casket's 3x3 grid, pulling ingredients from the Vault and inventory</li>
 *   <li>Show Recipe / Show Uses / Bookmark on hovered Vault entries</li>
 *   <li>Clickable crafting arrow and the Bottomless Casket as a crafting catalyst</li>
 * </ul>
 */
@JeiPlugin
public class SoulsVaultJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_UID = new ResourceLocation(Constants.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                new VaultCraftingTransferHandler(registration.getTransferHelper()),
                RecipeTypes.CRAFTING
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ForgeModBlocks.BOTTOMLESS_CASKET_ITEM.get()), RecipeTypes.CRAFTING);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(
                SoulsVaultScreen.class,
                new VaultScreenHandler(registration.getJeiHelpers().getIngredientManager())
        );
    }

    private static final class VaultCraftingTransferHandler implements IRecipeTransferHandler<SoulsVaultMenu, CraftingRecipe> {
        private final IRecipeTransferHandlerHelper helper;

        VaultCraftingTransferHandler(IRecipeTransferHandlerHelper helper) {
            this.helper = helper;
        }

        @Override
        public Class<? extends SoulsVaultMenu> getContainerClass() {
            return SoulsVaultMenu.class;
        }

        @Override
        public Optional<MenuType<SoulsVaultMenu>> getMenuType() {
            return Optional.of(ForgeModBlocks.VAULT_MENU.get());
        }

        @Override
        public RecipeType<CraftingRecipe> getRecipeType() {
            return RecipeTypes.CRAFTING;
        }

        @Override
        @Nullable
        public IRecipeTransferError transferRecipe(
                SoulsVaultMenu menu,
                CraftingRecipe recipe,
                IRecipeSlotsView recipeSlots,
                Player player,
                boolean maxTransfer,
                boolean doTransfer
        ) {
            // JEI's crafting category always lays out 9 input slots in row-major grid order.
            List<IRecipeSlotView> inputs = recipeSlots.getSlotViews(RecipeIngredientRole.INPUT);
            List<List<ItemStack>> grid = new ArrayList<>(inputs.size());
            for (IRecipeSlotView input : inputs) {
                grid.add(input.getItemStacks().toList());
            }

            CraftingGridPlanner.Plan plan = VaultRecipeTransfer.plan(menu, grid);
            if (!plan.missingSlots().isEmpty()) {
                List<IRecipeSlotView> missing = new ArrayList<>();
                for (int slot : plan.missingSlots()) {
                    if (slot < inputs.size()) {
                        missing.add(inputs.get(slot));
                    }
                }
                return this.helper.createUserErrorForMissingSlots(
                        Component.translatable("gui.soulsvault.transfer.missing"),
                        missing
                );
            }
            if (!plan.canFill()) {
                return this.helper.createInternalError();
            }
            if (doTransfer) {
                VaultRecipeTransfer.transfer(menu, grid, maxTransfer ? VaultRecipeTransfer.MAX_CRAFTS : 1);
            }
            return null;
        }
    }

    private static final class VaultScreenHandler implements IGuiContainerHandler<SoulsVaultScreen> {
        private final IIngredientManager ingredientManager;

        VaultScreenHandler(IIngredientManager ingredientManager) {
            this.ingredientManager = ingredientManager;
        }

        @Override
        public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(
                SoulsVaultScreen screen,
                double mouseX,
                double mouseY
        ) {
            VaultItemSlotWidget slot = screen.getVaultSlotAt(mouseX, mouseY);
            if (slot == null) {
                return Optional.empty();
            }
            Rect2i area = new Rect2i(slot.getX(), slot.getY(), slot.getWidth(), slot.getHeight());
            return this.ingredientManager
                    .createTypedIngredient(VanillaTypes.ITEM_STACK, slot.getDisplayStack().copyWithCount(1))
                    .map(typed -> new ClickableVaultIngredient<>(typed, area));
        }

        @Override
        public Collection<IGuiClickableArea> getGuiClickableAreas(SoulsVaultScreen screen, double guiMouseX, double guiMouseY) {
            // Uses the screen's own mouse position: The Aether's JEI mixin passes guiMouseX as guiMouseY when JEI
            // draws click-area tooltips, which would show "Show Recipes" in a whole column above and below the arrow.
            if (!screen.isMouseOverCraftArrow()) {
                return List.of();
            }
            return List.of(IGuiClickableArea.createBasic(
                    SoulsVaultScreen.CRAFT_ARROW_X,
                    SoulsVaultScreen.CRAFT_ARROW_Y,
                    SoulsVaultScreen.CRAFT_ARROW_WIDTH,
                    SoulsVaultScreen.CRAFT_ARROW_HEIGHT,
                    RecipeTypes.CRAFTING
            ));
        }
    }

    private record ClickableVaultIngredient<T>(ITypedIngredient<T> typedIngredient, Rect2i area)
            implements IClickableIngredient<T> {
        // Still abstract across every JEI 15.x release for 1.20.1, so it must be implemented despite the deprecation.
        @SuppressWarnings("removal")
        @Override
        public ITypedIngredient<T> getTypedIngredient() {
            return this.typedIngredient;
        }

        @Override
        public Rect2i getArea() {
            return this.area;
        }
    }
}
