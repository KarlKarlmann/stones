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
        registration.addGuiContainerHandler(InventoryScreen.class, new IGuiContainerHandler<InventoryScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(InventoryScreen screen) {
                // Basis-Koordinaten aus dem ActionSystem
                int xBase = screen.width / 2 + 95;
                int yBase = screen.height - 22;
                
                // ÄNDERUNG: Statt nur 66 Pixel Breite zu reservieren, sperren wir 
                // den KOMPLETTEN Platz ab deiner Actionbar bis zum rechten Bildschirmrand.
                // Das zwingt JEI dazu, die Item-Slots einzukürzen und das Suchfenster über
                // unsere GUI zu schieben.
                int widthToRightEdge = screen.width - xBase;
                
                // Wir starten wieder bei yBase - 60 und gehen 82 Pixel nach unten.
                return List.of(new Rect2i(xBase, yBase - 60, widthToRightEdge, 82));
            }
        });
    }
}