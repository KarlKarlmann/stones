package net.stones.transpiler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.stones.StonesMod;
import net.stones.init.StonesModConfig;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Mod.EventBusSubscriber(modid = StonesMod.MODID)
public class StonesScriptPipeline {

    private static final String TARGET_DIR = "kubejs/server_scripts/stones_generated";

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        rebuildAll(event.getServer());
    }

    public static void rebuildAll(MinecraftServer server) {
        File jsDir = FMLPaths.GAMEDIR.get().resolve(TARGET_DIR).toFile();
        if (jsDir.exists()) {
            File[] old = jsDir.listFiles((dir, name) -> name.endsWith(".js"));
            if (old != null) {
                for (File f : old) f.delete();
            }
        } else {
            jsDir.mkdirs();
        }

        // Sicherstellen, dass der Helper in kubejs/server_scripts/ existiert:
        ensureHelperScriptDeployed();

        String activePack = StonesModConfig.ACTIVE_WORKSPACE_PACK.get();
        if (activePack == null || activePack.isEmpty()) return;

        File enchantDir = FMLPaths.GAMEDIR.get().resolve("datapacks/" + activePack + "/data/stones_workspace/enchantments").toFile();
        if (!enchantDir.exists()) return;

        File[] files = enchantDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null) return;

        for (File jsonFile : files) {
            try (FileReader reader = new FileReader(jsonFile, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				if (!json.has("behaviors") || json.getAsJsonArray("behaviors").isEmpty()) {
					continue;
				}
                String js = StonesTranspiler.transpile(jsonFile.getName(), json);
                File target = new File(jsDir, jsonFile.getName().replace(".json", ".js"));
                Files.writeString(target.toPath(), js, StandardCharsets.UTF_8);
            } catch (Exception e) {
                StonesMod.LOGGER.error("Fehler beim Transpilieren: " + jsonFile.getName(), e);
            }
        }
        StonesMod.LOGGER.info("[Stones Pipeline] Alle Skripte nach KubeJS transpiliert.");
    }

    /**
     * Wird direkt beim Klick auf <Apply> / Übernehmen aufgerufen.
     * Transpiliert alle JSONs frisch und triggert den KubeJS Server-Reload.
     */
    public static void rebuildAndReload(MinecraftServer server) {
        if (server == null) return;
        rebuildAll(server);

        CommandSourceStack source = server.createCommandSourceStack()
                .withSuppressedOutput()
                .withPermission(4);
        server.getCommands().performPrefixedCommand(source, "kubejs reload server_scripts");
        StonesMod.LOGGER.info("[Stones Pipeline] KubeJS Server-Scripts nach <Apply> neu geladen.");
    }

    private static void ensureHelperScriptDeployed() {
        try {
            File targetHelper = FMLPaths.GAMEDIR.get().resolve("kubejs/server_scripts/00_stones_helper.js").toFile();

                targetHelper.getParentFile().mkdirs();
                try (var is = StonesScriptPipeline.class.getResourceAsStream("/kubejs_scripts/00_stones_helper.js")) {
                    if (is != null) {
                        java.nio.file.Files.copy(is, targetHelper.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        StonesMod.LOGGER.info("[Stones Pipeline] 00_stones_helper.js automatisch exportiert.");
                    }
                }

        } catch (Exception e) {
            StonesMod.LOGGER.error("[Stones Pipeline] Konnte Helper-Skript nicht exportieren: ", e);
        }
    }
}