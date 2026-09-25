package net.stones.visuals;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.stones.client.integration.StonesUIManager;
import net.stones.visuals.client.gui.RunestoneScreen;
import net.stones.visuals.client.gui.VisualRuneInfoScreen;
import net.stones.visuals.client.renderer.ClientDynamicLabelHandler;
import net.stones.visuals.integration.VisualsEventHandler;

public class VisualsInitializer {

    public static void start() {
        // WICHTIG: Mod-Event-Bus holen!
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Auf dem MOD-Bus registrieren, damit EntityRenderersEvent feuert!
        modBus.register(new VisualsEventHandler());

        // Font-Metrics für Schrein-Labels im Skinpack initialisieren
        ClientDynamicLabelHandler.init();

        StonesUIManager.RUNESTONE_SCREEN = RunestoneScreen::new;
        StonesUIManager.RUNE_INFO_SCREEN = VisualRuneInfoScreen::new;
    }
}