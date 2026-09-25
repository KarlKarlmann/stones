package net.stones.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
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
public class StonesBeamRenderer {

    private static final List<BeamInstance> ACTIVE_BEAMS = new CopyOnWriteArrayList<>();

    public static void spawnBeam(BeamInstance beam) {
        if (beam != null) {
            ACTIVE_BEAMS.add(beam);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || Minecraft.getInstance().isPaused()) return;

        for (BeamInstance beam : ACTIVE_BEAMS) {
            beam.tick();
            if (beam.isDead()) {
                ACTIVE_BEAMS.remove(beam);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (ACTIVE_BEAMS.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition(); // Kamera-Offset sichern

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        for (BeamInstance beam : ACTIVE_BEAMS) {
            renderSingleBeam(beam, poseStack, camPos, event.getPartialTick(), tesselator, buffer);
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void renderSingleBeam(BeamInstance beam, PoseStack poseStack, Vec3 camPos,
                                         float partialTick, Tesselator tesselator, BufferBuilder buffer) {

        // Vanilla-Texturen direkt laden, Studio-Texturen über Cache
        ResourceLocation textureLoc;
        if (beam.textureId != null && (beam.textureId.contains(":") || beam.textureId.endsWith(".png"))) {
            textureLoc = new ResourceLocation(beam.textureId);
        } else {
            textureLoc = ClientTextureCache.getOrRequest(beam.textureId);
        }
        RenderSystem.setShaderTexture(0, textureLoc);

        if (beam.blendMode == SpriteInstance.BlendMode.ADDITIVE) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }

        // Relative Kamera-Koordinaten (Pflicht im RenderLevelStageEvent!)
        Vec3 start = beam.start.subtract(camPos);
        Vec3 end = beam.end.subtract(camPos);

        Vec3 dir = end.subtract(start);
        double len = dir.length();
        if (len < 0.0001) return;

        Vec3 normDir = dir.scale(1.0 / len);

        Vec3 arbitrary = Math.abs(normDir.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 perp1 = normDir.cross(arbitrary).normalize().scale(beam.width * 0.5f);
        Vec3 perp2 = normDir.cross(perp1).normalize().scale(beam.width * 0.5f);

        float progress = (beam.age + partialTick) * beam.uvScrollSpeed;
        float u0 = progress;
        float u1 = progress + beam.uvRepeat;

        poseStack.pushPose();
        Matrix4f mat = poseStack.last().pose();

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        // Plane 1 (Horizontal)
        drawQuad(buffer, mat, start.subtract(perp1), start.add(perp1), end.add(perp1), end.subtract(perp1), u0, u1, beam.r, beam.g, beam.b, beam.a);
        // Plane 2 (Vertikal - 90 Grad gedreht)
        drawQuad(buffer, mat, start.subtract(perp2), start.add(perp2), end.add(perp2), end.subtract(perp2), u0, u1, beam.r, beam.g, beam.b, beam.a);

        tesselator.end();
        poseStack.popPose();
    }

    private static void drawQuad(BufferBuilder buffer, Matrix4f mat, Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3, float u0, float u1, float r, float g, float b, float a) {
        buffer.vertex(mat, (float) v0.x, (float) v0.y, (float) v0.z).uv(u0, 0.0f).color(r, g, b, a).endVertex();
        buffer.vertex(mat, (float) v1.x, (float) v1.y, (float) v1.z).uv(u0, 1.0f).color(r, g, b, a).endVertex();
        buffer.vertex(mat, (float) v2.x, (float) v2.y, (float) v2.z).uv(u1, 1.0f).color(r, g, b, a).endVertex();
        buffer.vertex(mat, (float) v3.x, (float) v3.y, (float) v3.z).uv(u1, 0.0f).color(r, g, b, a).endVertex();
    }
}