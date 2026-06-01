package net.stones.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.stones.StonesMod;

import java.util.List;

@JeiPlugin
public class StonesJeiPlugin implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(StonesMod.MODID, "jei_plugin");
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        // Registriert den Ausschlussbereich für das Standard-Spielerinventar
        registration.addGuiContainerHandler(InventoryScreen.class, new IGuiContainerHandler<InventoryScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(InventoryScreen screen) {
                // Hier greifen wir deine genauen Basis-Koordinaten aus dem ActionSystem auf
                int xBase = screen.width / 2 + 95;
                int yBase = screen.height - 22;
                
                // Wir schützen einen großzügigen Bereich, der das Dropdown mit einschließt:
                // Breite: 66 Pixel (3 Slots á 22)
                // Höhe: 82 Pixel (22 für die Hauptleiste + 60 nach oben für 3 Dropdown-Slots)
                // Startpunkt-Y: yBase - 60 (Damit die Box weit genug oben anfängt)
                return List.of(new Rect2i(xBase, yBase - 60, 66, 82));
            }
        });
    }
}