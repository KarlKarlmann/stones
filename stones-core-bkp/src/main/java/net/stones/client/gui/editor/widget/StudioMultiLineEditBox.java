package net.stones.client.gui.editor.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import net.stones.client.gui.editor.StonesStudioScreen;
import net.stones.client.gui.editor.modal.AbstractStudioModal;

/**
 * Ein sicheres mehrzeiliges Textfeld für das Stones Studio.
 * Überlässt das Fokus-Routing nun sauber dem AbstractStudioModal.
 */
public class StudioMultiLineEditBox extends MultiLineEditBox {

    private final StonesStudioScreen screen;
    private Component customTooltip;

    public StudioMultiLineEditBox(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component placeholder, Component message) {
        super(font, x, y, width, height, placeholder, message);
        this.screen = screen;
    }

    public StudioMultiLineEditBox(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component placeholder, Component message, Component tooltipText) {
        this(screen, font, x, y, width, height, placeholder, message);
        this.customTooltip = tooltipText;
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        if (this.isHovered && this.customTooltip != null) {
            screen.queueTooltip(this.customTooltip, mouseX, mouseY);
        }
    }
    
    // HINWEIS: Die überschriebene mouseClicked-Methode wurde entfernt!
    // Vanilla Minecraft kann Klicks perfekt verarbeiten, wenn das AbstractStudioModal
    // das Fokus-Flag korrekt löscht und delegiert.
}