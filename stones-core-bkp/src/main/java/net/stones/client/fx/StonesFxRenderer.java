package net.stones.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stones.StonesMod;
import net.stones.client.cache.ClientTextureCache;
import org.joml.Matrix4f;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Mod.EventBusSubscriber(modid = StonesMod.MODID, value = Dist.CLIENT)
public class StonesFxRenderer {

    private static final List<SpriteInstance> ACTIVE_SPRITES = new CopyOnWriteArrayList<>();

    public static void spawnSprite(SpriteInstance sprite) {
        if (sprite != null) {
            ACTIVE_SPRITES.add(sprite);
        }
    }

    public static void clearAll() {
        ACTIVE_SPRITES.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (Minecraft.getInstance().isPaused()) return;

        for (SpriteInstance sprite : ACTIVE_SPRITES) {
            sprite.tick();
            if (sprite.isDead()) {
                ACTIVE_SPRITES.remove(sprite);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // Wir rendern im TRANSPARENT / PARTICLES Stage
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (ACTIVE_SPRITES.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();

        RenderSystem.enableBlend();
        RenderSystem.depthMask(false); // Kein Tiefenschreiben, verhindert eckige Überlappungskanten
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferBuilder = tesselator.getBuilder();

        for (SpriteInstance sprite : ACTIVE_SPRITES) {
            renderSingleSprite(sprite, poseStack, camera, camPos, event.getPartialTick(), tesselator, bufferBuilder);
        }

        // Blendmode & DepthMask auf Vanilla-Standard zurücksetzen
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void renderSingleSprite(SpriteInstance sprite, PoseStack poseStack, Camera camera, Vec3 camPos,
                                           float partialTick, Tesselator tesselator, BufferBuilder bufferBuilder) {

        // 1. Textur via On-Demand Texture-Cache anfordern ("Was?"-System)
        ResourceLocation textureLoc = ClientTextureCache.getOrRequest(sprite.textureId);
        RenderSystem.setShaderTexture(0, textureLoc);

        // 2. Blend-Mode (ADDITIVE Glüh-Effekt vs. ALPHA Abdeckung)
        if (sprite.blendMode == SpriteInstance.BlendMode.ADDITIVE) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }

        // 3. Position & Interpolation
        Vec3 renderPos = sprite.getInterpolatedPos(partialTick);
        float[] color = sprite.getCurrentColor(partialTick);

        poseStack.pushPose();
        // Offset relativ zur Spielerkamera setzen
        poseStack.translate(renderPos.x - camPos.x, renderPos.y - camPos.y, renderPos.z - camPos.z);

        // 4. Facing & Alignment
        switch (sprite.facing) {
            case BILLBOARD -> {
                // Dreht sich immer exakt mit der Kamera mit
                poseStack.mulPose(camera.rotation());
            }
            case GROUND_FLAT -> {
                // Flach auf der XZ-Boden-Ebene ausrichten
                poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            }
            case VELOCITY_ALIGNED -> {
                // Richtet das Quad entlang des Bewegungsvektors aus
                if (sprite.velocity.lengthSqr() > 0.0001) {
                    Vec3 dir = sprite.velocity.normalize();
                    float yaw = (float) Math.toDegrees(Math.atan2(dir.x, dir.z));
                    float pitch = (float) Math.toDegrees(Math.asin(-dir.y));
                    poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                    poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
                } else {
                    poseStack.mulPose(camera.rotation());
                }
            }
        }

        // Eigen-Spin-Rotation um die Z-Achse anwenden
        if (sprite.rotation != 0.0f) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(sprite.rotation));
        }

        // Skalierung des 3D-Quads
        float halfSize = sprite.scale * 0.5f;
        Matrix4f mat = poseStack.last().pose();

        // 5. Quad Rendern
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        
        bufferBuilder.vertex(mat, -halfSize, -halfSize, 0).uv(0.0f, 1.0f)
                .color(color[0], color[1], color[2], color[3]).endVertex();
        bufferBuilder.vertex(mat, -halfSize, halfSize, 0).uv(0.0f, 0.0f)
                .color(color[0], color[1], color[2], color[3]).endVertex();
        bufferBuilder.vertex(mat, halfSize, halfSize, 0).uv(1.0f, 0.0f)
                .color(color[0], color[1], color[2], color[3]).endVertex();
        bufferBuilder.vertex(mat, halfSize, -halfSize, 0).uv(1.0f, 1.0f)
                .color(color[0], color[1], color[2], color[3]).endVertex();

        tesselator.end();

        poseStack.popPose();
    }
}