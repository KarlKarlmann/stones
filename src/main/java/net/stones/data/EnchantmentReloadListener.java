package net.stones.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.registries.ForgeRegistries;
import net.stones.StonesMod;
import net.stones.enchantment.RuneEnchantment;
import net.stones.init.StonesModConfig;
import net.stones.network.PacketSyncEnchantments;

import java.util.Map;
import java.util.HashMap;

/**
 * Scannt das aktive Datapack im isolierten Namespace "stones_workspace".
 * Mappt die gefundenen JSON-Dateien automatisch zurück auf die echten Registrierungen
 * der Hauptmod (Namespace "stones") oder zugehöriger Brücken-Mods.
 */
@Mod.EventBusSubscriber(modid = StonesMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EnchantmentReloadListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();
    private static final String WORKSPACE_NAMESPACE = "stones_workspace";

    public EnchantmentReloadListener() {
        super(GSON, "enchantments");
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new EnchantmentReloadListener());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsonMap, ResourceManager resourceManager, ProfilerFiller profiler) {
        // 1. Alle registrierten Enchantments zuerst in den Schlafmodus versetzen und komplett resetten
        ForgeRegistries.ENCHANTMENTS.getValues().stream()
            .filter(e -> e instanceof RuneEnchantment)
            .map(e -> (RuneEnchantment) e)
            .forEach(rune -> {
                rune.sleep();
                resetRuneFields(rune); // Verhindert das Akkumulieren veralteter Cooldowns/Behaviors!
            });

        // 2. PRIORITÄTS-FILTER (KOLLISIONS-KILLER):
        // Wir weisen allen JSON-Dateien zuerst ihre Ziel-Slot-IDs zu.
        // Wenn ein Slot sowohl von einem Standard-Pack (z.B. der Mod-JAR) als auch von "stones_workspace" geladen werden soll,
        // hat "stones_workspace" IMMER Vorfahrt. Wir sortieren die Standard-Datei aus, um Kollisionen zu verhindern!
        Map<ResourceLocation, ResourceLocation> fileToTargetId = new HashMap<>();
        Map<ResourceLocation, ResourceLocation> targetIdToChosenFile = new HashMap<>();

        for (ResourceLocation fileLoc : jsonMap.keySet()) {
            JsonElement el = jsonMap.get(fileLoc);
            if (el == null || !el.isJsonObject()) continue;
            JsonObject json = el.getAsJsonObject();
            
            // REDIRECT-FILTRATION:
            // "stones_workspace" wird NUR verarbeitet, wenn ein aktives Datapack in der Config steht
            if (fileLoc.getNamespace().equals(WORKSPACE_NAMESPACE)) {
                if (StonesModConfig.ACTIVE_WORKSPACE_PACK.get().isEmpty()) {
                    continue; 
                }
            }

            ResourceLocation targetRegistryId;
            if (json.has("override_registry_id")) {
                targetRegistryId = new ResourceLocation(json.get("override_registry_id").getAsString());
            } else {
                String filename = fileLoc.getPath(); 
                String[] parts = filename.split("_");
                if (parts.length >= 3 && (parts[1].equals("minor") || parts[1].equals("major") || parts[1].equals("milestone"))) {
                    String targetNamespace = parts[0]; 
                    String targetPath = parts[0] + "_" + parts[1] + "_" + parts[2];
                    targetRegistryId = new ResourceLocation(targetNamespace, targetPath);
                } else {
                    targetRegistryId = new ResourceLocation(StonesMod.MODID, filename);
                }
            }
            
            fileToTargetId.put(fileLoc, targetRegistryId);
            
            // Priorisierung: Workspace-Projekt-Dateien überschreiben Standard-Ressourcen bedingungslos!
            if (!targetIdToChosenFile.containsKey(targetRegistryId) || fileLoc.getNamespace().equals(WORKSPACE_NAMESPACE)) {
                targetIdToChosenFile.put(targetRegistryId, fileLoc);
            }
        }

        int loadedCount = 0;

        // 3. Wecke die gefilterten, prioritären Enchantments auf
        for (ResourceLocation fileLocation : targetIdToChosenFile.values()) {
            JsonElement el = jsonMap.get(fileLocation);
            if (el == null || !el.isJsonObject()) continue;
            JsonObject json = el.getAsJsonObject();
            ResourceLocation targetRegistryId = fileToTargetId.get(fileLocation);

            if (targetRegistryId == null) continue;

            try {
                Enchantment targetEnchantment = ForgeRegistries.ENCHANTMENTS.getValue(targetRegistryId);
                
                if (targetEnchantment instanceof RuneEnchantment rune) {
                    // Wir wecken das schlafende Enchantment auf und laden die JSON-Logik
                    String logicalId = fileLocation.getPath();
                    rune.loadFromJson(logicalId, json);
                    loadedCount++;
                    StonesMod.LOGGER.debug("[Stones Studio] Enchantment erwacht: {} (Slot: {})", rune.getFullname(1).getString(), targetRegistryId);
                } else {
                    StonesMod.LOGGER.warn("[Stones Studio] Ziel-Slot '{}' für Datei '{}' existiert nicht in der Registry!", targetRegistryId, fileLocation);
                }

            } catch (Exception e) {
                StonesMod.LOGGER.error("[Stones Studio] Fehler beim Parsen des Enchantments: {}", fileLocation, e);
            }
        }
        
        StonesMod.LOGGER.info("[Stones Studio] Datapack angewendet: {} Enchantments erfolgreich geladen.", loadedCount);

        // 4. MILESTONE-SKILLS NEU AUFBAUEN (Sicherheitsnetz per Reflection)
        try {
            Class<?> milestoneRegistryClass = Class.forName("net.stones.milestone.MilestoneRegistry");
            java.lang.reflect.Method rebuildMethod = milestoneRegistryClass.getDeclaredMethod("rebuild");
            rebuildMethod.invoke(null);
            StonesMod.LOGGER.info("[Stones Studio] MilestoneRegistry erfolgreich neu aufgebaut.");
        } catch (ClassNotFoundException e) {
            try {
                Class<?> milestoneHelperClass = Class.forName("net.stones.util.MilestoneHelper");
                java.lang.reflect.Method reloadMethod = milestoneHelperClass.getDeclaredMethod("reload");
                reloadMethod.invoke(null);
                StonesMod.LOGGER.info("[Stones Studio] MilestoneHelper erfolgreich neu geladen.");
            } catch (Exception ignored) {}
        } catch (Exception e) {
            StonesMod.LOGGER.error("[Stones Studio] Fehler beim Neuaufbau der Milestone-Registry: ", e);
        }

        // 5. S2C Netzwerk-Synchronisation im Dedicated Multiplayer
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            StonesMod.PACKET_HANDLER.send(
                PacketDistributor.ALL.noArg(),
                PacketSyncEnchantments.build()
            );
            StonesMod.LOGGER.info("[Stones] Netzwerk-Synchronisation: Neue Runen-Daten an alle Spieler gesendet.");

            // 6. LIVE-UPDATE FÜR ONLINE-SPIELER
            ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers().forEach(player -> {
                try {
                    Class<?> runeHelperClass = Class.forName("net.stones.util.RuneHelper");
                    java.lang.reflect.Method refreshMethod = runeHelperClass.getDeclaredMethod("refreshPlayer", net.minecraft.world.entity.player.Player.class);
                    refreshMethod.invoke(null, player);
                } catch (Exception ignored) {
                    try {
                        Class<?> milestoneHelperClass = Class.forName("net.stones.util.MilestoneHelper");
                        java.lang.reflect.Method updateMethod = milestoneHelperClass.getDeclaredMethod("updatePlayerSkills", net.minecraft.world.entity.player.Player.class);
                        updateMethod.invoke(null, player);
                    } catch (Exception ignored2) {}
                }
            });
            StonesMod.LOGGER.info("[Stones] Live-Refresh der Meilenstein-Attribute für alle Spieler durchgeführt.");
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
                        if (col != null) {
                            col.clear();
                        }
                    } 
                    else if (java.util.Map.class.isAssignableFrom(field.getType())) {
                        java.util.Map<?, ?> map = (java.util.Map<?, ?>) field.get(rune);
                        if (map != null) {
                            map.clear();
                        }
                    }
                    else if (field.getType() == net.minecraft.world.entity.ai.attributes.Attribute.class) {
                        field.set(rune, null);
                    }
                    else if (field.getType() == net.minecraft.world.effect.MobEffect.class) {
                        field.set(rune, null);
                    }
                    else if (field.getType() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.class) {
                        field.set(rune, null);
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception e) {
            StonesMod.LOGGER.error("[Stones Studio] Fehler beim Zurücksetzen der Rune-Klassenfelder: ", e);
        }
    }
}