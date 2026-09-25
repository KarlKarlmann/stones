package net.stones.client.fx;

import net.minecraft.world.phys.Vec3;

public class BeamInstance {

    public Vec3 start;
    public Vec3 end;
    public float width;
    public String textureId;
    public float r, g, b, a;
    public float uvScrollSpeed;
    public float uvRepeat;
    public SpriteInstance.BlendMode blendMode;

    public int age = 0;
    public final int maxAge;

    public BeamInstance(Vec3 start, Vec3 end, float width, String textureId, 
                        float r, float g, float b, float a, 
                        float uvScrollSpeed, float uvRepeat,
                        SpriteInstance.BlendMode blendMode, int maxAge) {
        this.start = start;
        this.end = end;
        this.width = width;
        this.textureId = textureId;
        this.r = r; this.g = g; this.b = b; this.a = a;
        this.uvScrollSpeed = uvScrollSpeed;
        this.uvRepeat = uvRepeat;
        this.blendMode = blendMode;
        this.maxAge = Math.max(1, maxAge);
    }

    public void tick() {
        this.age++;
    }

    public boolean isDead() {
        return this.age >= this.maxAge;
    }
}