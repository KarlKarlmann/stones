package net.stones.editor.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import net.stones.StonesMod;
import net.stones.enchantment.RuneEnchantment;
import net.stones.enchantment.RuneStat;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * =========================================================================================
 * ARCHITEKTUR: WELT-GEBUNDENES RUNTIME-DATAPACK
 * =========================================================================================
 * 1. WORKSPACES (global in <gameDir>/datapacks/<ProjektName>/):
 *    - Beinhalten AUSSCHLIESSLICH 'data/stones_workspace/'.
 *    - Reines Staging / Sandbox. Werden von Minecraft niemals direkt als 'stones:' geladen.
 * 
 * 2. WELT-RUNTIME (<world>/datapacks/stones_runtime/):
 *    - Liegt nativ im Datapack-Ordner der aktuell laufenden Welt.
 *    - Hält in seiner 'pack.mcmeta' das Feld 'stones.source_project' fest.
 *    - Dadurch weiß jede Welt autark, welches Workspace-Projekt sie repräsentiert.
 *    - Keine globale Config mehr nötig!
 * 
 * 3. EXPORT / APPLY:
 *    - Leert 'data/stones/' in der Welt-Runtime vollständig.
 *    - Befüllt es frisch aus dem gewählten Workspace.
 *    - Aktiviert 'file/stones_runtime' nativ mit Top-Priorität im WorldData der Welt.
 * =========================================================================================
 */
