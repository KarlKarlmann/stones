package net.stones.gui.layout;

import net.minecraft.world.phys.Vec2;
import java.util.ArrayList;
import java.util.List;

/**
 * Kern-Mathematik für das thaumaturgische Interface.
 * Implementiert die Vogel-Spirale (Phyllotaxis) für organische Verteilung.
 */
public class ShrineLayout {

    // Der Goldene Winkel: 137.508 Grad in Radiant
    // Dies ist der Schlüssel, um Moiré-Muster zu vermeiden und "natürliche" Unordnung zu schaffen.
    private static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    // Skalierungsfaktor: Wie weit sind die Punkte auseinander?
    // 24.0 ist empirisch ermittelt für 18px Slots + 6px "Luft".
    private static final double SPREAD_CONSTANT = 24.0;

    /**
     * Berechnet die Positionen für alle Slots basierend auf der Spirale.
     * 0,0 ist hierbei der mathematische Ursprung (Zentrum).
     */
    public static List<Vec2> generateSpiralPositions(int slotCount) {
        List<Vec2> positions = new ArrayList<>(slotCount);

        for (int i = 0; i < slotCount; i++) {
            // Formel: r = c * sqrt(n), theta = n * 137.5 deg
            double theta = i * GOLDEN_ANGLE;
            double radius = SPREAD_CONSTANT * Math.sqrt(i);

            // Polar zu Kartesisch
            float x = (float) (radius * Math.cos(theta));
            float y = (float) (radius * Math.sin(theta));

            positions.add(new Vec2(x, y));
        }
        return positions;
    }

    /**
     * Einfacher Algorithmus zur Generierung von Ley-Linien (Verbindungen).
     * Verbindet Punkte, die nah beieinander liegen, um ein Netz zu bilden.
     */
    public static List<int[]> generateConnections(List<Vec2> positions) {
        List<int[]> connections = new ArrayList<>();
        double maxDistSq = 45.0 * 45.0; // Maximale Verbindungslänge (quadriert für Performance)

        for (int i = 0; i < positions.size(); i++) {
            Vec2 p1 = positions.get(i);
            
            // Wir schauen nur auf vorherige Punkte, um Dopplungen zu vermeiden
            // und die Spirale "nach innen" zu verbinden.
            for (int j = Math.max(0, i - 8); j < i; j++) {
                Vec2 p2 = positions.get(j);
                
                double dx = p1.x - p2.x;
                double dy = p1.y - p2.y;
                double distSq = dx*dx + dy*dy;

                if (distSq < maxDistSq) {
                    connections.add(new int[]{i, j});
                }
            }
        }
        return connections;
    }
}