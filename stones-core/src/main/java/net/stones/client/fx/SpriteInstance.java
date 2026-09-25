package net.stones.client.fx;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SpriteInstance {

    public enum Facing { BILLBOARD, GROUND_FLAT, VELOCITY_ALIGNED }
    public enum BlendMode { ADDITIVE, ALPHA }

    public static class Keyframe {
        public float time; // 0.0f bis 1.0f
        public float r, g, b, a;

        public Keyframe(float time, float r, float g, float b, float a) {
            this.time = Mth.clamp(time, 0.0f, 1.0f);
            this.r = r;
            this.g = g;
            this.b = b;
            this.a = a;
        }

        public static Keyframe fromHexAndAlpha(float time, String hex, float alpha) {
            try {
                int rgb = Integer.parseInt(hex.replace("#", ""), 16);
                float r = ((rgb >> 16) & 0xFF) / 255.0f;
                float g = ((rgb >> 8) & 0xFF) / 255.0f;
                float b = (rgb & 0xFF) / 255.0f;
                return new Keyframe(time, r, g, b, alpha);
            } catch (Exception e) {
                return new Keyframe(time, 1.0f, 1.0f, 1.0f, alpha);
            }
        }
    }

    // --- Anker & Physik ---
    public Vec3 pos;
    public Vec3 prevPos;
    public Vec3 velocity;
    public float drag;
    public float gravity;

    // --- Geometrie & Traformation ---
    public float scale;
    public float growthRate;
    public float rotation;
    public float spinSpeed;
    public Facing facing;

    // --- Darstellende Eigenschaften ---
    public String textureId;
    public BlendMode blendMode;
    public List<Keyframe> keyframes = new ArrayList<>();

    // --- Lebensdauer ---
    public int age = 0;
    public final int maxAge;

    public SpriteInstance(Vec3 pos, Vec3 velocity, float drag, float gravity,
                          float initialScale, float growthRate, float spinSpeed,
                          Facing facing, String textureId, BlendMode blendMode,
                          int maxAge, List<Keyframe> keyframes) {
        this.pos = pos;
        this.prevPos = pos;
        this.velocity = velocity;
        this.drag = drag;
        this.gravity = gravity;
        this.scale = initialScale;
        this.growthRate = growthRate;
        this.spinSpeed = spinSpeed;
        this.facing = facing;
        this.textureId = textureId;
        this.blendMode = blendMode;
        this.maxAge = Math.max(1, maxAge);

        if (keyframes != null && !keyframes.isEmpty()) {
            this.keyframes.addAll(keyframes);
            this.keyframes.sort(Comparator.comparingDouble(k -> k.time));
        } else {
            // Fallback Keyframes
            this.keyframes.add(new Keyframe(0.0f, 1.0f, 1.0f, 1.0f, 1.0f));
            this.keyframes.add(new Keyframe(1.0f, 1.0f, 1.0f, 1.0f, 0.0f));
        }
    }

    public void tick() {
        this.prevPos = this.pos;
        this.age++;

        // Physik-Update
        this.velocity = this.velocity.scale(this.drag).subtract(0, this.gravity, 0);
        this.pos = this.pos.add(this.velocity);

        // Transform-Update
        this.scale += this.growthRate;
        this.rotation += this.spinSpeed;
    }

    public boolean isDead() {
        return this.age >= this.maxAge;
    }

    public Vec3 getInterpolatedPos(float partialTick) {
        return new Vec3(
            Mth.lerp(partialTick, this.prevPos.x, this.pos.x),
            Mth.lerp(partialTick, this.prevPos.y, this.pos.y),
            Mth.lerp(partialTick, this.prevPos.z, this.pos.z)
        );
    }

    /**
     * Interpoliert RGBA fließend zwischen den N Keyframes über die Lebensdauer.
     */
    public float[] getCurrentColor(float partialTick) {
        float progress = Mth.clamp((this.age + partialTick) / (float) this.maxAge, 0.0f, 1.0f);

        if (keyframes.isEmpty()) return new float[]{1f, 1f, 1f, 1f};
        if (keyframes.size() == 1) return new float[]{keyframes.get(0).r, keyframes.get(0).g, keyframes.get(0).b, keyframes.get(0).a};

        if (progress <= keyframes.get(0).time) {
            Keyframe k = keyframes.get(0);
            return new float[]{k.r, k.g, k.b, k.a};
        }

        if (progress >= keyframes.get(keyframes.size() - 1).time) {
            Keyframe k = keyframes.get(keyframes.size() - 1);
            return new float[]{k.r, k.g, k.b, k.a};
        }

        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe k1 = keyframes.get(i);
            Keyframe k2 = keyframes.get(i + 1);

            if (progress >= k1.time && progress <= k2.time) {
                float factor = (progress - k1.time) / (k2.time - k1.time);
                return new float[]{
                    Mth.lerp(factor, k1.r, k2.r),
                    Mth.lerp(factor, k1.g, k2.g),
                    Mth.lerp(factor, k1.b, k2.b),
                    Mth.lerp(factor, k1.a, k2.a)
                };
            }
        }

        return new float[]{1f, 1f, 1f, 1f};
    }
}