package net.stones.client.renderer.combo;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stones.StonesMod;
import net.stones.network.PacketSyncCombo;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-Only Rendering Engine.
 * Modifiziert, um wirbelnde Combo-Punkte um jedes beliebige Entity (Spieler und Mobs) darzustellen.
 */
@Mod.EventBusSubscriber(modid = StonesMod.MODID, value = Dist.CLIENT)
public class ClientComboRenderer {

    public static class ActiveCombo {
        public String comboId;
        public int entityId;
        public int count, maxCount;
        public ResourceLocation texture;
        public float size, radius, speed, r, g, b, a;
        public long expirationTime; 
    }

    // Key ist nun eine Kombination aus EntityID und ComboID
    private static final Map<String, ActiveCombo> ACTIVE_COMBOS = new HashMap<>();

    public static void updateCombo(PacketSyncCombo msg) {
        String key = msg.entityId + "_" + msg.comboId;
        if (msg.count <= 0) {
            ACTIVE_COMBOS.remove(key);
            return;
        }

        ActiveCombo combo = ACTIVE_COMBOS.computeIfAbsent(key, k -> new ActiveCombo());
        combo.comboId = msg.comboId;
        combo.entityId = msg.entityId;
        combo.count = msg.count;
        combo.maxCount = msg.maxCount;
        combo.texture = new ResourceLocation(msg.texture);
        combo.size = msg.size;
        combo.radius = msg.radius;
        combo.speed = msg.speed;
        combo.r = msg.r; combo.g = msg.g; combo.b = msg.b; combo.a = msg.a;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            combo.expirationTime = mc.level.getGameTime() + msg.timeoutTicks;
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long currentTime = mc.level.getGameTime();
        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();

        // 1. Abgelaufene Kombos entfernen
        ACTIVE_COMBOS.entrySet().removeIf(entry -> currentTime > entry.getValue().expirationTime);

        if (ACTIVE_COMBOS.isEmpty()) return;

        Vec3 cameraPos = camera.getPosition();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest(); // Sichtbarkeit durch Wände/Gras gewährleisten
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();

        for (ActiveCombo combo : ACTIVE_COMBOS.values()) {
            Entity targetEntity = mc.level.getEntity(combo.entityId);
            // Sicherheitscheck: Existiert das Entity noch und ist am Leben?
            if (targetEntity == null || !targetEntity.isAlive()) continue;

            RenderSystem.setShaderTexture(0, combo.texture);
            bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

            // Nutze die Ticks des Ziel-Entities, um die Umlaufbahn flüssig zu drehen
            float timeOffset = (targetEntity.tickCount + event.getPartialTick()) * combo.speed;

            for (int i = 0; i < combo.count; i++) {
                float angleOffset = (Mth.TWO_PI / Math.max(combo.count, combo.maxCount)) * i;
                float currentAngle = timeOffset + angleOffset;

                // Position um die Brusthöhe des Ziels berechnen (basierend auf der Augenhöhe minus Offset)
                double x = Mth.lerp(event.getPartialTick(), targetEntity.xo, targetEntity.getX()) + Math.cos(currentAngle) * combo.radius;
                double y = Mth.lerp(event.getPartialTick(), targetEntity.yo, targetEntity.getY()) + (targetEntity.getEyeHeight() / 1.5);
                double z = Mth.lerp(event.getPartialTick(), targetEntity.zo, targetEntity.getZ()) + Math.sin(currentAngle) * combo.radius;

                poseStack.pushPose();
                poseStack.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
                
                // Rotations-Billboard zur Kamera ausrichten
                poseStack.mulPose(camera.rotation());

                float s = combo.size / 2.0f;
                Matrix4f matrix = poseStack.last().pose();
                
                bufferbuilder.vertex(matrix, -s, -s, 0).uv(0, 1).color(combo.r, combo.g, combo.b, combo.a).endVertex();
                bufferbuilder.vertex(matrix, -s,  s, 0).uv(0, 0).color(combo.r, combo.g, combo.b, combo.a).endVertex();
                bufferbuilder.vertex(matrix,  s,  s, 0).uv(1, 0).color(combo.r, combo.g, combo.b, combo.a).endVertex();
                bufferbuilder.vertex(matrix,  s, -s, 0).uv(1, 1).color(combo.r, combo.g, combo.b, combo.a).endVertex();

                poseStack.popPose();
            }
            tesselator.end();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}