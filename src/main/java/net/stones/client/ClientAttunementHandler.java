package net.stones.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.GlobalPos;
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
        
        // Raytrace checken
        if (mc.hitResult instanceof BlockHitResult blockHit) {
             if (mc.level.getBlockState(blockHit.getBlockPos()).getBlock() instanceof RunestoneBlock) {
                 lookingAtRunestone = true;
             }
        }

        if (isRightClickDown) {
            // Taste gedrückt halten
            if (lookingAtRunestone) {
                holdTicks++;
                
                // Trigger Bindung nach 3 Sekunden
                if (holdTicks == REQUIRED_TICKS) {
                    if (mc.hitResult instanceof BlockHitResult blockHit) {
                        StonesMod.PACKET_HANDLER.sendToServer(new PacketBindShrine(blockHit.getBlockPos()));
                        // Clientseitiges Feedback (Server sendet auch eins, aber Latenz minimieren)
                        mc.player.playSound(net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    }
                }
            } else {
                // Weggeschaut -> Reset
                holdTicks = 0;
            }
        } else {
            // Taste losgelassen
            if (wasDown) {
                // War es ein kurzer Klick auf den Stein?
                if (holdTicks > 0 && holdTicks < CLICK_THRESHOLD && lookingAtRunestone) {
                     // GUI Öffnen
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
                int color = 0xFF00FFCC; // Magisches Cyan
                
                graphics.fill(x, y, x + filledWidth, y + barHeight, color);

                String text = "Seelenbindung...";
                graphics.drawCenteredString(mc.font, text, width / 2, y + 8, 0xFFFFFF);
            }
            // Info Text wenn wir nur draufschauen
            else if (holdTicks <= 5) {
                int y = (height / 2) + 15;
                graphics.drawCenteredString(mc.font, "§6Runestone Schrein", width / 2, y, 0xFFFFFF);
                
                float scale = 0.75f;
                graphics.pose().pushPose();
                graphics.pose().translate(width / 2.0, y + 12, 0);
                graphics.pose().scale(scale, scale, scale);
                
                String info = "§7[Rechtsklick] Öffnen   §7[Halten] Binden";
                graphics.drawCenteredString(mc.font, info, 0, 0, 0xFFFFFF);
                
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
            String txt = "§dUnbekannte Dimension";
            graphics.drawCenteredString(mc.font, txt, w / 2, h - 65, 0xFFFFFF);
            return;
        }

        Vec3 playerPos = mc.player.position();
        double dx = target.pos().getX() - playerPos.x;
        double dz = target.pos().getZ() - playerPos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);

        // Winkelberechnung relativ zum Spielerblick
        // atan2 gibt Winkel in Radians (-PI bis PI). Umrechnen in Grad.
        // Minecraft Yaw ist 0 = Süden, wir müssen das korrigieren.
        double angleToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90;
        double playerYaw = mc.player.getYRot();
        double relativeAngle = Mth.wrapDegrees(angleToTarget - playerYaw);

        // Render
        String distStr = String.format("§b%.0fm", dist);
        String dirArrow = getDirectionArrow(relativeAngle);
        
        // Positionieren über der Hotbar (etwas höher als der Info-Text)
        int y = h - 65;
        
        graphics.drawCenteredString(mc.font, "§6✦ Schrein Resonanz ✦", w / 2, y - 10, 0xFFFFFF);
        graphics.drawCenteredString(mc.font, dirArrow + " " + distStr + " " + dirArrow, w / 2, y, 0xFFFFFF);
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