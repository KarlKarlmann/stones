package net.stones.data;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import net.stones.StonesMod;
import net.stones.enchantment.RuneEnchantment;
import net.stones.network.PacketSyncEnchantments;

import java.util.HashMap;
import java.util.Map;

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

        // 1. Reset aller Runen
        ForgeRegistries.ENCHANTMENTS.getValues().stream()
            .filter(e -> e instanceof RuneEnchantment)
            .map(e -> (RuneEnchantment) e)
            .forEach(rune -> {
                rune.sleep();
                resetRuneFields(rune);
            });

        // 2. Konflikte auflösen: Ziel-Slot zuordnen & nach Namespace-Priorität filtern
        Map<ResourceLocation, Map.Entry<ResourceLocation, JsonObject>> prioritizedMap = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : jsonMap.entrySet()) {
            ResourceLocation fileLoc = entry.getKey();
            JsonElement el = entry.getValue();

            if (el == null || !el.isJsonObject()) continue;
            JsonObject json = el.getAsJsonObject();

            ResourceLocation targetRegistryId = resolveTargetRegistryId(fileLoc, json);
            if (targetRegistryId == null) continue;

            if (!prioritizedMap.containsKey(targetRegistryId)) {
                prioritizedMap.put(targetRegistryId, Map.entry(fileLoc, json));
            } else {
                // Vergleiche Priorität der bestehenden vs. der neuen Datei
                Map.Entry<ResourceLocation, JsonObject> existing = prioritizedMap.get(targetRegistryId);
                int existingPrio = getPriorityScore(existing.getKey(), existing.getValue());
                int newPrio = getPriorityScore(fileLoc, json);

                // Datei mit höherer oder gleicher Priorität überschreibt den Platzhalter
                if (newPrio >= existingPrio) {
                    prioritizedMap.put(targetRegistryId, Map.entry(fileLoc, json));
                }
            }
        }

        int loadedCount = 0;

        // 3. Nur die gewinnenden Dateien verarbeiten & JS generieren
        for (Map.Entry<ResourceLocation, Map.Entry<ResourceLocation, JsonObject>> entry : prioritizedMap.entrySet()) {
            ResourceLocation targetRegistryId = entry.getKey();
            ResourceLocation fileLoc = entry.getValue().getKey();
            JsonObject json = entry.getValue().getValue();

            try {
                Enchantment targetEnchantment = ForgeRegistries.ENCHANTMENTS.getValue(targetRegistryId);

                if (targetEnchantment instanceof RuneEnchantment rune) {
                    String logicalId = fileLoc.getPath(); 
                    
                    rune.loadFromJson(logicalId, json);
                    loadedCount++;

                    exportJsScriptIfNeeded(logicalId, json);

                    StonesMod.LOGGER.info("[Stones] Rune geladen: {} -> Slot: {} (Quelle: {})", 
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

        // 5. S2C Sync
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
     * Rein interne Prioritäts-Berechnung anhand von Vanilla ResourceLocations.
     */
    private static int getPriorityScore(ResourceLocation fileLoc, JsonObject json) {
        // Explizites Override im JSON hat höchste Priorität
        if (json.has("override_registry_id")) {
            return 300;
        }

        // Jedes Datapack/Workspace (Namespace != "stones") hat Vorrang vor JAR-Defaults
        if (!fileLoc.getNamespace().equalsIgnoreCase(StonesMod.MODID)) {
            return 200;
        }

        // Standard-Dateien aus der eigenen Mod-JAR ("stones") -> Niedrigste Priorität
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

    private static void exportJsScriptIfNeeded(String logicalId, JsonObject json) {
        try {
            String jsCode = null;

            if (json.has("raw_script")) {
                jsCode = json.get("raw_script").getAsString();
            } else if (json.has("behaviors")) {
                jsCode = net.stones.transpiler.StonesTranspiler.transpile(logicalId, json);
            }

            if (jsCode != null && !jsCode.isBlank()) {
                File scriptDir = FMLPaths.GAMEDIR.get().resolve("kubejs/server_scripts/stones_generated").toFile();
                if (!scriptDir.exists()) scriptDir.mkdirs();

                File scriptFile = new File(scriptDir, logicalId + ".js");
                Files.writeString(scriptFile.toPath(), jsCode);
            }
        } catch (Exception e) {
            StonesMod.LOGGER.error("[Stones] Fehler beim Generieren des JS-Skripts für '{}': ", logicalId, e);
        }
    }

    private static void ensureHelperScriptDeployed() {
        try {
            File targetHelper = FMLPaths.GAMEDIR.get().resolve("kubejs/server_scripts/00_stones_helper.js").toFile();
            targetHelper.getParentFile().mkdirs();

            try (var is = EnchantmentReloadListener.class.getResourceAsStream("/kubejs_scripts/00_stones_helper.js")) {
                if (is != null) {
                    Files.copy(is, targetHelper.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    StonesMod.LOGGER.info("[Stones] 00_stones_helper.js automatisch bereitgestellt.");
                }
            }
        } catch (Exception e) {
            StonesMod.LOGGER.error("[Stones] Konnte Helper-Skript nicht exportieren: ", e);
        }
    }

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