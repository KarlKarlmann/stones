package net.stones.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.stones.gui.EchoTraderMenu;
import net.stones.network.PacketBuyEcho;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class EchoTraderScreen extends AbstractContainerScreen<EchoTraderMenu> {

    // --- ARCHITEKTONISCHE ASSETS ---
    private static final ResourceLocation TEXTURE_BG = new ResourceLocation("stones", "textures/gui/echo_trader_bg.png");
    private static final ResourceLocation TRADER_HEAD = new ResourceLocation("stones", "textures/gui/echo_trader_head.png");
    private static final ResourceLocation EYE_TEXTURE = new ResourceLocation("stones", "textures/gui/echo_trader_eye.png");
    
    // --- KOSTEN ICONS ---
    private static final ResourceLocation XP_ICON = new ResourceLocation("stones", "textures/gui/icon_xp_bottle.png");
    private static final ResourceLocation LIFE_ICON = new ResourceLocation("stones", "textures/gui/icon_void_heart.png");

    // --- DIMENSIONEN ---
    private static final int TOTAL_WIDTH = 233;
    private static final int TOTAL_HEIGHT = 233;
    private static final int HEAD_W = 91;
    private static final int HEAD_H = 109;

    public EchoTraderScreen(EchoTraderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = TOTAL_WIDTH;
        this.imageHeight = TOTAL_HEIGHT;
        this.inventoryLabelY = 133; 
        this.titleLabelY = 8;
    }

    @Override
    protected void init() {
        super.init();
        this.leftPos = (this.width - this.imageWidth) / 2;
        this.topPos = (this.height - this.imageHeight) / 2;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        super.render(gui, mouseX, mouseY, partialTick); 
        
        // Kosten-Icons zeichnen
        this.renderCostIcons(gui);
        
        this.renderTooltip(gui, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        
        // 1. Hintergrund
        gui.blit(TEXTURE_BG, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);

        // 2. Verbindungslinien der Spirale
        renderSpiralLines(gui);

        // 3. Kopf-Overlay mit Augen-Animation
        renderAnimatedHead(gui, partialTick);
    }

    private void renderSpiralLines(GuiGraphics gui) {
        if (this.menu.slots.size() >= 13) {
            PoseStack pose = gui.pose();
            pose.pushPose();
            int color = 0x66FFD700; // Goldenes Echo
            for (int i = 1; i < 13; i++) {
                Slot prev = this.menu.slots.get(i-1);
                Slot curr = this.menu.slots.get(i);
                drawLine(gui, this.leftPos + prev.x + 9, this.topPos + prev.y + 9, 
                              this.leftPos + curr.x + 9, this.topPos + curr.y + 9, color);
            }
            pose.popPose();
        }
    }

    private void renderAnimatedHead(GuiGraphics gui, float partialTick) {
        float time = (System.currentTimeMillis() % 100000) / 1000f;
        float breathOffset = Mth.sin(time * 2.0f) * 1.5f; 
        int headX = this.leftPos;
        int headY = this.topPos + (int)breathOffset;

        // Augen (Hintergrund)
        float eyeAngle = time * (360f / 1.618f); 
        renderRotatingEye(gui, headX + 44, headY + 66, eyeAngle);
        renderRotatingEye(gui, headX + 57, headY + 67, -eyeAngle);

        // Kopf-Textur (Overlay)
        RenderSystem.enableBlend();
        gui.blit(TRADER_HEAD, headX, headY, 0, 0, HEAD_W, HEAD_H, HEAD_W, HEAD_H);
        RenderSystem.disableBlend();
    }

    private void renderRotatingEye(GuiGraphics gui, int centerX, int centerY, float angle) {
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        pose.translate(-8, -8, 0); 
        gui.blit(EYE_TEXTURE, 0, 0, 0, 0, 16, 16, 16, 16);
        pose.popPose();
    }

    /**
     * Zeichnet die Kosten-Icons.
     * Beide Icons sitzen nun oben rechts, um nicht mit der Item-Anzahl (unten rechts) zu kollidieren.
     */
    private void renderCostIcons(GuiGraphics gui) {
        for (int i = 0; i < 13; i++) {
            if (i >= this.menu.slots.size()) break;
            Slot slot = this.menu.slots.get(i);
            
            if (!slot.hasItem()) continue;

            ItemStack stack = slot.getItem();
            
            // Logik-Check: Ist es eine Opfergabe?
            boolean isSacrifice = stack.hasTag() && stack.getTag().getBoolean("EchoSacrifice");
            ResourceLocation icon = isSacrifice ? LIFE_ICON : XP_ICON;
            
            int sx = this.leftPos + slot.x;
            int sy = this.topPos + slot.y;
            
            // Einheitliche Positionierung oben rechts für beide Icon-Typen
            // sx + 10 schiebt es an den rechten Rand des 18px Slots
            // sy - 4 lässt es leicht über dem Slot schweben
            int ix = sx + 10;
            int iy = sy - 4;

            gui.pose().pushPose();
            // Z-Level 300 stellt sicher, dass die Icons über den Items gerendert werden
            gui.pose().translate(0, 0, 300); 
            RenderSystem.enableBlend();
            gui.blit(icon, ix, iy, 0, 0, 10, 10, 10, 10);
            gui.pose().popPose();
        }
    }

    private void drawLine(GuiGraphics gui, int x1, int y1, int x2, int y2, int color) {
        float a = ((color >> 24) & 0xFF) / 255.0F;
        float r = ((color >> 16) & 0xFF) / 255.0F;
        float g = ((color >> 8) & 0xFF) / 255.0F;
        float b = (color & 0xFF) / 255.0F;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(2.0F);

        Matrix4f matrix = gui.pose().last().pose();
        bufferbuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        bufferbuilder.vertex(matrix, x1, y1, 0).color(r, g, b, a).endVertex();
        bufferbuilder.vertex(matrix, x2, y2, 0).color(r, g, b, a).endVertex();
        tesselator.end();
    }

    @Override
    protected void renderTooltip(GuiGraphics gui, int x, int y) {
        if (this.menu.getCarried().isEmpty() && this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            if (this.hoveredSlot.index < 13) {
                ItemStack stack = this.hoveredSlot.getItem();
                List<Component> tooltip = new ArrayList<>(this.getTooltipFromContainerItem(stack));
                tooltip.add(Component.empty());

                if (stack.hasTag() && stack.getTag().getBoolean("EchoSacrifice")) {
                    int type = stack.getTag().getInt("SacrificeType");
                    switch(type) {
                        case 0 -> { tooltip.add(Component.literal("§7Opfere §c10% §7deiner Vitalität.")); tooltip.add(Component.literal("§aGewinn: Wenig Erfahrung.")); }
                        case 1 -> { tooltip.add(Component.literal("§7Opfere §c30-60% §7deiner Vitalität.")); tooltip.add(Component.literal("§aGewinn: Moderate Erfahrung.")); }
                        case 2 -> { tooltip.add(Component.literal("§4§lWARNUNG: §c75-100% §7Vitalitätsverlust.")); tooltip.add(Component.literal("§0§lKANN TÖDLICH SEIN.")); tooltip.add(Component.literal("§aGewinn: Massive Erfahrung.")); }
                    }
                } else {
                    int cost = PacketBuyEcho.getXpCost(stack);
                    boolean canAfford = this.minecraft.player.experienceLevel >= cost || this.minecraft.player.isCreative();
                    tooltip.add(Component.literal("Kosten: " + cost + " Level").withStyle(canAfford ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD));
                }
                gui.renderComponentTooltip(this.font, tooltip, x, y);
                return;
            }
        }
        super.renderTooltip(gui, x, y);
    }
}