package net.stones.editor.init;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.stones.editor.StonesEditorMod;
import net.stones.editor.init.StonesEditorConfig;
import net.stones.util.ServerDatapackExporter;

import java.io.File;

@Mod.EventBusSubscriber(modid = StonesEditorMod.MODID)
public class StonesEditorCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stonesstudio")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("autoupdate")
                .executes(StonesEditorCommand::executeAutoUpdate)
            )
        );
    }

    private static int executeAutoUpdate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (source.getEntity() instanceof ServerPlayer player) {
            String activePack = StonesEditorConfig.ACTIVE_WORKSPACE_PACK.get();
            String newPackName = generateNextPackName(activePack);

            ServerDatapackExporter.createAndExportNewPack(player, newPackName);

            StonesEditorConfig.ACTIVE_WORKSPACE_PACK.set(newPackName);
            StonesEditorConfig.SPEC.save();

            player.sendSystemMessage(Component.translatable("chat.stones.studio.templates_updated.success", newPackName));

            player.getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                "reload"
            );
            return 1;
        }
        return 0;
    }

    private static String generateNextPackName(String activePack) {
        File datapacksDir = FMLPaths.GAMEDIR.get().resolve("datapacks").toFile();
        String baseName = (activePack == null || activePack.isEmpty()) ? "Stones_Project" : activePack;

        if (baseName.matches(".*_v\\d+$")) {
            baseName = baseName.substring(0, baseName.lastIndexOf("_v"));
        }

        int version = 2;
        String candidate = baseName + "_v" + version;
        while (new File(datapacksDir, candidate).exists()) {
            version++;
            candidate = baseName + "_v" + version;
        }
        return candidate;
    }
}