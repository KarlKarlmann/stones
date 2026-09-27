package net.stones.editor.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
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

public class ServerDatapackExporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void createAndExportNewPack(ServerPlayer player, String packName) {
        try {
            File datapacksDir = FMLPaths.GAMEDIR.get().resolve("datapacks").toFile();
            File packDir = new File(datapacksDir, packName.replaceAll("[^a-zA-Z0-9_.-]", "_"));

            if (!packDir.exists()) {
                packDir.mkdirs();
            }

            // 1. pack.mcmeta schreiben
            File metaFile = new File(packDir, "pack.mcmeta");
            JsonObject meta = new JsonObject();
            JsonObject pack = new JsonObject();
            pack.addProperty("pack_format", 15);
            pack.addProperty("description", "Stones Studio Workspace: " + packName);
            meta.add("pack", pack);

            Files.writeString(metaFile.toPath(), GSON.toJson(meta), StandardCharsets.UTF_8);

            // 2. data/stones_workspace/enchantments und data/stones_workspace/scripts anlegen
            File enchantmentsDir = new File(packDir, "data/stones_workspace/enchantments");
            File scriptsDir = new File(packDir, "data/stones_workspace/scripts");
            if (!enchantmentsDir.exists()) enchantmentsDir.mkdirs();
            if (!scriptsDir.exists()) scriptsDir.mkdirs();

            // 3. Alle registrierten, erwachten Hüllen serialisieren und speichern
            int exportCount = exportAllRunesToDir(enchantmentsDir, scriptsDir);

            player.sendSystemMessage(Component.literal("§a[Stones Server] Datapack '" + packName + "' erfolgreich erstellt! (" + exportCount + " Enchantments)"));
            StonesMod.LOGGER.info("[Stones Server] Datapack '{}' wurde von Spieler {} erstellt ({} Enchantments).", packName, player.getName().getString(), exportCount);

        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("§c[Stones Server] Fehler beim Erstellen des Datapacks: " + e.getMessage()));
            StonesMod.LOGGER.error("[Stones Server] Fehler beim Exportieren des Datapacks durch Spieler " + player.getName().getString(), e);
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
                    // Prüft, ob ein Enchantment mit "stones:<enchantmentname>" existiert
                    ResourceLocation expectedStonesId = new ResourceLocation(StonesMod.MODID, registryId.getPath());
                    if (!ForgeRegistries.ENCHANTMENTS.containsKey(expectedStonesId)) {
                        continue;
                    }

                    // 1. Skripte exportieren: stones: und stones_workspace: gehören zu uns!
                    // Echte Dritt-Mods (z. B. other_mod:kube_js/...) werden nicht angefasst.
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

                    // 2. Serialisiere das JSON
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

        // 4. raw_script Verlinkung
        ResourceLocation originalScriptLoc = rune.getRawScriptPath() != null ? ResourceLocation.tryParse(rune.getRawScriptPath()) : null;
        boolean isForeignNamespace = originalScriptLoc != null 
            && !originalScriptLoc.getNamespace().equalsIgnoreCase(StonesMod.MODID) 
            && !originalScriptLoc.getNamespace().equalsIgnoreCase("stones_workspace");

        if (rune.getRawScriptPath() != null) {
            // Behält die originale ResourceLocation bei (z.B. "stones:scripts/milestone_necromancer.js")
            json.addProperty("raw_script", rune.getRawScriptPath());
        } else if (rune.getRawScriptContent() != null && !rune.getRawScriptContent().isBlank()) {
            // Standard-Verlinkung auf den stones-Namespace
            String scriptFileName = resolveScriptFileName(rune, trueId);
            json.addProperty("raw_script", StonesMod.MODID + ":scripts/" + scriptFileName + ".js");
        } else if (rune.getRawBehaviors() != null && !rune.getRawBehaviors().isEmpty()) {
            json.add("behaviors", rune.getRawBehaviors().deepCopy());
        }

        // 5. Template-Hash stempeln
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