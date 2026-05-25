package net.stones.client.gui.toasts;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;

public class SimpleLevelToast implements Toast {
    
    private final Component text;

    public SimpleLevelToast(int level, Component diffText) {
        this.text = diffText;
    }

    @Override
    public Visibility render(GuiGraphics guiGraphics, ToastComponent toastComponent, long timeSinceLastVisible) {
        // Maße: Klein und kompakt
        int width = toastComponent.getMinecraft().font.width(text) + 20;
        int height = 24;
        
        // Hintergrund: Dunkler Streifen mit Cyan-Rand (passend zum Mod-Thema)
        guiGraphics.fill(0, 0, width, height, 0xAA000000); // Halbtransparent Schwarz
        guiGraphics.renderOutline(0, 0, width, height, 0xFF00FFFF); // Cyan Outline

        // Text zentriert
        // Wir entfernen evtl. vorhandene "➤" für den Toast, um Platz zu sparen
        guiGraphics.drawString(toastComponent.getMinecraft().font, text, 10, 8, 0xFFFFFFFF, false);

        return timeSinceLastVisible >= 2500L ? Visibility.HIDE : Visibility.SHOW; // Kurz sichtbar (2.5s)
    }
}