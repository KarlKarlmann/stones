package net.stones.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.stones.StonesMod;
import net.stones.entity.EchoTraderEntity;

/**
 * Renderer für den Echo Trader.
 * Implementiert sanftes Floating, Glitch-Effekte bei baldigem Despawn und Glow-Maps.
 */
public class EchoTraderRenderer extends MobRenderer<EchoTraderEntity, VillagerModel<EchoTraderEntity>> {
    
    private static final ResourceLocation TEXTURE = new ResourceLocation(StonesMod.MODID, "textures/entity/echo_trader.png");
    private static final ResourceLocation GLOW_TEXTURE = new ResourceLocation(StonesMod.MODID, "textures/entity/echo_trader_glow.png");

    public EchoTraderRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.WANDERING_TRADER)), 0.5F);
        
        this.addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        this.addLayer(new CrossedArmsItemLayer<>(this, context.getItemInHandRenderer()));
        this.addLayer(new EchoGlowLayer(this));
    }

    @Override
    public void render(EchoTraderEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        int remaining = entity.getRemainingTicks();
        
        poseStack.pushPose();
        
        // 1. SANFTES FLOATING (Sinus-Welle)
        // Wir nutzen partialTicks für absolut ruckelfreie Bewegung unabhängig von der Framerate
        float time = entity.tickCount + partialTicks;
        float floatingOffset = Mth.sin(time * 0.1F) * 0.12F;
        poseStack.translate(0, floatingOffset, 0);

        // 2. GLITCH-ZITTERN (bei baldigem Verschwinden)
        if (remaining < 400 && entity.tickCount % 2 == 0) {
            float glitchX = (entity.getRandom().nextFloat() - 0.5F) * 0.04F;
            float glitchZ = (entity.getRandom().nextFloat() - 0.5F) * 0.04F;
            poseStack.translate(glitchX, 0, glitchZ);
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        
        poseStack.popPose();
    }

    @Override
    protected RenderType getRenderType(EchoTraderEntity entity, boolean invisible, boolean translucent, boolean glowing) {
        int remaining = entity.getRemainingTicks();
        // Unter 200 Ticks (10 Sek) wird er für den finalen Fade-Out transparent gerendert
        if (remaining < 200) {
            return RenderType.entityTranslucent(getTextureLocation(entity));
        }
        return super.getRenderType(entity, invisible, translucent, glowing);
    }

    @Override
    public ResourceLocation getTextureLocation(EchoTraderEntity entity) {
        return TEXTURE;
    }

    /**
     * Dedizierter Glow-Layer für leuchtende Augen/Runen.
     */
    private static class EchoGlowLayer extends RenderLayer<EchoTraderEntity, VillagerModel<EchoTraderEntity>> {
        public EchoGlowLayer(RenderLayerParent<EchoTraderEntity, VillagerModel<EchoTraderEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, EchoTraderEntity entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
            int remaining = entity.getRemainingTicks();
            float alpha = 1.0F;
            
            // Berechne Transparenz für das Verschwinden
            if (remaining < 200) {
                alpha = remaining / 200.0F;
            }

            VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.eyes(GLOW_TEXTURE));
            
            // Pulsierender Glow (synchron zum Floating oder leicht versetzt)
            float pulse = (Mth.sin(ageInTicks * 0.08F) + 1.0F) * 0.25F + 0.5F;
            float finalBrightness = pulse * alpha;
            
            this.getParentModel().renderToBuffer(poseStack, vertexconsumer, 15728880, OverlayTexture.NO_OVERLAY, finalBrightness, finalBrightness, finalBrightness, alpha);
        }
    }
}