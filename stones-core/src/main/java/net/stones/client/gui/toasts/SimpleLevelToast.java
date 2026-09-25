package net.stones.client.gui.toasts;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

public class SimpleLevelToast implements Toast {
    
    private final Component text;
    private final int borderColor;
    private final long duration;

    // Einheitlicher Konstruktor für alle dynamischen Toasts
    public SimpleLevelToast(Component text, int borderColor, long duration) {
        this.text = text;
        this.borderColor = borderColor;
        this.duration = duration;
    }

    @Override
    public Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        // Maße: Klein und kompakt
        int width = toastComponent.getMinecraft().font.width(text) + 20;
        int height = 24;
        
        guiGraphics.fill(0, 0, width, height, 0xAA000000); // Halbtransparent Schwarz
        // Nutzt jetzt die dynamische Farbe aus der Config
        guiGraphics.renderOutline(0, 0, width, height, this.borderColor);

        // Text zentriert
        guiGraphics.drawString(toastComponent.getMinecraft().font, text, 10, 8, 0xFFFFFFFF, false);

        // Nutzt jetzt die dynamische Dauer aus der Config
        return timeSinceLastVisible >= this.duration ? Visibility.HIDE : Visibility.SHOW;
    }
}