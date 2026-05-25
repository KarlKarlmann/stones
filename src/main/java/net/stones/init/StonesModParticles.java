package net.stones.init;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.stones.StonesMod;
import net.stones.particle.XrayParticleOptions;

public class StonesModParticles {
    public static final DeferredRegister<ParticleType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, StonesMod.MODID);

    // Xray-Spark für Erz-Suche
    public static final RegistryObject<ParticleType<XrayParticleOptions>> XRAY_SPARK = REGISTRY.register("xray_spark", 
            () -> new ParticleType<>(false, XrayParticleOptions.DESERIALIZER) {
                @Override
                public com.mojang.serialization.Codec<XrayParticleOptions> codec() {
                    return XrayParticleOptions.CODEC;
                }
            });

    // NEU: Der einfache Partikel-Typ für die Echo-Motten
    public static final RegistryObject<SimpleParticleType> ECHO_MOTH = REGISTRY.register("echo_moth", 
            () -> new SimpleParticleType(false));
}