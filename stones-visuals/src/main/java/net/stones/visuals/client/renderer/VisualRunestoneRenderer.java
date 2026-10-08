package net.stones.visuals.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.stones.block.entity.RunestoneBlockEntity;
import net.stones.visuals.integration.VisualsEventHandler;
import org.joml.Matrix4f;

import java.util.List;
import java.util.UUID;

public class VisualRunestoneRenderer implements BlockEntityRenderer<RunestoneBlockEntity> {

    private final PlayerModel<AbstractClientPlayer> playerModel;

    public VisualRunestoneRenderer(BlockEntityRendererProvider.Context context) {
        this.playerModel = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false);
        this.playerModel.young = false;
        this.playerModel.hat.visible = false;
        this.playerModel.jacket.visible = false;
        this.playerModel.leftSleeve.visible = false;
        this.playerModel.rightSleeve.visible = false;
        this.playerModel.leftPants.visible = false;
        this.playerModel.rightPants.visible = false;
    }

    @Override
    public void render(RunestoneBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int combinedLight, int combinedOverlay) {
        UUID shrineId = be.getShrineId();
        if (shrineId == null) return;

        renderHolographicCoin(shrineId, be.getBlockPos(), poseStack, buffer);
        renderInventoryOverlay(be, poseStack, buffer);
        renderGuardians(be, partialTick, poseStack, buffer, combinedLight, combinedOverlay);
    }

    private void renderInventoryOverlay(RunestoneBlockEntity be, PoseStack stack, MultiBufferSource buffer) {
        ResourceLocation overlayTex = ClientRunestoneTextureManager.getOrCreate(be.getShrineId(), be.getClientMaxLevel());
        if (overlayTex == null) return;

        int glowLight = 15728880;
        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(overlayTex));

        for (int i = 0; i < 4; i++) {
            stack.pushPose();
            stack.translate(0.5, 0.5, 0.5);
            stack.mulPose(Axis.YP.rotationDegrees(i * 90));
            stack.translate(-0.5, -0.5, -0.5);
            stack.translate(0, 0, -0.001);

            Matrix4f matrix = stack.last().pose();
            vc.vertex(matrix, 0, 0, 0).color(255, 255, 255, 255).uv(0, 1).overlayCoords(0).uv2(glowLight).normal(0, 0, -1).endVertex();
            vc.vertex(matrix, 0, 1, 0).color(255, 255, 255, 255).uv(0, 0).overlayCoords(0).uv2(glowLight).normal(0, 0, -1).endVertex();
            vc.vertex(matrix, 1, 1, 0).color(255, 255, 255, 255).uv(1, 0).overlayCoords(0).uv2(glowLight).normal(0, 0, -1).endVertex();
            vc.vertex(matrix, 1, 0, 0).color(255, 255, 255, 255).uv(1, 1).overlayCoords(0).uv2(glowLight).normal(0, 0, -1).endVertex();
            stack.popPose();
        }
    }

    private void renderHolographicCoin(UUID shrineId, BlockPos pos, PoseStack poseStack, MultiBufferSource buffer) {
        ClientDynamicLabelHandler.LabelEntry entry = ClientDynamicLabelHandler.getOrGenerate(shrineId);
        if (entry == null || entry.location() == null) return;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        double dx = camera.getPosition().x - (pos.getX() + 0.5);
        double dz = camera.getPosition().z - (pos.getZ() + 0.5);
        // Zylindrische Ausrichtung: Dreht sich nur um Yaw zum Spieler und steht senkrecht in der Welt
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;

        long time = System.currentTimeMillis();
        // 75 Sekunden fuer eine volle 360-Grad Drehung um die Z-Achse (im Uhrzeigersinn)
        float rollAngle = ((time % 75000L) / 75000.0f) * 360.0f;
        double bob = Math.sin((time % 4500) / 4500.0 * Math.PI * 2) * 0.035;
        float pulse = 0.88f + 0.12f * (float) Math.sin((time % 2800) / 2800.0 * Math.PI * 2);

        poseStack.pushPose();
        poseStack.translate(0.5, 1.80 + bob, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
        poseStack.mulPose(Axis.ZP.rotationDegrees(rollAngle));

        float coinDiameter = 1.05f;
        float half = coinDiameter * 0.5f;
        int fullBright = 15728880;

        VertexConsumer vc = buffer.getBuffer(RenderType.entityTranslucentEmissive(entry.location()));

        // Tiefe: Leicht nach hinten versetzter Schattenpass fuer Kontrast gegen helle Himmels-/Gelaendeflaechen
        Matrix4f shadowMat = poseStack.last().pose();
        float shadowOffset = 0.008f;
        vc.vertex(shadowMat, -half + shadowOffset, -half - shadowOffset, -0.005f).color(15, 12, 10, 160).uv(0, 0).overlayCoords(0).uv2(fullBright).normal(0, 0, 1).endVertex();
        vc.vertex(shadowMat, -half + shadowOffset,  half - shadowOffset, -0.005f).color(15, 12, 10, 160).uv(0, 1).overlayCoords(0).uv2(fullBright).normal(0, 0, 1).endVertex();
        vc.vertex(shadowMat,  half + shadowOffset,  half - shadowOffset, -0.005f).color(15, 12, 10, 160).uv(1, 1).overlayCoords(0).uv2(fullBright).normal(0, 0, 1).endVertex();
        vc.vertex(shadowMat,  half + shadowOffset, -half - shadowOffset, -0.005f).color(15, 12, 10, 160).uv(1, 0).overlayCoords(0).uv2(fullBright).normal(0, 0, 1).endVertex();

        // Vordergrund: Volle Leuchtkraft mit Arkan-Gold und sanftem Helligkeitspuls
        int alpha = (int) (255 * pulse);
        Matrix4f mainMat = poseStack.last().pose();
        vc.vertex(mainMat, -half, -half, 0.0f).color(255, 255, 255, alpha).uv(0, 0).overlayCoords(0).uv2(fullBright).normal(0, 0, 1).endVertex();
        vc.vertex(mainMat, -half,  half, 0.0f).color(255, 255, 255, alpha).uv(0, 1).overlayCoords(0).uv2(fullBright).normal(0, 0, 1).endVertex();
        vc.vertex(mainMat,  half,  half, 0.0f).color(255, 255, 255, alpha).uv(1, 1).overlayCoords(0).uv2(fullBright).normal(0, 0, 1).endVertex();
        vc.vertex(mainMat,  half, -half, 0.0f).color(255, 255, 255, alpha).uv(1, 0).overlayCoords(0).uv2(fullBright).normal(0, 0, 1).endVertex();

        poseStack.popPose();
    }

    private void renderGuardians(RunestoneBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int light, int overlay) {
        List<Vec3> spots = be.getGuardianSpots();
        UUID[] owners = be.getClientOwners().toArray(new UUID[0]);
        Player localPlayer = Minecraft.getInstance().player;
        if (localPlayer == null) return;

        boolean isNight = be.getLevel().getDayTime() % 24000 > 13000 && be.getLevel().getDayTime() % 24000 < 23000;

        for (int i = 0; i < Math.min(spots.size(), owners.length); i++) {
            UUID ownerId = owners[i];
            Vec3 spot = spots.get(i);
            BlockPos spotPos = BlockPos.containing(spot);

            int skyGeometry = be.getLevel().getBrightness(LightLayer.SKY, spotPos);
            int blockLight = be.getLevel().getBrightness(LightLayer.BLOCK, spotPos);
            float timeLightFactor = (isNight) ? 0.0f : 1.0f;
            int finalLightLevel = Math.max(blockLight, (int)(skyGeometry * timeLightFactor));
            boolean isDark = finalLightLevel < 7;

            GuardianSkinPostProcessor.getOrProcess(ownerId);
            ResourceLocation texture = isDark ? GuardianSkinPostProcessor.getNightSkin(ownerId) : GuardianSkinPostProcessor.getDaySkin(ownerId);
            if (texture == null) continue;

            RenderType renderType = isDark ? RenderType.entityTranslucentEmissive(texture) : RenderType.entityTranslucent(texture);
            int packedLight = isDark ? 15728880 : LevelRenderer.getLightColor(be.getLevel(), spotPos);

            poseStack.pushPose();
            poseStack.translate(spot.x - be.getBlockPos().getX(), spot.y - be.getBlockPos().getY(), spot.z - be.getBlockPos().getZ());

            Vec3 dir = localPlayer.position().subtract(spot);
            float yaw = (float)(Mth.atan2(dir.z, dir.x) * (180 / Math.PI)) - 90;
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

            poseStack.pushPose();
            poseStack.scale(-0.9375F, -0.9375F, 0.9375F);
            poseStack.translate(0, -1.501, 0);

            playerModel.rightArm.xRot = -0.6f;
            playerModel.rightArm.yRot = -0.4f;
            playerModel.leftArm.xRot = -0.6f;
            playerModel.leftArm.yRot = 0.4f;

            playerModel.renderToBuffer(poseStack, buffer.getBuffer(renderType), packedLight, overlay, 1.0f, 1.0f, 1.0f, 1.0f);

            if (!VisualsEventHandler.SHRINE_ARTIFACTS.isEmpty()) {
                long sMSB = be.getShrineId().getMostSignificantBits();
                long sLSB = be.getShrineId().getLeastSignificantBits();
                long oLSB = ownerId.getLeastSignificantBits();
                long entropy = (sMSB ^ (sLSB >>> (i * 7))) ^ (oLSB << i);
                long ritualCore = entropy * 0x243F6A8885A308D3L;
                int artifactIndex = Math.abs((int)((ritualCore ^ (ritualCore >>> 32)))) % VisualsEventHandler.SHRINE_ARTIFACTS.size();
                ResourceLocation artifactLoc = VisualsEventHandler.SHRINE_ARTIFACTS.get(artifactIndex);
                BakedModel artifactModel = Minecraft.getInstance().getModelManager().getModel(artifactLoc);

                if (artifactModel != null && artifactModel != Minecraft.getInstance().getModelManager().getMissingModel()) {
                    poseStack.pushPose();
                    playerModel.body.translateAndRotate(poseStack);
                    poseStack.translate(0, 0.4, -0.4);
                    poseStack.mulPose(Axis.YP.rotationDegrees(180));
                    poseStack.mulPose(Axis.XP.rotationDegrees(180));
                    poseStack.mulPose(Axis.XP.rotationDegrees(-45));

                    ItemTransform transform = artifactModel.getTransforms().getTransform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
                    if (transform != ItemTransform.NO_TRANSFORM) {
                        poseStack.translate(transform.translation.x() / 16.0f, transform.translation.y() / 16.0f, transform.translation.z() / 16.0f);
                        poseStack.mulPose(Axis.XP.rotationDegrees(transform.rotation.x()));
                        poseStack.mulPose(Axis.YP.rotationDegrees(transform.rotation.y()));
                        poseStack.mulPose(Axis.ZP.rotationDegrees(transform.rotation.z()));
                        poseStack.scale(transform.scale.x(), transform.scale.y(), transform.scale.z());
                    }

                    poseStack.translate(-0.5, -0.5, -0.5);
                    Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                        poseStack.last(),
                        buffer.getBuffer(RenderType.cutout()),
                        null,
                        artifactModel,
                        1f, 1f, 1f,
                        packedLight,
                        overlay
                    );
                    poseStack.popPose();
                }
            }

            playerModel.rightArm.xRot = 0; playerModel.leftArm.xRot = 0;
            playerModel.rightArm.yRot = 0; playerModel.leftArm.yRot = 0;

            poseStack.popPose();
            poseStack.popPose();
        }
    }

    @Override
    public int getViewDistance() { return 128; }
}