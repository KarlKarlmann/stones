package net.stones.editor.client.event;

import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stones.editor.StonesEditorMod;
import net.stones.editor.client.gui.StonesStudioScreen;

@Mod.EventBusSubscriber(modid = StonesEditorMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StonesEditorClientEvents {

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        // Befehl: /stonesstudio
        event.getDispatcher().register(
            Commands.literal("stonesstudio")
                .executes(context -> {
                    Minecraft.getInstance().tell(() -> {
                        Minecraft.getInstance().setScreen(new StonesStudioScreen());
                    });
                    return 1;
                })
        );

        // Optionaler Alias: /stones studio
        event.getDispatcher().register(
            Commands.literal("stones")
                .then(Commands.literal("studio")
                    .executes(context -> {
                        Minecraft.getInstance().tell(() -> {
                            Minecraft.getInstance().setScreen(new StonesStudioScreen());
                        });
                        return 1;
                    })
                )
        );
    }
}