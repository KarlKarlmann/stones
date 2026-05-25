package net.stones.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
import net.stones.init.StonesModClient;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * Finaler Renderer für den Runenschrein (Void Altar).
 * Korrektur:
 * - Entfernung der manuellen Y-Rotation ("Facing Fix"), da diese die Ausrichtung verfälscht hat.
 * - Striktes Anwenden der JSON-Transformationsdaten.
 * - Wächter-Scaling vereinfacht (-X, -Y, Z) für korrekte Normalen.
 * - FIX: Null-Check für Skin-Texturen hinzugefügt (Verhindert Crash mit Oculus/Embeddium).
 */
public class RunestoneRenderer implements BlockEntityRenderer<RunestoneBlockEntity> {

    private final PlayerModel<AbstractClientPlayer> playerModel;

    public RunestoneRenderer(BlockEntityRendererProvider.Context context) {
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

        renderHolographicLabel(shrineId, poseStack, buffer, combinedLight);
        renderInventoryOverlay(be, poseStack, buffer);
        renderGuardians(be, partialTick, poseStack, buffer, combinedLight, combinedOverlay);
    }

    private void renderInventoryOverlay(RunestoneBlockEntity be, PoseStack stack, MultiBufferSource buffer) {
        ResourceLocation overlayTex = ClientRunestoneTextureManager.getOrCreate(be.getShrineId());
        if (overlayTex == null) return; // Safety check

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

    private void renderHolographicLabel(UUID shrineId, PoseStack poseStack, MultiBufferSource buffer, int light) {
        ResourceLocation labelTex = ClientDynamicLabelHandler.getOrGenerate(shrineId);
        if (labelTex == null) return; // Safety check

        long time = System.currentTimeMillis();
        poseStack.pushPose();
        double bob = Math.sin((time % 4000) / 4000.0 * Math.PI * 2) * 0.03;
        poseStack.translate(0.5, 2.0 + bob, 0.5); 
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        float scale = 0.02f; 
        poseStack.scale(-scale, -scale, scale);
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityTranslucentEmissive(labelTex));
        float halfW = 64.0f; float halfH = 16.0f;
        Matrix4f matrix = poseStack.last().pose();
        vertexConsumer.vertex(matrix, -halfW, -halfH, 0.0f).color(255, 255, 255, 255).uv(0.0f, 0.0f).overlayCoords(0).uv2(light).normal(0, 1, 0).endVertex();
        vertexConsumer.vertex(matrix, -halfW, halfH, 0.0f).color(255, 255, 255, 255).uv(0.0f, 1.0f).overlayCoords(0).uv2(light).normal(0, 1, 0).endVertex();
        vertexConsumer.vertex(matrix, halfW, halfH, 0.0f).color(255, 255, 255, 255).uv(1.0f, 1.0f).overlayCoords(0).uv2(light).normal(0, 1, 0).endVertex();
        vertexConsumer.vertex(matrix, halfW, -halfH, 0.0f).color(255, 255, 255, 255).uv(1.0f, 0.0f).overlayCoords(0).uv2(light).normal(0, 1, 0).endVertex();
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

            GrimdarkSkinManager.getOrProcess(ownerId);
            ResourceLocation texture = isDark ? GrimdarkSkinManager.getNightSkin(ownerId) : GrimdarkSkinManager.getDaySkin(ownerId);
            
            // --- CRASH FIX ---
            // Oculus/Sodium crasht, wenn texture null ist. Wir müssen sicherstellen, dass sie existiert.
            // Wenn der SkinManager noch lädt oder fehlschlägt, ist texture null.
            if (texture == null) {
                continue; // Überspringe diesen Wächter für diesen Frame
            }

            RenderType renderType = isDark ? RenderType.entityTranslucentEmissive(texture) : RenderType.entityTranslucent(texture);
            int packedLight = isDark ? 15728880 : LevelRenderer.getLightColor(be.getLevel(), spotPos);

            poseStack.pushPose();
            // 1. Position in der Welt
            poseStack.translate(spot.x - be.getBlockPos().getX(), spot.y - be.getBlockPos().getY(), spot.z - be.getBlockPos().getZ());
            
            // 2. Billboard Y-Rotation (Zum Spieler)
            Vec3 dir = localPlayer.position().subtract(spot);
            float yaw = (float)(Mth.atan2(dir.z, dir.x) * (180 / Math.PI)) - 90;
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));

            // 3. Entity Scale & Fixes
            poseStack.pushPose();
            // Standard Entity Rendering: Scale(-1, -1, 1) wäre Spiegelverkehrt.
            // Wir nutzen Scale(-X, -Y, Z) um die Spiegelung zu verhindern und die Normalen korrekt zu halten.
            poseStack.scale(-0.9375F, -0.9375F, 0.9375F);
            poseStack.translate(0, -1.501, 0); 

            // 4. BEIDHÄNDIGE HALTUNG (Bauchhöhe)
            playerModel.rightArm.xRot = -0.6f; 
            playerModel.rightArm.yRot = -0.4f;
            playerModel.leftArm.xRot = -0.6f;
            playerModel.leftArm.yRot = 0.4f;
            
            // Wächter rendern
            playerModel.renderToBuffer(poseStack, buffer.getBuffer(renderType), packedLight, overlay, 1.0f, 1.0f, 1.0f, 1.0f);

            // 6. ARTEFAKT RENDERING (Zentriert Bauchhöhe)
            if (!StonesModClient.SHRINE_ARTIFACTS.isEmpty()) {
                long sMSB = be.getShrineId().getMostSignificantBits();
                long sLSB = be.getShrineId().getLeastSignificantBits();
                long oLSB = ownerId.getLeastSignificantBits();
                long entropy = (sMSB ^ (sLSB >>> (i * 7))) ^ (oLSB << i);
                long ritualCore = entropy * 0x243F6A8885A308D3L;
                int artifactIndex = Math.abs((int)((ritualCore ^ (ritualCore >>> 32)))) % StonesModClient.SHRINE_ARTIFACTS.size();
                ResourceLocation artifactLoc = StonesModClient.SHRINE_ARTIFACTS.get(artifactIndex);
                BakedModel artifactModel = Minecraft.getInstance().getModelManager().getModel(artifactLoc);

                if (artifactModel != null && artifactModel != Minecraft.getInstance().getModelManager().getMissingModel()) {
                    poseStack.pushPose();
                    
                    // A. Parenting am BODY
                    playerModel.body.translateAndRotate(poseStack);

                    // B. Positionierung auf Bauchhöhe
                    poseStack.translate(0, 0.4, -0.4);

                    // C. KRITISCHER FIX: Erst Y-Rotation 180° BEVOR der X-Flip kommt!
                    // Das kompensiert die "Rückwärts"-Ausrichtung
                    poseStack.mulPose(Axis.YP.rotationDegrees(180));

                    // D. Item Rotation Basis (X-Flip + Vorwärts-Neigung)
                    // Jetzt erst der 180° X-Flip für Item-Koordinaten
                    poseStack.mulPose(Axis.XP.rotationDegrees(180));
                    
                    // E. 45° nach vorne neigen (um die lokale X-Achse im Item-Space)
                    poseStack.mulPose(Axis.XP.rotationDegrees(-45));

                    // E. JSON Transforms
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

            // Reset
            playerModel.rightArm.xRot = 0; playerModel.leftArm.xRot = 0;
            playerModel.rightArm.yRot = 0; playerModel.leftArm.yRot = 0;
            
            poseStack.popPose(); 
            poseStack.popPose(); 
        }
    }

    @Override
    public int getViewDistance() { return 128; }
}