package net.stones.client.particle;

import net.stones.particle.XrayParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class XrayParticle extends TextureSheetParticle {
    
    protected XrayParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, XrayParticleOptions options) {
        super(level, x, y, z);
        this.friction = 1.0F; // Kein Bewegen/Abbremsen, wir wollen statische Marker
        this.xd = 0; this.yd = 0; this.zd = 0;
        
        // Dynamische Werte aus den Options
        this.lifetime = options.lifetime();
        this.quadSize *= options.size();
        
        this.setSpriteFromAge(sprites);
        this.rCol = 1.0F; this.gCol = 1.0F; this.bCol = 1.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return XrayParticleRender.XRAY;
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880; // Fullbright
    }

    @Override
    public void tick() {
        super.tick();
        // Sanftes Ausfaden am Ende der Lebenszeit
        if (this.age > this.lifetime - 10) {
            this.setAlpha(Math.max(0, (float)(this.lifetime - this.age) / 10.0F));
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<XrayParticleOptions> {
        private final SpriteSet sprites;
        public Provider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(XrayParticleOptions data, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
            XrayParticle p = new XrayParticle(level, x, y, z, this.sprites, data);
            p.pickSprite(this.sprites);
            return p;
        }
    }
}