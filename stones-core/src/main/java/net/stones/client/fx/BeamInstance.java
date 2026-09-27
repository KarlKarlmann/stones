package net.stones.client.fx;

import net.minecraft.world.phys.Vec3;

public class BeamInstance {

    public Vec3 start;
    public Vec3 end;
    public Vec3 prevStart;
    public Vec3 prevEnd;

    public BeamType beamType;
    public String textureId;

    public float coreWidth;
    public float coronaWidth;

    public float r, g, b, a;
    public float uvScrollSpeed;
    public float uvRepeat;

    public float helixRadius;
    public float helixFrequency;
    public float helixSpeed;

    public int age = 0;
    public final int maxAge;

    public BeamInstance(Vec3 start, Vec3 end, BeamType beamType, String textureId,
                        float coreWidth, float coronaWidth,
                        float r, float g, float b, float a,
                        float uvScrollSpeed, float uvRepeat,
                        float helixRadius, float helixFrequency, float helixSpeed,
                        int maxAge) {
        this.start = start;
        this.end = end;
        this.prevStart = start;
        this.prevEnd = end;
        this.beamType = beamType != null ? beamType : BeamType.LASER;
        this.textureId = textureId;
        this.coreWidth = coreWidth;
        this.coronaWidth = coronaWidth;
        this.r = r; this.g = g; this.b = b; this.a = a;
        this.uvScrollSpeed = uvScrollSpeed;
        this.uvRepeat = uvRepeat;
        this.helixRadius = helixRadius;
        this.helixFrequency = helixFrequency;
        this.helixSpeed = helixSpeed;
        this.maxAge = Math.max(1, maxAge);
    }

    public void tick() {
        this.prevStart = this.start;
        this.prevEnd = this.end;
        this.age++;
    }

    public boolean isDead() {
        return this.age >= this.maxAge;
    }

    public Vec3 getInterpolatedStart(float partialTick) {
        return prevStart.lerp(start, partialTick);
    }

    public Vec3 getInterpolatedEnd(float partialTick) {
        return prevEnd.lerp(end, partialTick);
    }
}