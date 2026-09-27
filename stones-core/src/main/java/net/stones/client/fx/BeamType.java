package net.stones.client.fx;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public enum BeamType {

    LASER(false, (start, end, partialTick, age, rng) -> List.of(start, end)),

    LIGHTNING(false, (start, end, partialTick, age, rng) -> {
        List<Vec3> nodes = new ArrayList<>();
        nodes.add(start);
        int segments = 8;
        Vec3 dir = end.subtract(start);
        Vec3 norm = dir.normalize();
        Vec3 perp1 = getOrthogonalVector(norm);
        Vec3 perp2 = norm.cross(perp1).normalize();

        for (int i = 1; i < segments; i++) {
            double t = (double) i / segments;
            double jitter = 0.2;
            double offsetX = (rng.nextDouble() - 0.5) * jitter;
            double offsetY = (rng.nextDouble() - 0.5) * jitter;
            Vec3 midPoint = start.add(dir.scale(t))
                    .add(perp1.scale(offsetX))
                    .add(perp2.scale(offsetY));
            nodes.add(midPoint);
        }
        nodes.add(end);
        return nodes;
    }),

    RAINBOW_RAY(false, (start, end, partialTick, age, rng) -> List.of(start, end)),

    ORBIT_LASER(true, (start, end, partialTick, age, rng) -> List.of(start, end));

    private final boolean outerHelix;
    private final PathGenerator pathGenerator;

    BeamType(boolean outerHelix, PathGenerator pathGenerator) {
        this.outerHelix = outerHelix;
        this.pathGenerator = pathGenerator;
    }

    public boolean hasOuterHelix() {
        return outerHelix;
    }

    public PathGenerator getPathGenerator() {
        return pathGenerator;
    }

    public void applyBlendMode() {
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
    }

    public static Vec3 getOrthogonalVector(Vec3 vec) {
        Vec3 norm = vec.normalize();
        Vec3 arbitrary = Math.abs(norm.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return norm.cross(arbitrary).normalize();
    }

    @FunctionalInterface
    public interface PathGenerator {
        List<Vec3> generatePath(Vec3 start, Vec3 end, float partialTick, int age, Random rng);
    }
}