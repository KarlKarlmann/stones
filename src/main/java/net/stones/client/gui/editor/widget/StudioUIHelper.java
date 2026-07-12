package net.stones.client.gui.editor.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.stones.client.gui.editor.StonesStudioScreen;

/**
 * Hilfsklasse für wiederkehrende UI-Aufgaben im Stones Studio.
 * Nutzt jetzt die Deferred Tooltip Queue des Hauptscreens!
 */
public class StudioUIHelper {

    /**
     * Zeichnet ein Text-Label auf den Bildschirm. Schwebt der Mauszeiger über dem Text,
     * wird der Tooltip an die Queue des Hauptscreens zur späteren Darstellung gesendet.
     */
    public static void drawLabelWithTooltip(StonesStudioScreen screen, GuiGraphics graphics, Font font, Component label, int x, int y, int mouseX, int mouseY, Component tooltipText) {
        graphics.drawString(font, label, x, y, 0xFFAAAAAA);
        
        int textWidth = font.width(label);
        int textHeight = font.lineHeight;

        // Bounding-Box Kollisionsprüfung für den Hover-Effekt.
        // Dank Ghost-Mouse wird mouseX/Y = -999 übergeben, falls ein Modal offen ist,
        // wodurch dieser Check automatisch sicher fehlschlägt und nichts durchblutet.
        if (mouseX >= x && mouseX <= x + textWidth && mouseY >= y && mouseY < y + textHeight) {
            if (tooltipText != null) {
                // Queue the tooltip instead of rendering it immediately!
                screen.queueTooltip(tooltipText, mouseX, mouseY);
            }
        }
    }
}