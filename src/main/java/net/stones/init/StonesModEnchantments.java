package net.stones.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import net.stones.StonesMod;
import net.stones.enchantment.RuneEnchantment;
import net.stones.enchantment.AmplifyEnchantment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod.EventBusSubscriber(modid = StonesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class StonesModEnchantments {

    private static final Logger LOGGER = LogManager.getLogger();

    // Eure gesammelten Legacy Runes aus alten Spielständen (Bleiben zur Abwärtskompatibilität unverändert)
    private static final String[] LEGACY_RUNES = {
        "major_fire_magic_resistance",
        "minor_ice_magic_power",
        "major_ice_magic_resistance",
        "major_evocation_magic_power",
        "milestone_gravity_well",
        "minor_max_strikes",
        "minor_muffling",
        "minor_vitality",
        "minor_ender_magic_resistance",
        "major_mana_regeneration",
        "milestone_drowned_warrior",
        "minor_ice_magic_resistance",
        "minor_blood_magic_resistance",
        "minor_resilience",
        "milestone_archon",
        "milestone_fireblast",
        "minor_evocation",
        "major_max_strikes",
        "milestone_gamblers_ruin",
        "major_stamina_regen",
        "minor_blood",
        "major_mana",
        "minor_void",
        "minor_eldritch_magic_power",
        "major_evocation",
        "major_celerity",
        "minor_lightning_magic_power",
        "major_wrath",
        "minor_ice",
        "major_luck",
        "major_eldritch_magic_power",
        "minor_blood_magic_power",
        "minor_ender",
        "major_ender",
        "minor_eldritch_magic_resistance",
        "major_weight",
        "minor_ender_magic_power",
        "milestone_arcane_focus",
        "minor_fire",
        "major_holy_magic_resistance",
        "major_evocation_magic_resistance",
        "major_void",
        "major_ender_magic_resistance",
        "minor_weight",
        "minor_mana",
        "major_eldritch",
        "minor_lightning_magic_resistance",
        "minor_lightning",
        "minor_cooldown_reduction",
        "major_lightning",
        "major_nature_magic_power",
        "minor_evocation_magic_power",
        "minor_fire_magic_power",
        "major_swift_casting",
        "major_spell_resistance",
        "minor_mana_regeneration",
        "minor_haste",
        "milestone_night_hunter",
        "major_blood",
        "major_cooldown_reduction",
        "minor_stability",
        "milestone_phoenix",
        "minor_nature_magic_resistance",
        "minor_fire_magic_resistance",
        "major_blood_magic_resistance",
        "minor_armor_negation",
        "major_ice",
        "minor_nature_magic_power",
        "major_nature_magic_resistance",
        "milestone_dash",
        "minor_swiftness",
        "major_holy",
        "minor_spell_resistance",
        "minor_hardness",
        "major_nature",
        "major_camouflage",
        "major_ice_magic_power",
        "major_armor_negation",
        "minor_swift_casting",
        "minor_holy",
        "milestone_master_prospector",
        "major_fire",
        "major_blood_magic_power",
        "milestone_storm_caller",
        "milestone_gladiator",
        "milestone_necromancer",
        "minor_impact",
        "minor_nature",
        "major_lightning_magic_power",
        "major_fire_magic_power",
        "minor_holy_magic_power",
        "minor_holy_magic_resistance",
        "minor_eldritch",
        "minor_fortune",
        "milestone_glacial_thrust",
        "minor_evocation_magic_resistance",
        "minor_camouflage",
        "milestone_berserker",
        "major_ender_magic_power",
        "minor_stamina_regen",
        "major_lightning_magic_resistance",
        "milestone_battle_mage",
        "major_spell_power",
        "minor_spell_power",
        "major_eldritch_magic_resistance",
        "milestone_midas_touch",
        "major_muffling",
        "major_impact",
        "major_holy_magic_power",
        "milestone_aeoncore"
    };

    // Feste Slots für die Hauptmod Stones
    private static final int DYNAMIC_MINOR_SLOTS = 150;
    private static final int DYNAMIC_MAJOR_SLOTS = 100;
    private static final int DYNAMIC_MILESTONE_SLOTS = 50;

    @SubscribeEvent
    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.ENCHANTMENTS)) {
            MilestoneActionRegistry.init();
            ConditionRegistry.init();

            // 1. Amplify registrieren
            event.register(ForgeRegistries.Keys.ENCHANTMENTS, 
                new ResourceLocation(StonesMod.MODID, "amplify"), 
                AmplifyEnchantment::new);

            // 2. Legacy Hüllen registrieren (Vermeidet Weltkorruption)
            for (String legacyId : LEGACY_RUNES) {
                RuneEnchantment.Type initialType = RuneEnchantment.Type.MINOR;
                if (legacyId.startsWith("major_")) {
                    initialType = RuneEnchantment.Type.MAJOR;
                } else if (legacyId.startsWith("milestone_")) {
                    initialType = RuneEnchantment.Type.MILESTONE;
                }
                registerDormantSlot(event, StonesMod.MODID, legacyId, initialType);
            }

            // 3. Echte Wildcard-Slots registrieren (Präfix angepasst auf stones_minor_<int>!)
            registerModSlots(event, StonesMod.MODID, DYNAMIC_MINOR_SLOTS, DYNAMIC_MAJOR_SLOTS, DYNAMIC_MILESTONE_SLOTS);
            
            LOGGER.info("[Stones] Registry Phase 1 abgeschlossen. Hüllen bereit.");
        }
    }

    /**
     * ÖFFENTLICHE API FÜR ADDONS / BRÜCKEN-MODS
     * Erlaubt es Brücken-Mods (wie Epic Fight Bridge) in ihrem eigenen RegisterEvent,
     * eine Reihe an eigenen Slots im selben Schema zu registrieren.
     * * Aufruf in der Bridge-Mod z. B.:
     * StonesModEnchantments.registerModSlots(event, "stonesefbridge", 100, 50, 25);
     */
    public static void registerModSlots(RegisterEvent event, String modId, int minorCount, int majorCount, int milestoneCount) {
        registerDynamicSlotsForMod(event, modId, modId + "_minor_", RuneEnchantment.Type.MINOR, minorCount);
        registerDynamicSlotsForMod(event, modId, modId + "_major_", RuneEnchantment.Type.MAJOR, majorCount);
        registerDynamicSlotsForMod(event, modId, modId + "_milestone_", RuneEnchantment.Type.MILESTONE, milestoneCount);
        LOGGER.info("[Stones API] {} Wildcard-Slots für die Mod '{}' registriert.", 
            (minorCount + majorCount + milestoneCount), modId);
    }

    private static void registerDynamicSlotsForMod(RegisterEvent event, String modId, String prefix, RuneEnchantment.Type type, int count) {
        for (int i = 1; i <= count; i++) {
            // Generiert z. B. stones_minor_01, stonesefbridge_minor_01, ...
            String slotId = prefix + String.format("%02d", i);
            registerDormantSlot(event, modId, slotId, type);
        }
    }

    private static void registerDormantSlot(RegisterEvent event, String namespace, String id, RuneEnchantment.Type initialType) {
        RuneEnchantment dormantShell = new RuneEnchantment(initialType);
        event.register(ForgeRegistries.Keys.ENCHANTMENTS, new ResourceLocation(namespace, id), () -> dormantShell);
    }
}