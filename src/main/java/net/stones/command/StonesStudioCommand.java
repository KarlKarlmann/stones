package net.stones.command;

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
import net.stones.StonesMod;
import net.stones.init.StonesModConfig;
import net.stones.util.ServerDatapackExporter;

import java.io.File;

/**
 * Registriert Serverbefehle für das Stones Studio.
 * Ermöglicht direkte Updates und Projekterstellungen aus dem Chat heraus.
 */
@Mod.EventBusSubscriber(modid = StonesMod.MODID)
public class StonesStudioCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("stonesstudio")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("autoupdate")
                .executes(StonesStudioCommand::executeAutoUpdate)
            )
        );
    }

    private static int executeAutoUpdate(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (source.getEntity() instanceof ServerPlayer player) {
            String activePack = StonesModConfig.ACTIVE_WORKSPACE_PACK.get();
            String newPackName = generateNextPackName(activePack);

            // 1. Neues Pack mit den aktuellen Templates aus der JAR erstellen
            ServerDatapackExporter.createAndExportNewPack(player, newPackName);

            // 2. Neues Pack in den Configs als aktiv eintragen & speichern
            StonesModConfig.ACTIVE_WORKSPACE_PACK.set(newPackName);
            StonesModConfig.SPEC.save();

            // 3. Erfolgsmeldung & Server-Reload ausführen
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

        // Bereits vorhandene _vX Suffixe entfernen für saubere Inkrementierung
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