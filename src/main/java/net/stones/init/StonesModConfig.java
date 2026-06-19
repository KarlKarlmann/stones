package net.stones.init;

import net.minecraftforge.common.ForgeConfigSpec;

public class StonesModConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue REWARD_SCORE_THRESHOLD;

    static {
        BUILDER.push("Rewards");

        REWARD_SCORE_THRESHOLD = BUILDER
                .comment("The minimum score required to obtain a Resonance Box upon death (default: 500).")
                .defineInRange("rewardScoreThreshold", 500, 0, Integer.MAX_VALUE);

        BUILDER.pop();
        
        SPEC = BUILDER.build();
    }
}