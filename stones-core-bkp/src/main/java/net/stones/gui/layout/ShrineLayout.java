package net.stones.gui.layout;

import net.stones.data.ShrineInstance;
import java.util.*;

public class ShrineLayout {

    /**
     * ZENTRALE LOGIK: Generiert aus ID und maxLevel deterministisch die Slot-Konfigurationen.
     */
    public static List<ShrineInstance.SlotConfig> generateDeterministicLayout(UUID id, int maxLevel) {
        List<ShrineInstance.SlotConfig> slotLayout = new ArrayList<>();
        long seed = (id.getMostSignificantBits() ^ id.getLeastSignificantBits());
        Random rand = new Random(seed);
        int currentIndex = 0;

        float scale = maxLevel / 100.0f;
        
        int minSlots = Math.max(1, (int)(8 * scale));
        int maxPossibleSlots = Math.max(minSlots, (int)(16 * scale));
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

        int minMilestones = Math.max(0, (int)(1 * scale));
        int maxMilestones = Math.max(minMilestones, (int)(3 * scale));
        int milestoneCount = minMilestones + rand.nextInt(maxMilestones - minMilestones + 1);

        List<Integer> milestoneLevels = new ArrayList<>();
        for (int i = 0; i < milestoneCount; i++) {
            int lvl = (maxLevel > 5) ? (5 + rand.nextInt(maxLevel - 5 + 1)) : 5;
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
}