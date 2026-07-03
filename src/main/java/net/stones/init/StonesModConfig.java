package net.stones.init;

import net.minecraftforge.common.ForgeConfigSpec;
import java.util.List;

public class StonesModConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue SHOW_STAT_TOASTS;
    public static final ForgeConfigSpec.BooleanValue SHOW_MILESTONE_TOASTS;
    public static final ForgeConfigSpec.ConfigValue<String> STAT_TOAST_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> MILESTONE_TOAST_COLOR;
    public static final ForgeConfigSpec.IntValue STAT_TOAST_DURATION;
    public static final ForgeConfigSpec.IntValue MILESTONE_TOAST_DURATION;
	
    public static final ForgeConfigSpec.IntValue REWARD_SCORE_THRESHOLD;
    
    // --- Echo Trader Config ---
    public static final ForgeConfigSpec.IntValue TRADER_SPAWN_INTERVAL;
    public static final ForgeConfigSpec.IntValue TRADER_SPAWN_CHANCE;
    public static final ForgeConfigSpec.IntValue TRADER_BOX_COUNT;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> TRADER_EXTRA_ITEMS;

    // --- Echo Trader Costs ---
    public static final ForgeConfigSpec.IntValue TRADER_COST_BOX_BASE;
    public static final ForgeConfigSpec.IntValue TRADER_COST_BOX_PER_TIER;
    public static final ForgeConfigSpec.IntValue TRADER_COST_RUNE_MINOR;
    public static final ForgeConfigSpec.IntValue TRADER_COST_RUNE_MAJOR;
    public static final ForgeConfigSpec.IntValue TRADER_COST_RUNE_MILESTONE;
    public static final ForgeConfigSpec.IntValue TRADER_COST_RUNE_AMP_DIVISOR;
    public static final ForgeConfigSpec.IntValue TRADER_COST_CLUSTER_MINOR;
    public static final ForgeConfigSpec.IntValue TRADER_COST_CLUSTER_MAJOR;
    public static final ForgeConfigSpec.IntValue TRADER_COST_CLUSTER_MILESTONE;
    public static final ForgeConfigSpec.IntValue TRADER_COST_RESOURCE_BASE;
    public static final ForgeConfigSpec.IntValue TRADER_COST_RESOURCE_DIVISOR;

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
        BUILDER.pop();
		
        BUILDER.push("Rewards");

        REWARD_SCORE_THRESHOLD = BUILDER
                .comment("The minimum score required to obtain a Resonance Box upon death (default: 500).")
                .defineInRange("rewardScoreThreshold", 500, 0, Integer.MAX_VALUE);

        BUILDER.pop();
        
        BUILDER.push("EchoTrader");
        
        TRADER_SPAWN_INTERVAL = BUILDER
                .comment("Interval in ticks to check for a spawn (24000 = 1 in-game day).")
                .defineInRange("spawnInterval", 24000, 100, Integer.MAX_VALUE);
                
        TRADER_SPAWN_CHANCE = BUILDER
                .comment("Chance in percent (0-100) to spawn the trader every interval.")
                .defineInRange("spawnChance", 15, 0, 100);
        
        TRADER_BOX_COUNT = BUILDER
                .comment("Amount of Resonance Boxes the Echo Trader offers.")
                .defineInRange("boxCount", 4, 0, 13);
                
        TRADER_EXTRA_ITEMS = BUILDER
                .comment("Extra resources the Echo Trader sells.",
                         "Format: 'modid:item_name;minAmount;maxAmount;levelCost'")
                .defineListAllowEmpty(List.of("traderItems"), () -> List.of(
                        "minecraft:lapis_lazuli;16;48;8",
                        "minecraft:diamond;1;3;18",
                        "minecraft:anvil;1;1;15",
                        "minecraft:enchanting_table;1;1;30"
                ), obj -> obj instanceof String && ((String)obj).split(";").length == 4);
                
        BUILDER.push("Costs");
        TRADER_COST_BOX_BASE = BUILDER.comment("Base cost for Resonance Boxes in levels.").defineInRange("costBoxBase", 5, 0, 100);
        TRADER_COST_BOX_PER_TIER = BUILDER.comment("Additional level cost per box tier.").defineInRange("costBoxPerTier", 4, 0, 100);
        TRADER_COST_RUNE_MINOR = BUILDER.comment("Base level cost for Minor Runes.").defineInRange("costRuneMinor", 10, 0, 100);
        TRADER_COST_RUNE_MAJOR = BUILDER.comment("Base level cost for Major Runes.").defineInRange("costRuneMajor", 25, 0, 100);
        TRADER_COST_RUNE_MILESTONE = BUILDER.comment("Base level cost for Milestone Runes.").defineInRange("costRuneMilestone", 40, 0, 100);
        TRADER_COST_RUNE_AMP_DIVISOR = BUILDER.comment("Divisor for Amplify level to extra cost (e.g. 10 means 1 extra level per 10 Amp).").defineInRange("costRuneAmpDivisor", 10, 1, 1000);
        TRADER_COST_CLUSTER_MINOR = BUILDER.comment("Level cost for Minor Cluster Jewels.").defineInRange("costClusterMinor", 50, 0, 100);
        TRADER_COST_CLUSTER_MAJOR = BUILDER.comment("Level cost for Major Cluster Jewels.").defineInRange("costClusterMajor", 70, 0, 100);
        TRADER_COST_CLUSTER_MILESTONE = BUILDER.comment("Level cost for Milestone Cluster Jewels.").defineInRange("costClusterMilestone", 100, 0, 100);
        TRADER_COST_RESOURCE_BASE = BUILDER.comment("Minimum level cost for extra resources without explicit pricing.").defineInRange("costResourceBase", 2, 0, 100);
        TRADER_COST_RESOURCE_DIVISOR = BUILDER.comment("Divisor for resource stack size to cost without explicit pricing (e.g. 8 means +1 level per 8 items).").defineInRange("costResourceDivisor", 8, 1, 1000);
        BUILDER.pop();

        BUILDER.pop();
        
        SPEC = BUILDER.build();
    }
}