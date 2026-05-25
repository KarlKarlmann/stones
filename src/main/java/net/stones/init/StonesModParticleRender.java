package net.stones.init;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stones.StonesMod;
import net.stones.client.particle.EchoMothParticle;
import net.stones.client.particle.XrayParticle;

@Mod.EventBusSubscriber(modid = StonesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class StonesModParticleRender {

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        // Registriert den Provider für den Xray-Spark
        event.registerSpriteSet(StonesModParticles.XRAY_SPARK.get(), XrayParticle.Provider::new);
        
        // Registriert den Provider für die neuen Echo-Motten
        event.registerSpriteSet(StonesModParticles.ECHO_MOTH.get(), EchoMothParticle.Provider::new);
    }
}