package net.stones.gui.layout;

import net.minecraft.world.phys.Vec2;
import net.stones.data.ShrineInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Kern-Mathematik für das thaumaturgische Interface.
 * SINGLE SOURCE OF TRUTH: Generiert alle Layouts deterministisch.
 */
public class ShrineLayout {

    // Der Goldene Winkel: 137.508 Grad in Radiant
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));
    private static final double SPREAD_CONSTANT = 24.0;

    /**
     * ZENTRALE LOGIK: Generiert aus ID und maxLevel immer 100% identische Layouts.
     * Ersetzt die asynchronen Generatoren auf Client und Server.
     */
    public static List<ShrineInstance.SlotConfig> generateDeterministicLayout(UUID id, int maxLevel) {
        List<ShrineInstance.SlotConfig> slotLayout = new ArrayList<>();
        long seed = (id.getMostSignificantBits() ^ id.getLeastSignificantBits());
        Random rand = new Random(seed);
        int currentIndex = 0;

        float scale = maxLevel / 100.0f;
        
        int minSlots = (int)(8 * scale);
        int maxPossibleSlots = (int)(16 * scale);
        if (minSlots < 1) minSlots = 1;
        if (maxPossibleSlots < minSlots) maxPossibleSlots = minSlots;

        int regularSlotsCount = minSlots + rand.nextInt(maxPossibleSlots - minSlots + 1);

        List<Integer> regularLevels = new ArrayList<>();
        for (int i = 0; i < regularSlotsCount; i++) {
            int lvl = 1;
            if (i > 0 && regularSlotsCount > 1) {
                double progress = (double) i / (regularSlotsCount - 1);
                lvl = 1 + (int)((maxLevel - 1) * Math.pow(progress, 1.3));
            }
            if (!regularLevels.contains(lvl)) {
                regularLevels.add(lvl);
            }
        }
        Collections.sort(regularLevels);

        for (int lvl : regularLevels) {
            float roll = rand.nextFloat();
            if (lvl == 1) {
                if (roll < 0.90f) slotLayout.add(new ShrineInstance.SlotConfig(ShrineInstance.SlotType.MINOR, lvl, currentIndex++));
                else slotLayout.add(new ShrineInstance.SlotConfig(ShrineInstance.SlotType.MAJOR, lvl, currentIndex++));
            } else {
                if (roll < 0.45f) slotLayout.add(new ShrineInstance.SlotConfig(ShrineInstance.SlotType.MINOR, lvl, currentIndex++));
                else if (roll < 0.60f) slotLayout.add(new ShrineInstance.SlotConfig(ShrineInstance.SlotType.MAJOR, lvl, currentIndex++));
            }
        }

        int minMilestones = (int)(1 * scale);
        int maxMilestones = (int)(3 * scale);
        if (minMilestones < 0) minMilestones = 0;
        if (maxMilestones < minMilestones) maxMilestones = minMilestones;

        int milestoneCount = minMilestones + rand.nextInt(maxMilestones - minMilestones + 1);
        List<Integer> milestoneLevels = new ArrayList<>();
        for (int i = 0; i < milestoneCount; i++) {
            int lvl = 5;
            if (maxLevel > 5) {
                lvl = 5 + rand.nextInt(maxLevel - 5 + 1);
            }
            if (!regularLevels.contains(lvl) && !milestoneLevels.contains(lvl)) {
                milestoneLevels.add(lvl);
            }
        }
        Collections.sort(milestoneLevels);

        for (int lvl : milestoneLevels) {
            slotLayout.add(new ShrineInstance.SlotConfig(ShrineInstance.SlotType.MILESTONE, lvl, currentIndex++));
        }

        slotLayout.sort(Comparator.comparingInt(a -> a.requiredLevel));

        for (int i = 0; i < slotLayout.size(); i++) {
            slotLayout.get(i).inventoryIndex = i;
        }

        return slotLayout;
    }

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
                if (dx*dx + dy*dy < maxDistSq) {
                    connections.add(new int[]{i, j});
                }
            }
        }
        return connections;
    }
}