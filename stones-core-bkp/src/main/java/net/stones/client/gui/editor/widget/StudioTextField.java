package net.stones.client.gui.editor.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.stones.client.gui.editor.StonesStudioScreen;

/**
 * Ein standardisiertes Textfeld für das Stones Studio.
 * Schützt vor dem stillschweigenden 32-Zeichen-Abschneide-Bug,
 * scrollt den Cursor bei Wertzuweisungen automatisch an den Anfang 
 * und hebt ungewollte Textmarkierungen sicher auf.
 */
public class StudioTextField extends EditBox {

    private final StonesStudioScreen screen;
    private Component customTooltip;

    public StudioTextField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message) {
        super(font, x, y, width, height, message);
        this.screen = screen;
        this.setMaxLength(512); 
    }

    public StudioTextField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message, Component tooltipText) {
        this(screen, font, x, y, width, height, message);
        this.customTooltip = tooltipText;
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
        this.setCursorPosition(0);
        this.setHighlightPos(0); // FIX: Entfernt die Vanilla-Textmarkierung und zentriert die Ansicht nach links!
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        
        // Intercept Hover Status und pushe den Tooltip sicher in die Hauptwarteschlange!
        if (this.isHovered && this.customTooltip != null) {
            screen.queueTooltip(this.customTooltip, mouseX, mouseY);
        }
    }
}