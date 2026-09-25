package net.stones.client.gui.editor.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.stones.client.gui.editor.StonesStudioScreen;

/**
 * Ein standardisierter Button für das Stones Studio.
 * Leitet Tooltips in die zentrale Deferred Queue des Screens um, 
 * anstatt die unvorhersehbare Vanilla-Formatierung zu nutzen.
 */
public class StudioButton extends Button {

    private final StonesStudioScreen screen;
    private final Component customTooltip;

    public StudioButton(StonesStudioScreen screen, int x, int y, int width, int height, Component message, OnPress onPress, Component customTooltip) {
        // DEFAULT_NARRATION ist die Standard-Vorlesefunktion von Minecraft für Barrierefreiheit
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.screen = screen;
        this.customTooltip = customTooltip;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Zeichnet den normalen Button
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        
        // Fängt den Hover-Status ab und schiebt den Text in unsere sichere Rendering-Queue
        // Die "Ghost Mouse" des Hauptscreens verhindert hier automatisch, dass Hintergrund-Buttons reagieren
        if (this.isHovered && this.customTooltip != null) {
            screen.queueTooltip(this.customTooltip, mouseX, mouseY);
        }
    }
}