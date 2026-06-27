package net.stones.features;

import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.stones.StonesMod;
import net.stones.client.cache.ClientShrineCache;
import net.stones.client.integration.ActionbarCompat;
import net.stones.enchantment.RuneEnchantment;
import net.stones.enchantment.behavior.TriggerType;
import net.stones.enchantment.behavior.RuneBehavior;
import net.stones.logic.RuneCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BACKEND FÜR DIE ACTIONBAR MOD (CLIENT SIDE) - CRASH SICHER
 * Steuert die Übertragung von Runen-Fähigkeiten und Cooldowns an die Actionbar-Mod.
 * Hochgradig optimiert mit Thread-sicheren Caches zur Vermeidung von GC-Garbage und Micro-Stutters.
 */
@Mod.EventBusSubscriber(modid = StonesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ActionSystem {

    // --- SHADOW TARGETS FÜR MIXINS ---
    private static final List<ResourceLocation> CALCULATED_ACTIONS_CACHE = new ArrayList<>();
    private static final Map<ResourceLocation, Integer> ACTION_LEVEL_CACHE = new HashMap<>();
    private static final String[] CLIENT_SLOTS = new String[]{"", "", "", ""};
    
    // Hält die clientseitigen Cooldown-Ablaufzeitpunkte (GameTime in Ticks)
    public static final Map<String, Long> CLIENT_COOLDOWNS = new ConcurrentHashMap<>();
    
    // Blitzschnelle Caches zur Vermeidung von doppelten Registry-Abfragen und String-Allokationen pro Frame
    private static final Map<String, RuneEnchantment> RUNE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, String> COOLDOWN_NAME_CACHE = new ConcurrentHashMap<>();
    
    public static boolean isSyncingWithActionbar = false;

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // SICHERHEITSCHECK: Nur wenn die Actionbar installiert ist, laden wir die Brücke!
            if (ModList.get().isLoaded("actionbar")) {
                ActionbarCompat.register();
            }
        });
    }

    public static void refreshCalculatedActions() {
        CALCULATED_ACTIONS_CACHE.clear();
        ACTION_LEVEL_CACHE.clear();
        
        // Caches leeren, wenn sich die Runen-Zusammenstellung ändert
        RUNE_CACHE.clear();
        COOLDOWN_NAME_CACHE.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            RuneCalculator.collectActiveRunes(ClientShrineCache.INVENTORY, ClientShrineCache.LAYOUT, mc.player.experienceLevel, 
                (rune, runeLevel, socketLevel, mult, mainSlot, subSlot) -> {
                    if (rune.getBehaviors().stream().anyMatch(b -> b.trigger == TriggerType.ON_ACTION_BUTTON)) {
                        ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(rune);
                        if (id != null) {
                            if (!CALCULATED_ACTIONS_CACHE.contains(id)) CALCULATED_ACTIONS_CACHE.add(id);
                            ACTION_LEVEL_CACHE.put(id, Math.max(ACTION_LEVEL_CACHE.getOrDefault(id, 0), runeLevel));
                        }
                    }
                }
            );
        }

        // --- MIXIN ANKER (Ganz wichtig für die Bridge Mod) ---
        for (int i = 1; i <= 3; i++) {
            String currentId = CLIENT_SLOTS[i]; 
            if (currentId != null) currentId.trim(); // Dummy read
        }

        // Actionbar aktualisieren, FALLS sie installiert ist
        if (!isSyncingWithActionbar && ModList.get().isLoaded("actionbar")) {
            isSyncingWithActionbar = true;
            ActionbarCompat.refresh();
            isSyncingWithActionbar = false;
        }
    }

    public static ResourceLocation getActionIcon(String idStr) {
        RuneEnchantment rune = getRuneById(idStr);
        if (rune != null && rune.getIconPath() != null) return new ResourceLocation(rune.getIconPath());
        return null; 
    }

    /**
     * Ermittelt den Cooldown-Namen einer Rune aus ihren Behaviors.
     * Mappt die Registry-ID (z. B. "stones:pyromancer") auf den logischen Cooldown-Schlüssel (z. B. "pyro_shot").
     * Das Ergebnis wird zur Vermeidung von Schleifendurchläufen und JSON-Parsings dauerhaft gecacht.
     */
    public static String getCooldownNameForRune(RuneEnchantment rune) {
        if (rune == null) return null;
        
        ResourceLocation loc = ForgeRegistries.ENCHANTMENTS.getKey(rune);
        if (loc == null) return null;
        
        String registryId = loc.toString();
        
        // Cache-Abfrage: Wurde dieser Cooldown-Name bereits einmal ermittelt?
        return COOLDOWN_NAME_CACHE.computeIfAbsent(registryId, key -> {
            for (RuneBehavior behavior : rune.getBehaviors()) {
                if (behavior.trigger == TriggerType.ON_ACTION_BUTTON) {
                    for (RuneBehavior.ConfiguredRuneAction configAction : behavior.actions) {
                        if (configAction.action != null && configAction.action.getId().equals("stones:cooldown")) {
                            if (configAction.params != null && configAction.params.has("name")) {
                                return configAction.params.get("name").getAsString().trim().toLowerCase();
                            }
                        }
                    }
                }
            }
            return loc.getPath().trim().toLowerCase();
        });
    }

    /**
     * Gibt den verbleibenden Cooldown in Sekunden für die Actionbar-Mod zurück.
     * Mappt davor die übergebene Action-ID über hocheffiziente Caches auf den aktiven Timestamp-Cooldown.
     */
    public static int getActionCooldown(String id) {
        if (id == null || id.isEmpty()) return 0;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        
        // Nur verarbeiten, wenn die ID zu unserer Mod gehört (verhindert String-Operationen bei Fremd-IDs)
        if (id.startsWith(StonesMod.MODID + ":")) {
            RuneEnchantment rune = getRuneById(id);
            if (rune == null) return 0;
            
            // Logischen Namen aus dem schnellen Cache holen
            String cooldownName = getCooldownNameForRune(rune);
            if (cooldownName == null) return 0;
            
            Long endTick = CLIENT_COOLDOWNS.get(cooldownName);
            if (endTick == null) return 0;
            
            long remainingTicks = endTick - mc.level.getGameTime();
            if (remainingTicks <= 0) {
                CLIENT_COOLDOWNS.remove(cooldownName); // Aufräumen abgelaufener Cooldowns
                return 0;
            }
            
            // Konvertierung in Sekunden (+1 für weiches Abrunden im HUD)
            return (int) (remainingTicks / 20) + 1;
        }
        return 0; 
    }

    public static List<Either<FormattedText, TooltipComponent>> getActionTooltip(String id) {
        List<Either<FormattedText, TooltipComponent>> tooltip = new ArrayList<>();
        RuneEnchantment rune = getRuneById(id);
        if (rune != null) {
            int level = ACTION_LEVEL_CACHE.getOrDefault(new ResourceLocation(id), 1);
            tooltip.add(Either.left((FormattedText) Component.empty().append(rune.getFullname(level)).withStyle(ChatFormatting.GOLD)));
            Component desc = rune.getCustomDescription(level);
            if (desc != null && !desc.getString().isEmpty()) tooltip.add(Either.left((FormattedText) Component.empty().append(desc).withStyle(ChatFormatting.GRAY)));
            return tooltip;
        }
        return null; 
    }

    /**
     * Holt die Rune aus der Registry.
     * Nutzt einen schnellen Cache, um doppelte ResourceLocation-Parsings und Registry-Lookups pro Frame zu eliminieren.
     */
    public static RuneEnchantment getRuneById(String id) {
        if (id == null || id.isEmpty()) return null;
        
        return RUNE_CACHE.computeIfAbsent(id, key -> {
            ResourceLocation loc = ResourceLocation.tryParse(key);
            if (loc == null) return null;
            var e = ForgeRegistries.ENCHANTMENTS.getValue(loc);
            return (e instanceof RuneEnchantment r) ? r : null;
        });
    }

    public static List<ResourceLocation> getCalculatedActionsCache() { return CALCULATED_ACTIONS_CACHE; }
    public static Map<ResourceLocation, Integer> getActionLevelCache() { return ACTION_LEVEL_CACHE; }
}