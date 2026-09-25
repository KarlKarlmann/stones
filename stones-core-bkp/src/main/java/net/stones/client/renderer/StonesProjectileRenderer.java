package net.stones.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.stones.client.renderer.combo.ClientComboRenderer;
import net.stones.entity.StonesProjectileEntity;
import org.joml.Matrix4f;

public class StonesProjectileRenderer extends EntityRenderer<StonesProjectileEntity> {

    public StonesProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(StonesProjectileEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        if ("BILLBOARD".equals(entity.getRenderMode())) {
            ResourceLocation texture = ClientComboRenderer.resolveTexture(entity.getTextureData());
            VertexConsumer builder = buffer.getBuffer(RenderType.entityTranslucent(texture));

            poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

            Matrix4f matrix = poseStack.last().pose();
            float half = 0.5f;
            int light = 15728880; // Fullbright Lightmap Wert (15, 15)

            builder.vertex(matrix, -half, -half, 0).color(1f, 1f, 1f, 0.9f).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();
            builder.vertex(matrix, -half,  half, 0).color(1f, 1f, 1f, 0.9f).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();
            builder.vertex(matrix,  half,  half, 0).color(1f, 1f, 1f, 0.9f).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();
            builder.vertex(matrix,  half, -half, 0).color(1f, 1f, 1f, 0.9f).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0, 1, 0).endVertex();
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(StonesProjectileEntity entity) {
        return ClientComboRenderer.resolveTexture(entity.getTextureData());
    }
}