package net.stones.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Logik für die Echo-Motten-Partikel.
 * Spielt 16 Sprites in einem Loop ab, um das Flattern zu simulieren.
 */
@OnlyIn(Dist.CLIENT)
public class EchoMothParticle extends TextureSheetParticle {
    
    private final SpriteSet sprites;

    protected EchoMothParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, dx, dy, dz);
        this.sprites = sprites;
        
        this.friction = 0.95F; // Gleitet sanft
        this.xd = dx; this.yd = dy; this.zd = dz;
        
        // Zufällige Größe und Lebensdauer
        this.quadSize *= 0.7f + (level.random.nextFloat() * 0.5f);
        this.lifetime = 60 + level.random.nextInt(40);
        
        // Farbe: Ätherisches Blau/Cyan
        this.rCol = 0.4f; this.gCol = 0.9f; this.bCol = 1.0f;
        this.alpha = 0.0f; // Startet unsichtbar für Fade-In
        
        this.setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        
        // --- ANIMATION ---
        // Wir haben 16 Sprites. Alle 2 Ticks wechseln wir zum nächsten Frame.
        int frame = (this.age / 2) % 16;
        this.setSprite(sprites.get(frame, 15));
        
        // Sanftes Ein- und Ausblenden
        if (this.age < 15) {
            this.alpha = (float)this.age / 15.0f;
        } else if (this.age > this.lifetime - 15) {
            this.alpha = (float)(this.lifetime - this.age) / 15.0f;
        } else {
            this.alpha = 1.0f;
        }

        // Flattern: Leichte zufällige Richtungsänderungen
        this.xd += (this.random.nextFloat() - 0.5F) * 0.015F;
        this.zd += (this.random.nextFloat() - 0.5F) * 0.015F;
        this.yd += (this.random.nextFloat() - 0.2F) * 0.005F; // Tendiert leicht nach oben
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            return new EchoMothParticle(level, x, y, z, dx, dy, dz, this.sprites);
        }
    }
}