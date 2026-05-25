package net.stones.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stones.StonesMod;
import net.stones.block.RunestoneBlock;
import net.stones.cap.PlayerShrineCapProvider;
import net.stones.item.StoneItem;
import net.stones.network.PacketBindShrine;
import net.stones.network.PacketOpenShrine;

@Mod.EventBusSubscriber(modid = StonesMod.MODID, value = Dist.CLIENT)
public class ClientAttunementHandler {

    // Status für Interaktion
    private static int holdTicks = 0;
    private static final int REQUIRED_TICKS = 60; // 3 Sekunden halten für Bindung
    private static final int CLICK_THRESHOLD = 10; // Unter 0.5 Sek gilt als Klick (Öffnen)
    private static boolean wasDown = false;

    // --- 1. LOGIK: Ticks & Input ---
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean isRightClickDown = mc.options.keyUse.isDown();
        boolean lookingAtRunestone = false;
        
        // Raytrace checken, ob der Spieler auf den Runenschrein blickt
        if (mc.hitResult instanceof BlockHitResult blockHit) {
             if (mc.level.getBlockState(blockHit.getBlockPos()).getBlock() instanceof RunestoneBlock) {
                 lookingAtRunestone = true;
             }
        }

        if (isRightClickDown) {
            // Taste gedrückt halten
            if (lookingAtRunestone) {
                holdTicks++;
                
                // Sende Zwischen-Ticks an den Server, um den progressiven 
                // Seelenentzug (Herzen schrumpfen, Nausea/Dunkelheit steigt) live zu triggern!
                if (holdTicks == 10 || holdTicks == 30 || holdTicks == 50) {
                    if (mc.hitResult instanceof BlockHitResult blockHit) {
                        StonesMod.PACKET_HANDLER.sendToServer(new PacketBindShrine(blockHit.getBlockPos(), holdTicks));
                    }
                }
                
                // Trigger Bindung nach exakt 3 Sekunden (60 Ticks)
                if (holdTicks == REQUIRED_TICKS) {
                    if (mc.hitResult instanceof BlockHitResult blockHit) {
                        // Sende finalen Überlastungs-Aufruf an den Server
                        StonesMod.PACKET_HANDLER.sendToServer(new PacketBindShrine(blockHit.getBlockPos(), REQUIRED_TICKS));
                        
                        // Der schauerlich-schöne, tiefe Saturn-Sound ertönt
                        mc.player.playSound(StonesMod.SHRINE_BIND.get(), 1.0f, 1.0f);
                    }
                }
            } else {
                // Weggeschaut -> Reset
                holdTicks = 0;
            }
        } else {
            // Taste losgelassen
            if (wasDown) {
                // War es ein kurzer Klick auf den Stein? (Öffnen des Schreins)
                if (holdTicks > 0 && holdTicks < CLICK_THRESHOLD && lookingAtRunestone) {
                     if (mc.hitResult instanceof BlockHitResult blockHit) {
                         StonesMod.PACKET_HANDLER.sendToServer(new PacketOpenShrine(blockHit.getBlockPos()));
                     }
                }
            }
            // Reset
            holdTicks = 0;
        }
        