public class ServerDatapackExporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final String RUNTIME_PACK_NAME = "stones_runtime";

    /**
     * Ermittelt das aktuell in dieser Welt verknüpfte Quell-Projekt direkt aus der pack.mcmeta
     * des weltgebundenen Runtime-Packs.
     */
    public static String getActiveProjectForWorld(MinecraftServer server) {
        if (server == null) return "";
        try {
            File worldDatapacksDir = server.getWorldPath(LevelResource.DATAPACK_DIR).toFile();
            File runtimeMeta = new File(worldDatapacksDir, RUNTIME_PACK_NAME + "/pack.mcmeta");
            if (runtimeMeta.exists()) {
                String metaContent = Files.readString(runtimeMeta.toPath(), StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(metaContent).getAsJsonObject();
                if (json.has("stones") && json.getAsJsonObject("stones").has("source_project")) {
                    return json.getAsJsonObject("stones").get("source_project").getAsString();
                }
            }
        } catch (Exception e) {
            StonesMod.LOGGER.error("[Stones Editor] Konnte aktives Projekt der Welt nicht auslesen: ", e);
        }
        return "";
    }

    /**
     * Kompiliert das ausgewählte Workspace-Projekt in das weltgebundene Datapack
     * '<world>/datapacks/stones_runtime/' und aktiviert es nativ im Server.
     */
    public static void buildAndEnableDatapack(ServerPlayer player, String activePackName) {
        if (activePackName == null || activePackName.isBlank() || player.getServer() == null) return;

        MinecraftServer server = player.getServer();
        try {
            File globalDatapacksDir = FMLPaths.GAMEDIR.get().resolve("datapacks").toFile();
            File sourcePackDir = new File(globalDatapacksDir, activePackName.replaceAll("[^a-zA-Z0-9_.-]", "_"));

            // Ziel-Ordner ist der native Datapack-Ordner der Welt!
            File worldDatapacksDir = server.getWorldPath(LevelResource.DATAPACK_DIR).toFile();
            File runtimePackDir = new File(worldDatapacksDir, RUNTIME_PACK_NAME);
            if (!runtimePackDir.exists()) {
                runtimePackDir.mkdirs();
            }

            // 1. pack.mcmeta mit Quell-Referenz schreiben
            File metaFile = new File(runtimePackDir, "pack.mcmeta");
            JsonObject meta = new JsonObject();
            JsonObject pack = new JsonObject();
            pack.addProperty("pack_format", 15);
            pack.addProperty("description", "Stones Mod Runtime: " + activePackName);
            meta.add("pack", pack);

            // Speichert die Verknüpfung direkt in der Datei des Spielstands
            JsonObject stonesMeta = new JsonObject();
            stonesMeta.addProperty("source_project", activePackName);
            meta.add("stones", stonesMeta);

            Files.writeString(metaFile.toPath(), GSON.toJson(meta), StandardCharsets.UTF_8);

            // 2. data/stones/ im Ziel leeren & frisch aufsetzen
            File targetEnchDir = new File(runtimePackDir, "data/stones/enchantments");
            File targetScriptsDir = new File(runtimePackDir, "data/stones/scripts");
            wipeDirectory(targetEnchDir);
            wipeDirectory(targetScriptsDir);
            targetEnchDir.mkdirs();
            targetScriptsDir.mkdirs();

            // 3. Workspace-Quelldaten kopieren
            File sourceEnchDir = new File(sourcePackDir, "data/stones_workspace/enchantments");
            File sourceScriptsDir = new File(sourcePackDir, "data/stones_workspace/scripts");

            if (sourceScriptsDir.exists() && sourceScriptsDir.listFiles() != null) {
                for (File f : sourceScriptsDir.listFiles()) {
                    if (f.isFile() && f.getName().endsWith(".js")) {
                        Files.copy(f.toPath(), new File(targetScriptsDir, f.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            if (sourceEnchDir.exists() && sourceEnchDir.listFiles() != null) {
                for (File f : sourceEnchDir.listFiles()) {
                    if (f.isFile() && f.getName().endsWith(".json") && !f.getName().endsWith(".bak")) {
                        try {
                            String content = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

                            if (json.has("raw_script")) {
                                String rawScript = json.get("raw_script").getAsString().trim();
                                if (!rawScript.isEmpty()) {
                                    String scriptFileName = rawScript;
                                    if (scriptFileName.contains("/")) scriptFileName = scriptFileName.substring(scriptFileName.lastIndexOf('/') + 1);
                                    if (scriptFileName.contains("\\")) scriptFileName = scriptFileName.substring(scriptFileName.lastIndexOf('\\') + 1);
                                    if (!scriptFileName.endsWith(".js")) scriptFileName += ".js";

                                    json.addProperty("raw_script", StonesMod.MODID + ":scripts/" + scriptFileName);
                                }
                            }

                            File targetFile = new File(targetEnchDir, f.getName());
                            Files.writeString(targetFile.toPath(), GSON.toJson(json), StandardCharsets.UTF_8);
                        } catch (Exception e) {
                            StonesMod.LOGGER.error("[Stones Editor] Fehler beim Kompilieren der Rune " + f.getName(), e);
                        }
                    }
                }
            }

            // 4. Runtime-Datapack in dieser Welt scharfschalten
            enablePackWithTopPriority(server, RUNTIME_PACK_NAME);
            StonesMod.LOGGER.info("[Stones Editor] Workspace '{}' erfolgreich in Welt-Datapack '{}' kompiliert.", activePackName, RUNTIME_PACK_NAME);

        } catch (Exception e) {
            StonesMod.LOGGER.error("[Stones Editor] Fehler beim Kompilieren nach stones_runtime: ", e);
        }
    }

    private static void wipeDirectory(File dir) {
        if (dir.exists() && dir.listFiles() != null) {
            for (File f : dir.listFiles()) {
                if (f.isDirectory()) {
                    wipeDirectory(f);
                }
                f.delete();
            }
        }
    }

    public static void enablePackWithTopPriority(MinecraftServer server, String packName) {
        if (server == null) return;
        try {
            PackRepository repo = server.getPackRepository();
            repo.reload();

            Collection<String> available = repo.getAvailableIds();
            String targetPackId = null;

            for (String id : available) {
                if (id.equals("file/" + packName) || id.endsWith("/" + packName) || id.equals(packName)) {
                    targetPackId = id;
                    break;
                }
            }

            if (targetPackId != null) {
                List<String> selected = new ArrayList<>(repo.getSelectedIds());
                selected.remove(targetPackId);
                selected.add(targetPackId);
                repo.setSelected(selected);

                List<String> enabledPacks = new ArrayList<>(server.getWorldData().getDataConfiguration().dataPacks().getEnabled());
                if (!enabledPacks.contains(targetPackId)) {
                    enabledPacks.add(targetPackId);
                }
                List<String> disabledPacks = new ArrayList<>(server.getWorldData().getDataConfiguration().dataPacks().getDisabled());
                disabledPacks.remove(targetPackId);

                server.getWorldData().setDataConfiguration(
                    new WorldDataConfiguration(
                        new DataPackConfig(enabledPacks, disabledPacks),
                        server.getWorldData().getDataConfiguration().enabledFeatures()
                    )
                );

                StonesMod.LOGGER.info("[Stones Editor] Welt-Datapack '{}' aktiviert & priorisiert.", targetPackId);
            } else {
                StonesMod.LOGGER.warn("[Stones Editor] Datapack '{}' nicht in availableIds gefunden! Gefunden: {}", packName, available);
            }
        } catch (Exception e) {
            StonesMod.LOGGER.error("[Stones Editor] Fehler beim Aktivieren von Datapack " + packName, e);
        }
    }

    public static void createAndExportNewPack(ServerPlayer player, String packName) {
        try {
            File datapacksDir = FMLPaths.GAMEDIR.get().resolve("datapacks").toFile();
            File packDir = new File(datapacksDir, packName.replaceAll("[^a-zA-Z0-9_.-]", "_"));

            if (!packDir.exists()) {
                packDir.mkdirs();
            }

            File metaFile = new File(packDir, "pack.mcmeta");
            JsonObject meta = new JsonObject();
            JsonObject pack = new JsonObject();
            pack.addProperty("pack_format", 15);
            pack.addProperty("description", "Stones Studio Workspace: " + packName);
            meta.add("pack", pack);
            Files.writeString(metaFile.toPath(), GSON.toJson(meta), StandardCharsets.UTF_8);

            File enchantmentsDir = new File(packDir, "data/stones_workspace/enchantments");
            File scriptsDir = new File(packDir, "data/stones_workspace/scripts");
            if (!enchantmentsDir.exists()) enchantmentsDir.mkdirs();
            if (!scriptsDir.exists()) scriptsDir.mkdirs();

            int exportCount = exportAllRunesToDir(enchantmentsDir, scriptsDir);

            // Initial für diese Welt scharfschalten
            buildAndEnableDatapack(player, packName);

            player.sendSystemMessage(Component.literal("§a[Stones Server] Projekt '" + packName + "' erfolgreich erstellt! (" + exportCount + " Enchantments)"));
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§c[Stones Server] Fehler beim Erstellen des Datapacks: " + e.getMessage()));
            StonesMod.LOGGER.error("[Stones Server] Fehler beim Exportieren des Datapacks: ", e);
        }
    }

    public static int exportAllRunesToDir(File enchantmentsDir, File scriptsDir) {
        int exportCount = 0;
        if (!enchantmentsDir.exists()) enchantmentsDir.mkdirs();
        if (!scriptsDir.exists()) scriptsDir.mkdirs();

        for (Enchantment enchantment : ForgeRegistries.ENCHANTMENTS.getValues()) {
            if (enchantment instanceof RuneEnchantment rune) {
                ResourceLocation registryId = ForgeRegistries.ENCHANTMENTS.getKey(rune);
                if (registryId != null) {
                    ResourceLocation expectedStonesId = new ResourceLocation(StonesMod.MODID, registryId.getPath());
                    if (!ForgeRegistries.ENCHANTMENTS.containsKey(expectedStonesId)) {
                        continue;
                    }

                    ResourceLocation originalScriptLoc = rune.getRawScriptPath() != null ? ResourceLocation.tryParse(rune.getRawScriptPath()) : null;
                    boolean isForeignNamespace = originalScriptLoc != null 
                        && !originalScriptLoc.getNamespace().equalsIgnoreCase(StonesMod.MODID) 
                        && !originalScriptLoc.getNamespace().equalsIgnoreCase("stones_workspace");

                    if (!isForeignNamespace && rune.getRawScriptContent() != null && !rune.getRawScriptContent().isBlank()) {
                        String scriptFileName = resolveScriptFileName(rune, registryId);
                        File scriptFile = new File(scriptsDir, scriptFileName + ".js");
                        try {
                            Files.writeString(scriptFile.toPath(), rune.getRawScriptContent(), StandardCharsets.UTF_8);
                        } catch (Exception e) {
                            StonesMod.LOGGER.error("[Stones] Fehler beim Schreiben des Skripts für " + registryId + ": ", e);
                        }
                    }

                    JsonObject serialized = serializeRune(rune);
                    File runeFile = new File(enchantmentsDir, registryId.getPath() + ".json");

                    try (FileWriter writer = new FileWriter(runeFile, StandardCharsets.UTF_8)) {
                        GSON.toJson(serialized, writer);
                        exportCount++;
                    } catch (Exception e) {
                        StonesMod.LOGGER.error("[Stones] Fehler beim Schreiben der Rune " + registryId + " ins Datapack: ", e);
                    }
                }
            }
        }
        return exportCount;
    }

    public static JsonObject serializeRune(RuneEnchantment rune) {
        JsonObject json = new JsonObject();

        ResourceLocation trueId = ForgeRegistries.ENCHANTMENTS.getKey(rune);
        if (trueId != null) {
            json.addProperty("override_registry_id", trueId.toString());
        }

        json.addProperty("type", rune.type.name());

        String name = getPrivateFieldString(rune, "customName");
        if (name != null) json.addProperty("name", name);

        String desc = getPrivateFieldString(rune, "customDescription");
        if (desc != null) json.addProperty("description", desc);

        String icon = getPrivateFieldString(rune, "iconPath");
        if (icon != null) json.addProperty("icon", icon);

        json.addProperty("required_level", rune.baseRequiredLevel);
        json.addProperty("factor", rune.factor);

        if (rune.isCurse()) {
            json.addProperty("is_curse", true);
        }

        boolean discoverable = getPrivateFieldBoolean(rune, "discoverable", true);
        if (!discoverable) {
            json.addProperty("discoverable", false);
        }

        json.addProperty("max_level", rune.getMaxLevel());

        if (rune.targetAttribute != null) {
            ResourceLocation attrId = ForgeRegistries.ATTRIBUTES.getKey(rune.targetAttribute);
            if (attrId != null) {
                json.addProperty("attribute", attrId.toString());
            }
            if (rune.operation != null) {
                json.addProperty("operation", rune.operation.name());
            }
        } else if (rune.targetEffect != null) {
            ResourceLocation effId = ForgeRegistries.MOB_EFFECTS.getKey(rune.targetEffect);
            if (effId != null) {
                json.addProperty("effect", effId.toString());
            }
        }

        if (!rune.getStats().isEmpty()) {
            JsonArray statsArray = new JsonArray();
            for (RuneStat stat : rune.getStats()) {
                JsonObject sObj = new JsonObject();
                sObj.addProperty("id", stat.id());
                sObj.addProperty("label", stat.label());
                sObj.addProperty("type", stat.type());
                sObj.addProperty("base", stat.base());
                if (stat.perLevel() != 0.0f) {
                    sObj.addProperty("per_level", stat.perLevel());
                }
                sObj.addProperty("scaling", stat.scaling());
                if (stat.displayFactor() != 1.0f) {
                    sObj.addProperty("display_factor", stat.displayFactor());
                }
                if (stat.suffix() != null && !stat.suffix().isEmpty()) {
                    sObj.addProperty("suffix", stat.suffix());
                }
                if (stat.min() != null) sObj.addProperty("min", stat.min());
                if (stat.max() != null) sObj.addProperty("max", stat.max());
                statsArray.add(sObj);
            }
            json.add("stats", statsArray);
        }

        if (rune.getRawScriptPath() != null) {
            json.addProperty("raw_script", rune.getRawScriptPath());
        } else if (rune.getRawScriptContent() != null && !rune.getRawScriptContent().isBlank()) {
            String scriptFileName = resolveScriptFileName(rune, trueId);
            json.addProperty("raw_script", StonesMod.MODID + ":scripts/" + scriptFileName + ".js");
        } else if (rune.getRawBehaviors() != null && !rune.getRawBehaviors().isEmpty()) {
            json.add("behaviors", rune.getRawBehaviors().deepCopy());
        }

        if (trueId != null) {
            TemplateHashHelper.ensureHashExists(json, trueId.getPath() + ".json");
        }

        return json;
    }

    private static String resolveScriptFileName(RuneEnchantment rune, @Nullable ResourceLocation fallbackId) {
        if (rune.getRawScriptPath() != null) {
            ResourceLocation loc = ResourceLocation.tryParse(rune.getRawScriptPath());
            if (loc != null) {
                String path = loc.getPath();
                int lastSlash = path.lastIndexOf('/');
                String fn = lastSlash != -1 ? path.substring(lastSlash + 1) : path;
                if (fn.endsWith(".js")) {
                    fn = fn.substring(0, fn.length() - 3);
                }
                if (!fn.isBlank()) {
                    return fn;
                }
            }
        }
        return fallbackId != null ? fallbackId.getPath() : rune.getLogicalId();
    }

    private static String getPrivateFieldString(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object val = f.get(obj);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean getPrivateFieldBoolean(Object obj, String fieldName, boolean def) {
        try {
            java.lang.reflect.Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.getBoolean(obj);
        } catch (Exception e) {
            return def;
        }
    }
}