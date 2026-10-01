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
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import net.stones.StonesMod;
import net.stones.enchantment.RuneEnchantment;
import net.stones.network.PacketSyncEnchantments;

/**
 * =========================================================================================
 * ARCHITEKTUR-DOKUMENTATION: STONES RUNEN- & DATAPACK-PIPELINE
 * =========================================================================================
 * 
 * 1. SANDBOX & STAGING (stones_editor):
 *    - Der Studio-Editor speichert Arbeitsstände ISOLIERT in 'data/stones_workspace/'.
 *    - 'stones_workspace' ist ein reines SICHERHEITSNETZ. Es ist KEIN aktiver Namespace!
 *    - Selbst wenn Drittanbieter-Mods oder globale Loader den Workspace-Ordner erfassen,
 *      verweigert dieser Listener strikt das Laden daraus, um unfertige Entwürfe abzufangen.
 * 
 * 2. BUILD & EXPORT (Apply-Schritt im Studio):
 *    - Erst beim Klick auf "Apply / Ins Spiel übernehmen" nimmt der ServerDatapackExporter
 *      die Sandbox-Dateien und baut das finale, aktive Datapack mit dem echten Namespace 'stones:'.
 *    - Das exportierte Datapack wird an die oberste Prioritätsstufe des PackRepository gesetzt
 *      (LIFO: Zuletzt geladen = überschreibt die Mod-JAR automatisch im ResourceManager).
 * 
 * 3. RUNTIME & VERARBEITUNG (dieser Listener):
 *    - Verarbeitet AUSSCHLIESSLICH den Namespace 'stones:'. Alles andere wird ignoriert.
 *    - Nutzt 'override_registry_id' (Score 300) zur Verknüpfung mit vorregistrierten Hüllen-Slots
 *      (z. B. 'stones_milestone_01', 'major_fire' usw.), da Forge zur Laufzeit keine neuen
 *      Enchantment-Objekte instanziieren darf.
 *    - Generiert KubeJS-Skripte zentral nach 'kubejs/server_scripts/stones_generated/<id>.js'.
 * 
 * 4. SYNCHRONISATION:
 *    - Baut die Milestone-Registry neu, berechnet Spieler-Attribute und synchronisiert die
 *      aktiven Runen via PacketSyncEnchantments mit allen Clients.
 * =========================================================================================
 */
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

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        ensureHelperScriptDeployed();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsonMap, ResourceManager resourceManager, ProfilerFiller profiler) {
        ensureHelperScriptDeployed();

        // 1. Reset aller Runen: Versetzt alle registrierten Hüllen vorübergehend in den Ruhezustand
        ForgeRegistries.ENCHANTMENTS.getValues().stream()
            .filter(e -> e instanceof RuneEnchantment)
            .map(e -> (RuneEnchantment) e)
            .forEach(rune -> {
                rune.sleep();
                resetRuneFields(rune);
            });

        // 2. Konflikte auflösen & Slot-Zuordnung
        Map<ResourceLocation, Map.Entry<ResourceLocation, JsonObject>> prioritizedMap = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : jsonMap.entrySet()) {
            ResourceLocation fileLoc = entry.getKey();
            JsonElement el = entry.getValue();

            if (el == null || !el.isJsonObject()) continue;
            JsonObject json = el.getAsJsonObject();

            // --- SICHERHEITSFILTER: Nur 'stones:' Namespace zulassen ---
            // 'stones_workspace' und Fremd-Namespaces werden strikt abgewiesen.
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

                // Höhere oder gleiche Priorität überschreibt bisherigen Kandidaten
                if (newPrio >= existingPrio) {
                    prioritizedMap.put(targetRegistryId, Map.entry(fileLoc, json));
                }
            }
        }

        int loadedCount = 0;

        // 3. Gewinner-Dateien in die Engine laden & KubeJS-Skripte erzeugen
        for (Map.Entry<ResourceLocation, Map.Entry<ResourceLocation, JsonObject>> entry : prioritizedMap.entrySet()) {
            ResourceLocation targetRegistryId = entry.getKey();
            ResourceLocation fileLoc = entry.getValue().getKey();
            JsonObject json = entry.getValue().getValue();

            try {
                Enchantment targetEnchantment = ForgeRegistries.ENCHANTMENTS.getValue(targetRegistryId);

                if (targetEnchantment instanceof RuneEnchantment rune) {
                    String logicalId = fileLoc.getPath();

                    // Skriptinhalt über Minecrafts ResourceManager aus 'data/stones/scripts/' auflösen
                    String scriptContent = resolveScriptContent(resourceManager, fileLoc.getNamespace(), json);

                    rune.loadFromJson(logicalId, json, scriptContent);
                    loadedCount++;

                    // Zentrale Skript-Generierung für KubeJS
                    exportJsScriptIfNeeded(logicalId, json, scriptContent);

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

        // 4. Milestone-Registry Rebuild
        try {
            Class<?> milestoneRegistryClass = Class.forName("net.stones.milestone.MilestoneRegistry");
            java.lang.reflect.Method rebuildMethod = milestoneRegistryClass.getDeclaredMethod("rebuild");
            rebuildMethod.invoke(null);
        } catch (Exception ignored) {}

        // 5. S2C Synchronisation an verbundene Clients
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            StonesMod.PACKET_HANDLER.send(
                PacketDistributor.ALL.noArg(),
                PacketSyncEnchantments.build()
            );

            server.getPlayerList().getPlayers().forEach(player -> {
                try {
                    Class<?> runeHelperClass = Class.forName("net.stones.util.RuneHelper");
                    java.lang.reflect.Method refreshMethod = runeHelperClass.getDeclaredMethod("refreshPlayer", net.minecraft.world.entity.player.Player.class);
                    refreshMethod.invoke(null, player);
                } catch (Exception ignored) {}
            });
        }
    }

    /**
     * Liest den verlinkten JavaScript-Code direkt aus dem ResourceManager.
     * Da das exportierte Datapack mit Top-Priorität eingehängt ist, liefert der ResourceManager
     * automatisch die Version aus dem aktiven Datapack vor der internen Mod-JAR.
     */
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

        // Datei regulär über Minecrafts ResourceManager auslesen
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

    /**
     * Prioritätsberechnung innerhalb des 'stones:' Namespaces:
     * - Score 300: Explizite Zuweisung über 'override_registry_id' (z. B. Custom-Rune zielt auf vorkompilierten Slot).
     * - Score 100: Standard-Rune (Mapping erfolgt rein über den Dateinamen).
     */
    private static int getPriorityScore(JsonObject json) {
        if (json.has("override_registry_id")) {
            return 300;
        }
        return 100;
    }

    /**
     * Löst den Registry-Slot auf, den diese JSON-Definition besetzen soll.
     */
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

    /**
     * Schreibt den JavaScript-Code bei Bedarf in das KubeJS-Server-Verzeichnis.
     */
    private static void exportJsScriptIfNeeded(String logicalId, JsonObject json, @Nullable String resolvedScript) {
        try {
            String jsCode = null;

            if (resolvedScript != null && !resolvedScript.isBlank()) {
                jsCode = resolvedScript;
            } else if (json.has("behaviors")) {
                jsCode = net.stones.transpiler.StonesTranspiler.transpile(logicalId, json);
            }

            if (jsCode != null && !jsCode.isBlank()) {
                File scriptDir = FMLPaths.GAMEDIR.get().resolve("kubejs/server_scripts/stones_generated").toFile();
                File scriptFile = new File(scriptDir, logicalId + ".js");

                if (scriptFile.getParentFile() != null) {
                    scriptFile.getParentFile().mkdirs();
                }

                Files.writeString(scriptFile.toPath(), jsCode);
            }
        } catch (Exception e) {
            StonesMod.LOGGER.error("[Stones] Fehler beim Generieren des JS-Skripts für '{}': ", logicalId, e);
        }
    }

    /**
     * Stellt sicher, dass das KubeJS-Hilfsskript im Zielverzeichnis existiert.
     */
    private static void ensureHelperScriptDeployed() {
        try {
            File targetHelper = FMLPaths.GAMEDIR.get().resolve("kubejs/server_scripts/00_stones_helper.js").toFile();
            targetHelper.getParentFile().mkdirs();

            try (var is = EnchantmentReloadListener.class.getResourceAsStream("/kubejs_scripts/00_stones_helper.js")) {
                if (is != null) {
                    Files.copy(is, targetHelper.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    StonesMod.LOGGER.info("[Stones] 00_stones_helper.js bereitgestellt.");
                }
            }
        } catch (Exception e) {
            StonesMod.LOGGER.error("[Stones] Konnte Helper-Skript nicht exportieren: ", e);
        }
    }

    /**
     * Bereinigt alle dynamisch gesetzten Felder einer Rune vor dem Neuladen.
     */
    private static void resetRuneFields(RuneEnchantment rune) {
        try {
            Class<?> clazz = rune.getClass();
            while (clazz != null && clazz != Object.class) {
                for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    if (java.util.Collection.class.isAssignableFrom(field.getType())) {
                        java.util.Collection<?> col = (java.util.Collection<?>) field.get(rune);
                        if (col != null) col.clear();
                    } else if (java.util.Map.class.isAssignableFrom(field.getType())) {
                        java.util.Map<?, ?> map = (java.util.Map<?, ?>) field.get(rune);
                        if (map != null) map.clear();
                    } else if (field.getType() == net.minecraft.world.entity.ai.attributes.Attribute.class ||
                               field.getType() == net.minecraft.world.effect.MobEffect.class ||
                               field.getType() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.class) {
                        field.set(rune, null);
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception ignored) {}
    }
}