        wasDown = isRightClickDown;
    }

    // --- 2. RENDER: HUD Overlay ---
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Wir nutzen den HOTBAR Layer als Anker, damit wir nicht mit Crosshair oder Chat kollidieren
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int width = event.getWindow().getGuiScaledWidth();
        int height = event.getWindow().getGuiScaledHeight();

        // --- A: Interaktion mit Runestone (Ladebalken) ---
        boolean lookingAtRunestone = false;
        if (mc.hitResult instanceof BlockHitResult blockHit) {
            if (mc.level.getBlockState(blockHit.getBlockPos()).getBlock() instanceof RunestoneBlock) {
                lookingAtRunestone = true;
            }
        }

        if (lookingAtRunestone) {
            // Balken zeichnen wenn wir halten
            if (holdTicks > 5 && holdTicks < REQUIRED_TICKS) {
                int barWidth = 100;
                int barHeight = 5;
                int x = (width - barWidth) / 2;
                int y = (height / 2) + 20;

                // Hintergrund
                graphics.fill(x, y, x + barWidth, y + barHeight, 0x80000000); 

                // Fortschritt
                float progress = (float) holdTicks / (float) REQUIRED_TICKS;
                int filledWidth = (int) (barWidth * progress);
                
                // Passend zum unheimlichen Void-Thema: Ein tiefes, düsteres Lila statt Cyan
                int color = 0xFF550055; 
                
                graphics.fill(x, y, x + filledWidth, y + barHeight, color);

                // Lokalisierter Ausrichtungs-Text
                graphics.drawCenteredString(mc.font, Component.translatable("gui.stones.shrine_binding"), width / 2, y + 8, 0x888888);
            }
            // Info Text wenn wir nur draufschauen
            else if (holdTicks <= 5) {
                int y = (height / 2) + 15;
                graphics.drawCenteredString(mc.font, Component.translatable("gui.stones.runestone_shrine_hud"), width / 2, y, 0xFFFFFF);
                
                float scale = 0.75f;
                graphics.pose().pushPose();
                graphics.pose().translate(width / 2.0, y + 12, 0);
                graphics.pose().scale(scale, scale, scale);
                
                graphics.drawCenteredString(mc.font, Component.translatable("gui.stones.shrine_interaction_info"), 0, 0, 0xFFFFFF);
                
                graphics.pose().popPose();
            }
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        // --- B: Navigation (Kompass) ---
        // Zeigen, wenn wir irgendeine Rune in der Hand haben
        boolean holdingRune = mc.player.getMainHandItem().getItem() instanceof StoneItem 
                           || mc.player.getOffhandItem().getItem() instanceof StoneItem;

        if (holdingRune) {
            // Capability abfragen
            mc.player.getCapability(PlayerShrineCapProvider.SHRINE_LINK).ifPresent(cap -> {
                GlobalPos pos = cap.getShrinePos();
                if (pos != null) {
                    renderCompass(graphics, mc, width, height, pos);
                }
            });
        }
    }

    private static void renderCompass(GuiGraphics graphics, Minecraft mc, int w, int h, GlobalPos target) {
        // Dimension Check
        if (!target.dimension().equals(mc.level.dimension())) {
            graphics.drawCenteredString(mc.font, Component.translatable("gui.stones.unknown_dimension"), w / 2, h - 65, 0xFFFFFF);
            return;
        }

        Vec3 playerPos = mc.player.position();
        double dx = target.pos().getX() - playerPos.x;
        double dz = target.pos().getZ() - playerPos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        // Winkelberechnung relativ zum Spielerblick
        double angleToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        double playerYaw = mc.player.getYRot();
        double relativeAngle = Mth.wrapDegrees(angleToTarget - playerYaw);

        String dirArrow = getDirectionArrow(relativeAngle);
        
        // Positionieren über der Hotbar (etwas höher als der Info-Text)
        int y = h - 65;
        
        graphics.drawCenteredString(mc.font, Component.translatable("gui.stones.compass_title"), w / 2, y - 10, 0xFFFFFF);
        
        Component distanceText = Component.translatable("gui.stones.compass_distance", String.format(java.util.Locale.ROOT, "%.0f", dist));
        Component layoutText = Component.translatable("gui.stones.compass_layout", dirArrow, distanceText);
        graphics.drawCenteredString(mc.font, layoutText, w / 2, y, 0xFFFFFF);
    }

    // Wandelt den relativen Winkel in einen Pfeil um
    private static String getDirectionArrow(double angle) {
        if (angle >= -22.5 && angle < 22.5) return "⬆";   // Vorne
        if (angle >= 22.5 && angle < 67.5) return "⬈";    // Rechts Vorne
        if (angle >= 67.5 && angle < 112.5) return "➡";   // Rechts
        if (angle >= 112.5 && angle < 157.5) return "⬊";  // Rechts Hinten
        if (angle >= 157.5 || angle < -157.5) return "⬇"; // Hinten
        if (angle >= -157.5 && angle < -112.5) return "⬋"; // Links Hinten
        if (angle >= -112.5 && angle < -67.5) return "⬅";  // Links
        if (angle >= -67.5 && angle < -22.5) return "⬉";   // Links Vorne
        return "?";
    }
}