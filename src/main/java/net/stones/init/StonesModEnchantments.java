package net.stones.init;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.stones.StonesMod;
import net.stones.effect.StonesCooldownEffect;
import net.stones.enchantment.RuneEnchantment;
import net.stones.enchantment.AmplifyEnchantment; // Import der neuen Klasse
import net.stones.enchantment.behavior.RuneAction;
import net.stones.enchantment.behavior.RuneBehavior;
import net.stones.enchantment.behavior.RuneCondition;
import net.stones.enchantment.RuneStat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.stones.enchantment.behavior.TriggerType;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(modid = StonesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class StonesModEnchantments {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    // Versionsordner NUR für die Config (Nutzer-Sicherheit)
    public static final String CONFIG_VERSION = "v1.1";
    
    // Sauberer Pfad ohne Version für Mod-Interne Ressourcen (Datapacks/Bridge-JAR)
    private static final String INTERNAL_DATA_PATH = "data/stones/enchantments";
    private static Map<String, JsonObject> discoveredRunes = null;
	
    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        // 1. JSONs scannen (nur beim ersten Aufruf eines Registry-Events)
        if (discoveredRunes == null) {
            discoveredRunes = scanModResources();
			// DEAKTIVIERT: Config-Ordner nicht mehr laden, um Server/Client Desyncs zu vermeiden.
            //scanConfigFolder(discoveredRunes);
        }

        // 2. VERZAUBERUNGEN REGISTRIEREN
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.ENCHANTMENTS)) {
            MilestoneActionRegistry.init();
            ConditionRegistry.init();

            // --- NEU: Manuelle Registrierung der Amplify Verzauberung ---
            event.register(ForgeRegistries.Keys.ENCHANTMENTS, 
                new ResourceLocation(StonesMod.MODID, "amplify"), 
                () -> new AmplifyEnchantment());
            
            // Registrierung der dynamischen Runen aus JSONs
            discoveredRunes.forEach((id, json) -> registerRuneFromJson(id, json, event));
        }

        // 3. AUTOMATISCHE COOLDOWN-EFFEKTE REGISTRIEREN
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.MOB_EFFECTS)) {
            discoveredRunes.forEach((id, json) -> {
                ResourceLocation effectId = new ResourceLocation(StonesMod.MODID, "cooldown_" + id);
                
                String iconPath = json.has("icon") ? json.get("icon").getAsString() : "minecraft:textures/item/barrier.png";
                
                event.register(ForgeRegistries.Keys.MOB_EFFECTS, effectId, () -> new StonesCooldownEffect(iconPath, json.has("name") ? json.get("name").getAsString() : "Skill"));
                LOGGER.info("[Stones] Auto-registrierter CD-Effekt: {} (Icon: {})", effectId, iconPath);
            });
        }
    }

    private static Map<String, JsonObject> scanModResources() {
        Map<String, JsonObject> map = new HashMap<>();
        
        for (IModInfo mod : ModList.get().getMods()) {
            String modId = mod.getModId();
            Path path = ModList.get().getModFileById(modId).getFile().findResource(INTERNAL_DATA_PATH);
            
            if (Files.exists(path)) {
                try (Stream<Path> walk = Files.walk(path, 1)) {
                    walk.filter(p -> p.toString().endsWith(".json"))
                        .forEach(file -> {
                            try (InputStream is = Files.newInputStream(file)) {
                                JsonObject json = GSON.fromJson(new InputStreamReader(is), JsonObject.class);
                                String id = file.getFileName().toString().replace(".json", "").toLowerCase();
                                map.put(id, json);
                            } catch (Exception e) {
                                LOGGER.error("Failed to read JAR resource from {}: {}", modId, file, e);
                            }
                        });
                } catch (Exception e) {
                    LOGGER.error("Failed to scan internal resources for mod {}: {}", modId, e.getMessage());
                }
            }
        }
        return map;
    }

