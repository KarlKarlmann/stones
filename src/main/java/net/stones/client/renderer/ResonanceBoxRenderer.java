package net.stones.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.stones.StonesMod;
import net.stones.block.entity.ResonanceBoxBlockEntity;

public class ResonanceBoxRenderer implements BlockEntityRenderer<ResonanceBoxBlockEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(StonesMod.MODID, "textures/entity/resonance_box.png");
    
    private final ModelPart lid;
    private final ModelPart bottom;
    private final ModelPart lock;

    public ResonanceBoxRenderer(BlockEntityRendererProvider.Context context) {
        // Wir holen uns das Standard-Kisten-Modell von Minecraft
        ModelPart root = context.bakeLayer(ModelLayers.CHEST);
        this.bottom = root.getChild("bottom");
        this.lid = root.getChild("lid");
        this.lock = root.getChild("lock");
    }

    @Override
    public void render(ResonanceBoxBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        poseStack.pushPose();

        // Positionierung und Rotation (Kisten-Modelle sind oft um 180 Grad gedreht)
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F)); // Korrektur der Ausrichtung
        poseStack.translate(-0.5D, -0.5D, -0.5D);

        // Nutze Vanilla-Animation für Openness (direkt vom ChestBlockEntity)
        float openProgress = be.getOpenNess(partialTick);
        
        // Ease-Out Kurve (macht es "schwungvoll")
        openProgress = 1.0F - openProgress;
        openProgress = 1.0F - openProgress * openProgress * openProgress;
        
        // Deckel rotieren (90 Grad = PI / 2)
        this.lid.xRot = -(openProgress * ((float)Math.PI / 2F));
        this.lock.xRot = this.lid.xRot;

        // Rendern
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
        
        // 1. Korpus ganz normal rendern
        this.bottom.render(poseStack, vertexConsumer, packedLight, packedOverlay);

        // 2. Deckel und Schloss minimal anheben gegen Textur-Überschneidungen (Z-Fighting)
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.065D, 0.0D);
        this.lid.render(poseStack, vertexConsumer, packedLight, packedOverlay);
        this.lock.render(poseStack, vertexConsumer, packedLight, packedOverlay);
        poseStack.popPose();

        poseStack.popPose();
    }
}