package net.stones.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stones.StonesMod;
import net.stones.client.gui.editor.StonesStudioScreen;
import net.stones.init.StonesModConfig;

import java.io.File;

/**
 * Steuert den Zugang zum Stones Studio im Pause-Menü 
 * und kümmert sich um das DAU-Onboarding (Toasts bei neuen Packs).
 * * AKTUALISIERT: Einbindung der voll-konfigurierbaren Pause-Menü-Schaltfläche.
 */
@Mod.EventBusSubscriber(modid = StonesMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StonesClientEvents {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PauseScreen pauseScreen) {
            
            // FIX: Prüft zuerst, ob der Knopf laut Config überhaupt gezeichnet werden soll
            if (!StonesModConfig.SHOW_PAUSE_BUTTON.get()) {
                return;
            }
            
            // Holt sich die Dimensionen dynamisch aus der Config
            int buttonWidth = StonesModConfig.PAUSE_BUTTON_WIDTH.get();
            int buttonHeight = StonesModConfig.PAUSE_BUTTON_HEIGHT.get();
            
            // Berechnet die exakten X/Y-Positionen anhand des gewählten Offsets und Abstands
            int x = (pauseScreen.width / 2) + StonesModConfig.PAUSE_BUTTON_X_OFFSET.get(); 
            int y = StonesModConfig.PAUSE_BUTTON_Y.get(); 
            
            event.addListener(Button.builder(
                Component.literal("§d✦ Stones Studio"),
                btn -> Minecraft.getInstance().setScreen(new StonesStudioScreen())
            ).bounds(x, y, buttonWidth, buttonHeight).build());
        }
    }

    /**
     * Fall 1 (Der DAU): Wenn der Spieler die Welt betritt, prüfen wir, ob inaktive 
     * Stones-Datapacks im Ordner liegen. Wenn ja, zeigen wir einen unaufdringlichen Toast.
     */
    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        File datapacksDir = new File(mc.gameDirectory, "datapacks");
        
        if (datapacksDir.exists() && datapacksDir.listFiles() != null) {
            String activeConfigPack = StonesModConfig.ACTIVE_WORKSPACE_PACK.get();
            boolean foundInactivePack = false;

            for (File file : datapacksDir.listFiles()) {
                String name = file.getName();
                if ((name.contains("stone") || name.contains("rune")) && !name.equals(activeConfigPack)) {
                    foundInactivePack = true;
                    break;
                }
            }

            if (foundInactivePack) {
                mc.getToasts().addToast(new SystemToast(
                    SystemToast.SystemToastIds.PACK_LOAD_FAILURE, 
                    Component.translatable("gui.stones.studio.toast.new_packs_found"),
                    Component.translatable("gui.stones.studio.toast.activation_hint")
                ));
            }
        }
    }
}