/*     private static void scanConfigFolder(Map<String, JsonObject> map) {
        Path configDir = FMLPaths.CONFIGDIR.get()
                .resolve("stones")
                .resolve(CONFIG_VERSION)
                .resolve("enchantments");
                
        File folder = configDir.toFile();
        
        if (!folder.exists()) {
            folder.mkdirs();
        }
        
        LOGGER.info("Loading Runes from: " + folder.getAbsolutePath());

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));

        if (files == null || files.length == 0) {
            LOGGER.info("No runes found, generating defaults using JsonExporter...");
            JsonExporter.main(new String[]{});
            files = folder.listFiles((dir, name) -> name.endsWith(".json"));
        }

        if (files != null) {
            for (File file : files) {
                try (FileReader reader = new FileReader(file)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    String id = file.getName().replace(".json", "").toLowerCase();
                    map.put(id, json); 
                } catch (Exception e) {
                    LOGGER.error("Failed to load rune json: " + file.getName(), e);
                }
            }
        }
    } */

    private static void registerRuneFromJson(String id, JsonObject json, RegisterEvent event) {
        try {
            String typeStr = json.get("type").getAsString().toUpperCase();
            RuneEnchantment.Type type = RuneEnchantment.Type.valueOf(typeStr);
            
            String customName = json.has("name") ? json.get("name").getAsString() : null;
            String customDesc = json.has("description") ? json.get("description").getAsString() : null;
			String iconPath = json.has("icon") ? json.get("icon").getAsString() : null;
            double factor = json.has("factor") ? json.get("factor").getAsDouble() : 0.0;
			float reqLevel = json.has("required_level") ? json.get("required_level").getAsFloat() : 5.0f;

            RuneEnchantment tempEnchantment = null;

            if (json.has("attribute")) {
                String attrStr = json.get("attribute").getAsString();
                Attribute attribute = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(attrStr));
                if (attribute != null) {
                    String opStr = json.get("operation").getAsString().toUpperCase();
                    AttributeModifier.Operation operation = AttributeModifier.Operation.valueOf(opStr);
                    tempEnchantment = new RuneEnchantment(type, attribute, operation, factor, customName, customDesc, iconPath, reqLevel);
                } else {
                    LOGGER.error("Attribute not found: " + attrStr);
                }
            } 
            else if (json.has("effect")) {
                String effStr = json.get("effect").getAsString();
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(effStr));
                if (effect != null) {
                    tempEnchantment = new RuneEnchantment(type, effect, factor, customName, customDesc, iconPath, reqLevel);
                } else {
                    LOGGER.error("Attribute not found: " + effStr);
                }
            }
            else {
                tempEnchantment = new RuneEnchantment(type, (Attribute)null, null, 0.0, customName, customDesc, iconPath, reqLevel);
            }

            if (tempEnchantment != null) {
				if (json.has("stats")) {
					JsonArray statsArray = json.getAsJsonArray("stats");
					for (JsonElement e : statsArray) {
						JsonObject sObj = e.getAsJsonObject();
						tempEnchantment.addStat(new RuneStat(
							sObj.get("id").getAsString(),
							sObj.get("label").getAsString(),
							sObj.has("type") ? sObj.get("type").getAsString() : "generic",
							sObj.get("base").getAsFloat(),
							sObj.has("per_level") ? sObj.get("per_level").getAsFloat() : 0f,
							sObj.has("scaling") ? sObj.get("scaling").getAsString() : "RUNE_LEVEL",
							sObj.has("display_factor") ? sObj.get("display_factor").getAsFloat() : 1.0f,
							sObj.has("suffix") ? sObj.get("suffix").getAsString() : "",
							sObj.has("min") ? sObj.get("min").getAsFloat() : null,
							sObj.has("max") ? sObj.get("max").getAsFloat() : null
						));
					}
				}
				
                if (json.has("behaviors")) {
                    JsonArray behaviors = json.getAsJsonArray("behaviors");
                    for (JsonElement el : behaviors) {
                        JsonObject bObj = el.getAsJsonObject();
                        String trigStr = bObj.get("trigger").getAsString().toUpperCase();
                        TriggerType trigger = TriggerType.get(trigStr);
                        
                        List<RuneCondition> conditionsList = new ArrayList<>();
                        if (bObj.has("conditions")) {
                            JsonElement condElement = bObj.get("conditions");
                            if (condElement.isJsonArray()) {
                                for (JsonElement ce : condElement.getAsJsonArray()) parseAndAddCondition(ce.getAsJsonObject(), conditionsList);
                            } else if (condElement.isJsonObject()) {
                                parseAndAddCondition(condElement.getAsJsonObject(), conditionsList);
                            }
                        }
                        
                        List<RuneBehavior.ConfiguredRuneAction> actionsList = new ArrayList<>();
                        if (bObj.has("actions")) {
                            for (JsonElement actEl : bObj.getAsJsonArray("actions")) {
                                JsonObject actObj = actEl.getAsJsonObject();
                                if (actObj.has("type")) {
                                    RuneAction action = MilestoneActionRegistry.get(actObj.get("type").getAsString());
                                    if (action != null) actionsList.add(new RuneBehavior.ConfiguredRuneAction(action, actObj));
                                }
                            }
                        }
                        tempEnchantment.addBehavior(new RuneBehavior(trigger, conditionsList, actionsList));
                    }
                }

                // NEU: Max-Level aus JSON lesen (falls nicht vorhanden, bleibt es beim Standardwert)
                if (json.has("max_level")) {
                    tempEnchantment.setMaxLevel(json.get("max_level").getAsInt());
                }

                final RuneEnchantment finalEnchantment = tempEnchantment;
                event.register(ForgeRegistries.Keys.ENCHANTMENTS, new ResourceLocation(StonesMod.MODID, id), () -> finalEnchantment);
                LOGGER.info("Registered Rune: stones:{}", id);
            }
        } catch (Exception e) {
            LOGGER.error("Critical error registering rune '" + id + "'", e);
        }
    }

    private static void parseAndAddCondition(JsonObject json, List<RuneCondition> list) {
        if (json.has("type")) {
            String type = json.get("type").getAsString();
            RuneCondition condition = ConditionRegistry.create(type, json);
            if (condition != null) {
                list.add(condition);
            } else {
                LOGGER.warn("Unknown condition type: " + type);
            }
        }
    }
}