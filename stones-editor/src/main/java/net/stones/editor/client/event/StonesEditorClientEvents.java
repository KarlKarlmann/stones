package net.stones.editor.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stones.editor.StonesEditorMod;
import net.stones.editor.client.gui.StonesStudioScreen;
import net.stones.editor.network.StudioNetwork;

@Mod.EventBusSubscriber(modid = StonesEditorMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StonesEditorClientEvents {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        // Befehl: /stonesstudio
        event.getDispatcher().register(
            Commands.literal("stonesstudio")
                .executes(context -> tryOpenStudio())
        );

        // Alias: /stones studio
        event.getDispatcher().register(
            Commands.literal("stones")
                .then(Commands.literal("studio")
                    .executes(context -> tryOpenStudio())
                )
        );
    }

    private static int tryOpenStudio() {
        Minecraft mc = Minecraft.getInstance();
        StudioNetwork.VersionCheckResult result = StudioNetwork.checkServerVersion();

        switch (result.status()) {
            case MATCH -> {
                // Exakte Übereinstimmung -> Screen öffnen
                mc.tell(() -> mc.setScreen(new StonesStudioScreen()));
                return 1;
            }
            case MOD_MISSING -> {
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal(
                        "§cStones Studio kann nicht geöffnet werden: Der Server hat den Mod nicht installiert."
                    ));
                }
                return 0;
            }
            case VERSION_MISMATCH -> {
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal(
                        "§cStones Studio Versions-Konflikt! Client: v" + StudioNetwork.PROTOCOL_VERSION + 
                        " | Server: v" + result.serverVersion()
                    ));
                }
                return 0;
            }
            default -> {
                if (mc.player != null) {
                    mc.player.sendSystemMessage(Component.literal("§cNicht mit einem Welt-Server verbunden."));
                }
                return 0;
            }
        }
    }
}