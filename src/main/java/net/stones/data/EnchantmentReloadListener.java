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
import net.stones.network.PacketSyncEnchantments;

import java.util.Map;

/**
 * Scannt den zentralen Ordner "data/stones/enchantments/" über alle Mods und Datapacks hinweg.
 * Befüllt die registrierten Hüllen zur Laufzeit und synchronisiert sie an alle Spieler.
 */
@Mod.EventBusSubscriber(modid = StonesMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
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
        // 1. Zuerst versetzen wir ALLE Runen zurück in den Schlaf (Reset)
        ForgeRegistries.ENCHANTMENTS.getValues().stream()
            .filter(e -> e instanceof RuneEnchantment)
            .map(e -> (RuneEnchantment) e)
            .forEach(RuneEnchantment::sleep);

        int loadedCount = 0;

        // 2. Jetzt wecken wir sie basierend auf den JSONs auf
        for (Map.Entry<ResourceLocation, JsonElement> entry : jsonMap.entrySet()) {
            ResourceLocation fileLocation = entry.getKey();
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                ResourceLocation targetRegistryId;

                // A: Legacy Override
                if (json.has("override_registry_id")) {
                    targetRegistryId = new ResourceLocation(json.get("override_registry_id").getAsString());
                } 
                // B: Modulares Routing (stones_minor_01_blah.json -> stones:stones_minor_01)
                // Für Expansion-Mods: stonesefbridge_minor_01_blitz.json -> stonesefbridge:stonesefbridge_minor_01
                else {
                    String filename = fileLocation.getPath(); 
                    String[] parts = filename.split("_");
                    
                    if (parts.length >= 3 && (parts[1].equals("minor") || parts[1].equals("major") || parts[1].equals("milestone"))) {
                        // Routing Schema: Namespace (Teil 1) + Namespace_Typ_Nummer (Teil 1 + 2 + 3)
                        String targetNamespace = parts[0]; 
                        String targetPath = parts[0] + "_" + parts[1] + "_" + parts[2];
                        
                        targetRegistryId = new ResourceLocation(targetNamespace, targetPath);
                    } else {
                        // Fallback für alte Legacy-Dateien, deren Name direkt dem Pfad entspricht
                        targetRegistryId = new ResourceLocation(StonesMod.MODID, filename);
                    }
                }

                Enchantment targetEnchantment = ForgeRegistries.ENCHANTMENTS.getValue(targetRegistryId);
                
                if (targetEnchantment instanceof RuneEnchantment rune) {
                    if (rune.isAwake()) {
                        StonesMod.LOGGER.error("SLOT-KOLLISION: Slot '{}' wurde bereits von einer anderen Rune belegt! Datei '{}' wird übersprungen.", targetRegistryId, fileLocation);
                        continue;
                    }
                    
                    String logicalId = fileLocation.getPath();
                    rune.loadFromJson(logicalId, json);
                    loadedCount++;
                    StonesMod.LOGGER.debug("Rune erwacht: {} (Slot: {})", rune.getFullname(1).getString(), targetRegistryId);
                } else {
                    StonesMod.LOGGER.warn("Ziel-Slot '{}' aus Datei '{}' existiert nicht in der Registry!", targetRegistryId, fileLocation);
                }

            } catch (Exception e) {
                StonesMod.LOGGER.error("Fehler beim Parsen der Rune JSON: {}", fileLocation, e);
            }
        }
        
        StonesMod.LOGGER.info("[Stones] Datapacks geladen: {} Runen erfolgreich aufgeweckt.", loadedCount);

        // 3. Daten-Synchronisation im Multiplayer (S2C)
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            StonesMod.PACKET_HANDLER.send(
                PacketDistributor.ALL.noArg(),
                PacketSyncEnchantments.build()
            );
            StonesMod.LOGGER.info("[Stones] Netzwerk-Synchronisation: Neue Runen-Daten an alle Spieler gesendet.");
        }
    }
}