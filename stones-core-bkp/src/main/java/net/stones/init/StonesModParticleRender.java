package net.stones.init;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stones.StonesMod;
import net.stones.client.particle.XrayParticle;

@Mod.EventBusSubscriber(modid = StonesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class StonesModParticleRender {

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(StonesModParticles.XRAY_SPARK.get(), XrayParticle.Provider::new);
    }
}