package net.stones.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stones.StonesMod;
import net.stones.client.gui.editor.StonesStudioScreen;
import net.stones.init.StonesModConfig;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Steuert den Zugang zum Stones Studio im Pause-Menü 
 * und kümmert sich um das DAU-Onboarding (Toasts bei neuen Packs & Update-Hinweise im Chat).
 */
@Mod.EventBusSubscriber(modid = StonesMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StonesClientEvents {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof PauseScreen pauseScreen) {
            
            if (!StonesModConfig.SHOW_PAUSE_BUTTON.get()) {
                return;
            }
            
            int buttonWidth = StonesModConfig.PAUSE_BUTTON_WIDTH.get();
            int buttonHeight = StonesModConfig.PAUSE_BUTTON_HEIGHT.get();
            
            int x = (pauseScreen.width / 2) + StonesModConfig.PAUSE_BUTTON_X_OFFSET.get(); 
            int y = StonesModConfig.PAUSE_BUTTON_Y.get(); 
            
            event.addListener(Button.builder(
                Component.translatable("gui.stones.studio.pause_button"),
                btn -> Minecraft.getInstance().setScreen(new StonesStudioScreen())
            ).bounds(x, y, buttonWidth, buttonHeight).build());
        }
    }

    /**
     * Prüft beim Betreten der Welt:
     * 1. Liegen inaktive Datapacks im Ordner? (Toast-Hinweis)
     * 2. Haben sich die Grund-Vorlagen der Mod seit Erstellung des aktiven Projekts geändert? (Chat-Hinweis)
     */
}