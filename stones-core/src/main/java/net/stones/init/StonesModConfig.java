package net.stones.init;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;

public class StonesModConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    
    // --- ClientUI Config ---
    public static final ForgeConfigSpec.BooleanValue SHOW_STAT_TOASTS;
    public static final ForgeConfigSpec.BooleanValue SHOW_MILESTONE_TOASTS;
    public static final ForgeConfigSpec.ConfigValue<String> STAT_TOAST_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> MILESTONE_TOAST_COLOR;
    public static final ForgeConfigSpec.IntValue STAT_TOAST_DURATION;
    public static final ForgeConfigSpec.IntValue MILESTONE_TOAST_DURATION;
    
    // --- Pause Menu Button Config ---
    public static final ForgeConfigSpec.BooleanValue SHOW_PAUSE_BUTTON;
    public static final ForgeConfigSpec.IntValue PAUSE_BUTTON_X_OFFSET;
    public static final ForgeConfigSpec.IntValue PAUSE_BUTTON_Y;
    public static final ForgeConfigSpec.IntValue PAUSE_BUTTON_WIDTH;
    public static final ForgeConfigSpec.IntValue PAUSE_BUTTON_HEIGHT;

    // --- Schrein-Konfiguration ---
    public static final ForgeConfigSpec.IntValue GLOBAL_MAX_SHRINE_LEVEL;

    // --- Security ---

    static {
        BUILDER.push("ClientUI");
        SHOW_STAT_TOASTS = BUILDER
                .comment("Show a toast notification when a normal stat increases on level up.")
                .define("showStatToasts", true);
        SHOW_MILESTONE_TOASTS = BUILDER
                .comment("Show a toast notification when a new milestone is activated.")
                .define("showMilestoneToasts", true);
        STAT_TOAST_COLOR = BUILDER
                .comment("Hex color code for the border of stat toasts (ARGB or RGB).")
                .define("statToastColor", "FF00FFFF");
        MILESTONE_TOAST_COLOR = BUILDER
                .comment("Hex color code for the border of milestone toasts (ARGB or RGB).")
                .define("milestoneToastColor", "FFFF55FF");
        STAT_TOAST_DURATION = BUILDER
                .comment("Duration in milliseconds for stat toasts.")
                .defineInRange("statToastDuration", 2500, 500, 10000);
        MILESTONE_TOAST_DURATION = BUILDER
                .comment("Duration in milliseconds for milestone toasts.")
                .defineInRange("milestoneToastDuration", 4000, 500, 10000);

        // Pause-Menü-Knopf voll anpassbar machen
        SHOW_PAUSE_BUTTON = BUILDER
                .comment("Soll der 'Stones Studio' Knopf im Minecraft Pause-Menü angezeigt werden?")
                .define("showPauseButton", true);
        PAUSE_BUTTON_X_OFFSET = BUILDER
                .comment("Horizontaler Versatz des Knopfes relativ zur Bildschirmmitte (Standard: +65 um rechts verschoben zu sein).")
                .defineInRange("pauseButtonXOffset", 65, -1000, 1000);
        PAUSE_BUTTON_Y = BUILDER
                .comment("Die Y-Koordinate des Knopfs im Pause-Menü (Standard: 10, d.h. am oberen Rand).")
                .defineInRange("pauseButtonY", 10, 0, 2000);
        PAUSE_BUTTON_WIDTH = BUILDER
                .comment("Die Breite des Pause-Menü-Knopfs (Standard: 120).")
                .defineInRange("pauseButtonWidth", 120, 10, 1000);
        PAUSE_BUTTON_HEIGHT = BUILDER
                .comment("Die Höhe des Pause-Menü-Knopfs (Standard: 20).")
                .defineInRange("pauseButtonHeight", 20, 5, 200);
        BUILDER.pop();

        // Schrein-Einstellungen
        BUILDER.push("Shrine");
        GLOBAL_MAX_SHRINE_LEVEL = BUILDER
                .comment("Das absolute maximale Level, das ein neu generierter Schrein erreichen kann (z.B. 500).",
                         "Bestimmt die Skalierunggrenze der thaumaturgischen Runen-Verbindungen.")
                .defineInRange("globalMaxShrineLevel", 500, 10, 1000);
        BUILDER.pop();

        // Server-Sicherheitseinstellungen für Reflection
        BUILDER.push("Security");
        BUILDER.pop();
        
        SPEC = BUILDER.build();
    }
}