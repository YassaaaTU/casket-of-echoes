package com.soulsvault.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.soulsvault.Constants;
import com.soulsvault.core.RegistryLookups;
import com.soulsvault.world.entity.SpectralCasketEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the temporary {@link SpectralCasketEntity} as a levitating, slowly rotating
 * full-bright Casket of Echoes in the field.
 */
public class SpectralCasketRenderer extends EntityRenderer<SpectralCasketEntity> {
    private static final ResourceLocation CASKET_ITEM_ID =
            new ResourceLocation(Constants.MOD_ID, "bottomless_casket");

    private ItemStack cachedRenderStack = ItemStack.EMPTY;

    public SpectralCasketRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.35F;
    }

    @Override
    public void render(
            SpectralCasketEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight
    ) {
        if (this.cachedRenderStack.isEmpty()) {
            this.cachedRenderStack = RegistryLookups.item(CASKET_ITEM_ID).map(ItemStack::new).orElse(ItemStack.EMPTY);
        }

        float ageInTicks = entity.tickCount + partialTick;
        float bobOffset = Mth.sin(ageInTicks * 0.1F) * 0.08F;
        float rotationDegrees = ageInTicks * 2.2F;

        poseStack.pushPose();
        poseStack.translate(0.0D, 0.45D + bobOffset, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationDegrees));
        poseStack.scale(1.15F, 1.15F, 1.15F);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                this.cachedRenderStack,
                ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId()
        );
        poseStack.popPose();

        super.render(entity, entityYaw, partialTick, poseStack, buffer, LightTexture.FULL_BRIGHT);
    }

    @Override
    public ResourceLocation getTextureLocation(SpectralCasketEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
