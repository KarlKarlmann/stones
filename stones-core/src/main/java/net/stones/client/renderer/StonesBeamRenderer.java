package net.stones.client.fx;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

@Mod.EventBusSubscriber(modid = StonesMod.MODID, value = Dist.CLIENT)
public class StonesBeamRenderer {

    private static final List<BeamInstance> ACTIVE_BEAMS = new CopyOnWriteArrayList<>();
    private static final Random RNG = new Random();

    public static void spawnBeam(BeamInstance beam) {
        if (beam != null) {
            ACTIVE_BEAMS.add(beam);
        }
    }

    public static void clearAll() {
        ACTIVE_BEAMS.clear();
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
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE_BEAMS.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();

        RenderSystem.enableBlend();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();

        poseStack.pushPose();
        Matrix4f mat = poseStack.last().pose();

        for (BeamInstance beam : ACTIVE_BEAMS) {
            renderBeamInstance(beam, mat, camPos, partialTick, tesselator, buffer);
        }

        poseStack.popPose();

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void renderBeamInstance(BeamInstance beam, Matrix4f mat, Vec3 camPos,
                                           float partialTick, Tesselator tesselator, BufferBuilder buffer) {

        ResourceLocation textureLoc = resolveTexture(beam.textureId);
        RenderSystem.setShaderTexture(0, textureLoc);
        beam.beamType.applyBlendMode();

        Vec3 start = beam.getInterpolatedStart(partialTick).subtract(camPos);
        Vec3 end = beam.getInterpolatedEnd(partialTick).subtract(camPos);

        double totalDist = start.distanceTo(end);
        if (totalDist < 0.0001) return;

        // Reproduzierbare Seed-Generierung
        RNG.setSeed(beam.beamType == BeamType.LIGHTNING ? (long) beam.age * 31103L : 42L);
        List<Vec3> pathNodes = beam.beamType.getPathGenerator().generatePath(start, end, partialTick, beam.age, RNG);
        if (pathNodes.size() < 2) return;

        float progress = (beam.age + partialTick) * beam.uvScrollSpeed;

        // Schicht 1:
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        renderAxialRibbon(buffer, mat, pathNodes, beam.coronaWidth, progress, beam.uvRepeat,
                beam.r, beam.g, beam.b, beam.a * 0.45f, beam.beamType == BeamType.RAINBOW_RAY, progress);
        tesselator.end();

        // Schicht 2:
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float coreAlpha = Math.min(1.0f, beam.a * 1.25f);
        renderAxialRibbon(buffer, mat, pathNodes, beam.coreWidth, progress * 1.3f, beam.uvRepeat,
                1.0f, 1.0f, 1.0f, coreAlpha, false, 0.0f);
        tesselator.end();

        // Schicht 3:
        if (beam.beamType.hasOuterHelix()) {
            renderHelixOrbit(buffer, mat, tesselator, start, end, beam, partialTick, progress);
        }
    }

    private static void renderAxialRibbon(BufferBuilder buffer, Matrix4f mat, List<Vec3> nodes,
                                          float width, float uvOffset, float uvRepeat,
                                          float r, float g, float b, float a,
                                          boolean rainbow, float timeProgress) {

        int count = nodes.size();
        float halfWidth = width * 0.5f;

        for (int i = 0; i < count - 1; i++) {
            Vec3 p0 = nodes.get(i);
            Vec3 p1 = nodes.get(i + 1);

            Vec3 segmentDir = p1.subtract(p0);
            double segLen = segmentDir.length();
            if (segLen < 1e-5) continue;
            Vec3 segNorm = segmentDir.scale(1.0 / segLen);

            // Blickrichtungsvektor vom Segmentmittelpunkt zur Kamera im Relativursprung (0,0,0)
            Vec3 mid = p0.add(p1).scale(0.5);
            Vec3 view = mid.scale(-1.0).normalize();

            // Aufspannen des Breitenvektors: u = norm(segNorm x view)
            Vec3 side = segNorm.cross(view);
            if (side.lengthSqr() < 1e-6) {
                side = BeamType.getOrthogonalVector(segNorm);
            } else {
                side = side.normalize();
            }

            Vec3 offset = side.scale(halfWidth);

            float t0 = (float) i / (count - 1);
            float t1 = (float) (i + 1) / (count - 1);

            float u0 = uvOffset + t0 * uvRepeat;
            float u1 = uvOffset + t1 * uvRepeat;

            float cr0 = r, cg0 = g, cb0 = b;
            float cr1 = r, cg1 = g, cb1 = b;

            if (rainbow) {
                float[] c0 = hsvToRgb((t0 * 2.0f - timeProgress * 0.2f) % 1.0f, 1.0f, 1.0f);
                float[] c1 = hsvToRgb((t1 * 2.0f - timeProgress * 0.2f) % 1.0f, 1.0f, 1.0f);
                cr0 = c0[0]; cg0 = c0[1]; cb0 = c0[2];
                cr1 = c1[0]; cg1 = c1[1]; cb1 = c1[2];
            }

            buffer.vertex(mat, (float) (p0.x - offset.x), (float) (p0.y - offset.y), (float) (p0.z - offset.z))
                    .uv(u0, 0.0f).color(cr0, cg0, cb0, a).endVertex();
            buffer.vertex(mat, (float) (p0.x + offset.x), (float) (p0.y + offset.y), (float) (p0.z + offset.z))
                    .uv(u0, 1.0f).color(cr0, cg0, cb0, a).endVertex();
            buffer.vertex(mat, (float) (p1.x + offset.x), (float) (p1.y + offset.y), (float) (p1.z + offset.z))
                    .uv(u1, 1.0f).color(cr1, cg1, cb1, a).endVertex();
            buffer.vertex(mat, (float) (p1.x - offset.x), (float) (p1.y - offset.y), (float) (p1.z - offset.z))
                    .uv(u1, 0.0f).color(cr1, cg1, cb1, a).endVertex();
        }
    }

    private static void renderHelixOrbit(BufferBuilder buffer, Matrix4f mat, Tesselator tesselator,
                                         Vec3 start, Vec3 end, BeamInstance beam,
                                         float partialTick, float progress) {

        int helixSegments = 32;
        Vec3 dir = end.subtract(start);
        Vec3 dirNorm = dir.normalize();

        Vec3 n1 = BeamType.getOrthogonalVector(dirNorm);
        Vec3 n2 = dirNorm.cross(n1).normalize();

        List<Vec3> helixPath = new ArrayList<>(helixSegments + 1);
        float timeAngle = (beam.age + partialTick) * beam.helixSpeed;

        for (int i = 0; i <= helixSegments; i++) {
            double t = (double) i / helixSegments;
            // 
            double envelope = Math.sin(Math.PI * t);
            double currentRadius = beam.helixRadius * envelope;

            double angle = 2.0 * Math.PI * beam.helixFrequency * t + timeAngle;
            Vec3 radialOffset = n1.scale(Math.cos(angle) * currentRadius)
                                  .add(n2.scale(Math.sin(angle) * currentRadius));

            helixPath.add(start.add(dir.scale(t)).add(radialOffset));
        }

        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        renderAxialRibbon(buffer, mat, helixPath, beam.coreWidth * 0.75f, progress * 2.0f,
                beam.uvRepeat * 2.0f, beam.r, beam.g, beam.b, beam.a * 0.85f, false, 0.0f);
        tesselator.end();
    }

    private static ResourceLocation resolveTexture(String textureId) {
        if (textureId != null && (textureId.contains(":") || textureId.endsWith(".png"))) {
            return new ResourceLocation(textureId);
        }
        return ClientTextureCache.getOrRequest(textureId);
    }

    private static float[] hsvToRgb(float h, float s, float v) {
        h = (h % 1.0f + 1.0f) % 1.0f;
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        return switch (i % 6) {
            case 0 -> new float[]{v, t, p};
            case 1 -> new float[]{q, v, p};
            case 2 -> new float[]{p, v, t};
            case 3 -> new float[]{p, q, v};
            case 4 -> new float[]{t, p, v};
            default -> new float[]{v, p, q};
        };
    }
}