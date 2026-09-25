package net.stones.visuals.client.gui;

import net.minecraft.world.phys.Vec2;
import java.util.ArrayList;
import java.util.List;

public class CosmosSpiralHelper {

    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private static final double SPREAD_CONSTANT = 24.0;

    public static List<Vec2> generateSpiralPositions(int slotCount) {
        List<Vec2> positions = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            double theta = i * GOLDEN_ANGLE;
            double radius = SPREAD_CONSTANT * Math.sqrt(i);
            float x = (float) (radius * Math.cos(theta));
            float y = (float) (radius * Math.sin(theta));
            positions.add(new Vec2(x, y));
        }
        return positions;
    }

    public static List<int[]> generateConnections(List<Vec2> positions) {
        List<int[]> connections = new ArrayList<>();
        double maxDistSq = 45.0 * 45.0; 
        for (int i = 0; i < positions.size(); i++) {
            Vec2 p1 = positions.get(i);
            for (int j = 0; j < i; j++) {
                Vec2 p2 = positions.get(j);
                double dx = p1.x - p2.x;
                double dy = p1.y - p2.y;
                if (dx * dx + dy * dy < maxDistSq) {
                    connections.add(new int[]{i, j});
                }
            }
        }
        return connections;
    }
}