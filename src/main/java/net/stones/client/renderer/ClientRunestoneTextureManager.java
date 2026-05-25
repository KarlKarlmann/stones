package net.stones.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec2;
import net.stones.gui.layout.ShrineLayout;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Erzeugt dynamische Overlay-Texturen für den Runenschrein.
 * Visualisiert das statische mathematische Layout (Sternenkonstellation) des Schreins.
 * Diese Textur ist unabhängig vom Inventar-Inhalt und wird einmalig pro Schrein generiert.
 * Die Berechnung erfolgt rein deterministisch aus der shrineId.
 */
public class ClientRunestoneTextureManager {
    private static final Map<UUID, ResourceLocation> TEXTURE_CACHE = new HashMap<>();
    
    // Blanchitsu-Palette für das Sacred Sketching
    private static final int COL_DARK    = (255 << 24) | (20 << 16) | (25 << 8) | 30;    // Schwarzbraun (Linien)
    private static final int COL_YELLOW  = (255 << 24) | (149 << 16) | (229 << 8) | 228; // Kränkliches Gelb (Knoten)
    private static final int COL_WHITE   = (255 << 24) | (224 << 16) | (245 << 8) | 245; // Off-White Highlights (Zentrum)

    /**
     * Ruft die Textur für einen Schrein ab. Die Slot-Anzahl wird intern berechnet.
     */
    public static ResourceLocation getOrCreate(UUID shrineId) {
        return TEXTURE_CACHE.computeIfAbsent(shrineId, id -> generate(id));
    }

    /**
     * Repliziert die Logik aus ShrineInstance.generateRandomLayout(), 
     * um die exakte Anzahl der Slots deterministisch zu bestimmen.
     */
    private static int calculateSlotCountFromId(UUID id) {
        long seed = (id.getMostSignificantBits() ^ id.getLeastSignificantBits());
        Random rand = new Random(seed);
        int count = 0;

        // 1. Reguläre Runen-Slots (basiert auf RUNE_UNLOCK_LEVELS, 11 mögliche Stufen)
        for (int i = 0; i < 11; i++) {
            float roll = rand.nextFloat();
            if (i == 0) {
                // Das erste Level (Lvl 1) ist immer ein Slot
                count++;
            } else {
                // Für die restlichen 10 Level gibt es eine 60% Chance auf einen Slot (Minor/Major kombiniert)
                if (roll < 0.60f) {
                    count++;
                }
            }
        }

        // 2. Milestones (basiert auf POOL_MILESTONE, rand.nextInt(8) zusätzliche Slots)
        count += rand.nextInt(8);

        // Sicherheits-Fallback
        return Math.max(1, count);
    }

    /**
     * Generiert das unveränderliche Sternenlayout des Schreins.
     */
    public static ResourceLocation generate(UUID shrineId) {
        NativeImage img = new NativeImage(256, 256, true);
        
        // Hintergrund transparent (Overlay-Modus)
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                img.setPixelRGBA(x, y, 0); 
            }
        }

        // 1. Slot-Anzahl deterministisch aus der ID berechnen
        int slotCount = calculateSlotCountFromId(shrineId);

        // 2. Layout-Mathematik beziehen (Phyllotaxis-Spirale)
        List<Vec2> positions = ShrineLayout.generateSpiralPositions(slotCount);
        List<int[]> connections = ShrineLayout.generateConnections(positions);
        
        float centerX = 128.0f;
        float centerY = 128.0f;
        float scale = 0.85f; // Skalierung für die Blockoberfläche

        // 3. Ley-Linien (Verbindungen) zeichnen
        for (int[] conn : connections) {
            Vec2 p1 = positions.get(conn[0]);
            Vec2 p2 = positions.get(conn[1]);
            drawSketchLine(img, centerX + p1.x * scale, centerY + p1.y * scale, 
                           centerX + p2.x * scale, centerY + p2.y * scale, COL_DARK);
        }

        // 4. Slots (Knotenpunkte/Sterne) zeichnen
        for (int i = 0; i < positions.size(); i++) {
            Vec2 pos = positions.get(i);
            int x = (int)(centerX + pos.x * scale);
            int y = (int)(centerY + pos.y * scale);

            // Das Zentrum erhält ein markanteres Highlight
            int color = (i == 0) ? COL_WHITE : COL_YELLOW;
            int radius = (i == 0) ? 9 : 7;
            
            drawSketchCircle(img, x, y, radius, color);
        }

        String name = "shrine_blueprint_" + shrineId.toString().substring(0, 8);
        return Minecraft.getInstance().getTextureManager().register(name, new DynamicTexture(img));
    }

    private static void drawSketchLine(NativeImage img, float x1, float y1, float x2, float y2, int color) {
        Random rng = new Random();
        int steps = (int)Math.sqrt(Math.pow(x2-x1, 2) + Math.pow(y2-y1, 2)) * 2;
        for (int i = 0; i <= steps; i++) {
            float t = (float)i / steps;
            float jX = (rng.nextFloat() - 0.5f) * 1.4f;
            float jY = (rng.nextFloat() - 0.5f) * 1.4f;
            int px = (int)(x1 + (x2 - x1) * t + jX);
            int py = (int)(y1 + (y2 - y1) * t + jY);
            if (px >= 0 && px < 256 && py >= 0 && py < 256) {
                img.setPixelRGBA(px, py, color);
            }
        }
    }

    private static void drawSketchCircle(NativeImage img, int cx, int cy, int radius, int color) {
        Random rng = new Random();
        for (int r = radius - 1; r <= radius; r++) {
            for (double a = 0; a < Math.PI * 2; a += 0.1) {
                float jitter = (rng.nextFloat() - 0.5f) * 2.5f;
                int px = cx + (int)((r + jitter) * Math.cos(a));
                int py = cy + (int)((r + jitter) * Math.sin(a));
                if (px >= 0 && px < 256 && py >= 0 && py < 256) {
                    img.setPixelRGBA(px, py, color);
                }
            }
        }
    }

    public static void markDirty(UUID id) {
        TEXTURE_CACHE.remove(id);
    }
}