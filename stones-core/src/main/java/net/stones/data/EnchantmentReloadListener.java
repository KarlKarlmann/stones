package net.stones.data;

import java.io.BufferedReader;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import net.stones.StonesMod;
import net.stones.enchantment.RuneEnchantment;
import net.stones.network.PacketSyncEnchantments;
import net.stones.transpiler.StonesScriptBridge;
import net.stones.util.RuneCalculator;

@Mod.EventBusSubscriber(modid = StonesMod.MODID)
public class EnchantmentReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    public EnchantmentReloadListener() {
        super(GSON, "enchantments");
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new EnchantmentReloadListener());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsonMap, ResourceManager resourceManager, ProfilerFiller profiler) {
        // 1. Reset aller Runen & Säuberung des RAM-Skript-Caches vor dem Neuladen
        StonesScriptBridge.clearRegisteredTriggers();
        StonesScriptBridge.clearCompiledScripts();

        ForgeRegistries.ENCHANTMENTS.getValues().stream()
            .filter(e -> e instanceof RuneEnchantment)
            .map(e -> (RuneEnchantment) e)
            .forEach(RuneEnchantment::sleep);

        // 2. Konflikte auflösen & Slot-Zuordnung
        Map<ResourceLocation, Map.Entry<ResourceLocation, JsonObject>> prioritizedMap = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : jsonMap.entrySet()) {
            ResourceLocation fileLoc = entry.getKey();
            JsonElement el = entry.getValue();

            if (el == null || !el.isJsonObject()) continue;
            JsonObject json = el.getAsJsonObject();

            if (!fileLoc.getNamespace().equalsIgnoreCase(StonesMod.MODID)) {
                if ("stones_workspace".equalsIgnoreCase(fileLoc.getNamespace())) {
                    StonesMod.LOGGER.warn("[Stones] Ignoriere unkompiliertes Staging-File '{}'. Der Workspace muss erst über das Studio exportiert werden!", fileLoc);
                }
                continue;
            }

            ResourceLocation targetRegistryId = resolveTargetRegistryId(fileLoc, json);
            if (targetRegistryId == null) continue;

            if (!prioritizedMap.containsKey(targetRegistryId)) {
                prioritizedMap.put(targetRegistryId, Map.entry(fileLoc, json));
            } else {
                Map.Entry<ResourceLocation, JsonObject> existing = prioritizedMap.get(targetRegistryId);
                int existingPrio = getPriorityScore(existing.getValue());
                int newPrio = getPriorityScore(json);

                if (newPrio >= existingPrio) {
                    prioritizedMap.put(targetRegistryId, Map.entry(fileLoc, json));
                }
            }
        }

        int loadedCount = 0;

        // 3. Gewinner-Dateien in die Engine laden & Skripte direkt im RAM transpilieren
        for (Map.Entry<ResourceLocation, Map.Entry<ResourceLocation, JsonObject>> entry : prioritizedMap.entrySet()) {
            ResourceLocation targetRegistryId = entry.getKey();
            ResourceLocation fileLoc = entry.getValue().getKey();
            JsonObject json = entry.getValue().getValue();

            try {
                Enchantment targetEnchantment = ForgeRegistries.ENCHANTMENTS.getValue(targetRegistryId);

                if (targetEnchantment instanceof RuneEnchantment rune) {
                    String logicalId = fileLoc.getPath();

                    String scriptContent = resolveScriptContent(resourceManager, fileLoc.getNamespace(), json);

                    rune.loadFromJson(logicalId, json, scriptContent);
                    loadedCount++;

                    // Keine Festplatten-Dateien mehr! Transpiliert direkt in den RAM der JVM
                    registerJsScriptToRam(logicalId, json, scriptContent);

                    StonesMod.LOGGER.info("[Stones] Rune aktiviert: {} -> Slot: {} (Quelle: {})",
                        rune.getFullname(1).getString(), targetRegistryId, fileLoc);
                } else {
                    StonesMod.LOGGER.warn("[Stones] Ziel-Slot '{}' für Datei '{}' existiert nicht in der Registry!", targetRegistryId, fileLoc);
                }

            } catch (Exception e) {
                StonesMod.LOGGER.error("[Stones] Fehler beim Parsen der Datei: {}", fileLoc, e);
            }
        }

        StonesMod.LOGGER.info("[Stones] Datapacks geladen: {} Runen erfolgreich aktiviert.", loadedCount);

        // 4. S2C Synchronisation & Spieler-Attribute aktualisieren
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            StonesMod.PACKET_HANDLER.send(
                PacketDistributor.ALL.noArg(),
                PacketSyncEnchantments.build()
            );

            server.getPlayerList().getPlayers().forEach(RuneCalculator::updatePlayer);
        }
    }

    private static String resolveScriptContent(ResourceManager resourceManager, String defaultNamespace, JsonObject json) {
        if (!json.has("raw_script")) return null;

        String raw = json.get("raw_script").getAsString().trim();
        if (raw.isEmpty()) return null;

        ResourceLocation scriptLoc;
        if (raw.contains(":")) {
            ResourceLocation parsed = ResourceLocation.tryParse(raw);
            if (parsed == null) {
                StonesMod.LOGGER.error("[Stones] Ungültige Skript-ResourceLocation: '{}'", raw);
                return null;
            }

            String path = parsed.getPath();
            if (!path.endsWith(".js")) path += ".js";
            if (!path.contains("/")) path = "scripts/" + path;

            scriptLoc = new ResourceLocation(parsed.getNamespace(), path);
        } else {
            String path = raw;
            if (!path.endsWith(".js")) path += ".js";
            if (!path.contains("/")) path = "scripts/" + path;

            scriptLoc = new ResourceLocation(defaultNamespace, path);
        }

        Optional<Resource> res = resourceManager.getResource(scriptLoc);
        if (res.isPresent()) {
            try (BufferedReader reader = res.get().openAsReader()) {
                return reader.lines().collect(Collectors.joining("\n"));
            } catch (Exception e) {
                StonesMod.LOGGER.error("[Stones] Konnte Skript-Datei '{}' nicht lesen: ", scriptLoc, e);
            }
        } else {
            StonesMod.LOGGER.warn("[Stones] Skript-Datei nicht gefunden unter data/{}/ (Resource: {})",
                scriptLoc.getNamespace(), scriptLoc);
        }

        return null;
    }

    private static int getPriorityScore(JsonObject json) {
        if (json.has("override_registry_id")) {
            return 300;
        }
        return 100;
    }

    private static ResourceLocation resolveTargetRegistryId(ResourceLocation fileLoc, JsonObject json) {
        if (json.has("override_registry_id")) {
            return new ResourceLocation(json.get("override_registry_id").getAsString());
        }

        String filename = fileLoc.getPath();
        ResourceLocation directLoc = new ResourceLocation(StonesMod.MODID, filename);
        if (ForgeRegistries.ENCHANTMENTS.containsKey(directLoc)) return directLoc;

        ResourceLocation prefixedLoc = new ResourceLocation(StonesMod.MODID, "stones_" + filename);
        if (ForgeRegistries.ENCHANTMENTS.containsKey(prefixedLoc)) return prefixedLoc;

        String[] parts = filename.split("_");
        if (parts.length >= 3 && (parts[1].equals("minor") || parts[1].equals("major") || parts[1].equals("milestone"))) {
            return new ResourceLocation(parts[0], parts[0] + "_" + parts[1] + "_" + parts[2]);
        }
        return directLoc;
    }

    // Speichert den generierten JavaScript-Code direkt im RAM der StonesScriptBridge.
    // Verhindert verdächtige Dropper-Heuristiken (Files.writeString) und beseitigt die KubeJS-I/O-Verzögerung.
    private static void registerJsScriptToRam(String logicalId, JsonObject json, @Nullable String resolvedScript) {
        try {
            String jsCode = null;

            if (resolvedScript != null && !resolvedScript.isBlank()) {
                jsCode = resolvedScript;
            } else if (json.has("behaviors")) {
                jsCode = net.stones.transpiler.StonesTranspiler.transpile(logicalId, json);
            }

            if (jsCode != null && !jsCode.isBlank()) {
                StonesScriptBridge.registerCompiledScript(logicalId, jsCode);
            }
        } catch (Exception e) {
            StonesMod.LOGGER.error("[Stones] Fehler beim Transpilieren des Skripts für '{}': ", logicalId, e);
        }
    }
}