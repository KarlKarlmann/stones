package net.stones.particle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.registries.ForgeRegistries;
import net.stones.init.StonesModParticles;

import java.util.Locale;

/**
 * Shared-Klasse: Muss für Server und Client erreichbar sein.
 * Transportiert die Lifetime-Daten für das effiziente Rendering.
 */
public record XrayParticleOptions(int lifetime, float size) implements ParticleOptions {
    
    public static final Codec<XrayParticleOptions> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
            Codec.INT.fieldOf("lifetime").forGetter(XrayParticleOptions::lifetime),
            Codec.FLOAT.fieldOf("size").forGetter(XrayParticleOptions::size)
    ).apply(instance, XrayParticleOptions::new));

    @SuppressWarnings("deprecation")
    public static final ParticleOptions.Deserializer<XrayParticleOptions> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override
        public XrayParticleOptions fromCommand(ParticleType<XrayParticleOptions> type, StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            int l = reader.readInt();
            reader.expect(' ');
            float s = reader.readFloat();
            return new XrayParticleOptions(l, s);
        }

        @Override
        public XrayParticleOptions fromNetwork(ParticleType<XrayParticleOptions> type, FriendlyByteBuf buffer) {
            return new XrayParticleOptions(buffer.readInt(), buffer.readFloat());
        }
    };

    @Override
    public ParticleType<XrayParticleOptions> getType() {
        return StonesModParticles.XRAY_SPARK.get();
    }

    @Override
    public void writeToNetwork(FriendlyByteBuf buffer) {
        buffer.writeInt(this.lifetime);
        buffer.writeFloat(this.size);
    }

    @Override
    public String writeToString() {
        return String.format(Locale.ROOT, "%s %d %.2f", ForgeRegistries.PARTICLE_TYPES.getKey(getType()), this.lifetime, this.size);
    }
}