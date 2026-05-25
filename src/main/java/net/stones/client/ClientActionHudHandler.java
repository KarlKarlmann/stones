package net.stones.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stones.StonesMod;
import net.stones.features.ActionSystem;

@Mod.EventBusSubscriber(modid = StonesMod.MODID, value = Dist.CLIENT)
public class ClientActionHudHandler {

    private static final ResourceLocation SLOT_TEX = new ResourceLocation("stones", "textures/gui/action_slot.png");

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;

        renderActionSlots(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    }

    private static void renderActionSlots(GuiGraphics graphics, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        
        int startX = width / 2 + 95; 
        int y = height - 22; 

        for (int i = 1; i <= 3; i++) {
            String actionId = ActionSystem.getClientSlot(i);
            if (actionId.isEmpty()) continue;

            ResourceLocation icon = ActionSystem.getActionIcon(actionId);
            if (icon == null) continue;

            int slotX = startX + (i - 1) * 22;

            graphics.fill(slotX, y, slotX + 20, y + 20, 0x66000000);
            graphics.renderOutline(slotX, y, 20, 20, 0xFF444444);

            // 1. ZENTRALE COOLDOWN-API NUTZEN!
            // Das HUD muss nicht mehr wissen, von welcher Mod die Action stammt.
            int cooldownSeconds = ActionSystem.getActionCooldown(actionId);
            boolean isOnCooldown = cooldownSeconds > 0;

            if (isOnCooldown) {
                RenderSystem.setShaderColor(0.3f, 0.3f, 0.3f, 1.0f);
                graphics.blit(icon, slotX + 2, y + 2, 0, 0, 16, 16, 16, 16);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, 200);
                graphics.drawCenteredString(mc.font, String.valueOf(cooldownSeconds), slotX + 10, y + 6, 0xFFFFFF);
                graphics.pose().popPose();
            } else {
                graphics.blit(icon, slotX + 2, y + 2, 0, 0, 16, 16, 16, 16);
            }

            String keyName = ActionSystem.getKeyName(i);
            graphics.pose().pushPose();
            graphics.pose().scale(0.5f, 0.5f, 0.5f);
            graphics.drawString(mc.font, keyName, (slotX + 14) * 2, (y + 14) * 2, 0xFFFF00, true);
            graphics.pose().popPose();
        }
    }
}