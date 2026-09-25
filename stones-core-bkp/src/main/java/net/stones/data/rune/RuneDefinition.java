package net.stones.data.rune;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import java.util.Optional;

public record RuneDefinition(
    RuneType type,
    ResourceLocation targetAttribute,
    int minLevelBase,
    RuneScaling scaling,
    RuneDisplay display
) {
    public enum RuneType implements StringRepresentable {
        MINOR, MAJOR, MILESTONE;
        public static final Codec<RuneType> CODEC = StringRepresentable.fromEnum(RuneType::values);
        @Override public String getSerializedName() { return this.name().toLowerCase(); }
    }

    public record RuneScaling(String formula, double factor, boolean clampNegative) {
        public static final Codec<RuneScaling> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("formula", "linear").forGetter(RuneScaling::formula),
            Codec.DOUBLE.fieldOf("factor").forGetter(RuneScaling::factor),
            Codec.BOOL.optionalFieldOf("clamp_negative", true).forGetter(RuneScaling::clampNegative)
        ).apply(instance, RuneScaling::new));
    }

    public record RuneDisplay(String nameKey, ResourceLocation icon) {
        public static final Codec<RuneDisplay> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(RuneDisplay::nameKey),
            ResourceLocation.CODEC.fieldOf("icon").forGetter(RuneDisplay::icon)
        ).apply(instance, RuneDisplay::new));
    }

    public static final Codec<RuneDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        RuneType.CODEC.fieldOf("type").forGetter(RuneDefinition::type),
        ResourceLocation.CODEC.fieldOf("target_attribute").forGetter(RuneDefinition::targetAttribute),
        Codec.INT.fieldOf("min_level_base").forGetter(RuneDefinition::minLevelBase),
        RuneScaling.CODEC.fieldOf("scaling").forGetter(RuneDefinition::scaling),
        RuneDisplay.CODEC.fieldOf("display").forGetter(RuneDefinition::display)
    ).apply(instance, RuneDefinition::new));
}