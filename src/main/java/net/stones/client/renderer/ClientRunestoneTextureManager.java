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

public class ClientRunestoneTextureManager {

    private record CacheKey(UUID id, int param) {}
    private static final Map<CacheKey, ResourceLocation> TEXTURE_CACHE = new HashMap<>();
    
    private static final int COL_DARK    = (255 << 24) | (20 << 16) | (25 << 8) | 30;    
    private static final int COL_YELLOW  = (255 << 24) | (149 << 16) | (229 << 8) | 228; 
    private static final int COL_WHITE   = (255 << 24) | (224 << 16) | (245 << 8) | 245; 

    /**
     * Für den Renderer: Berechnet die Slots sauber via Single Source of Truth,
     * chached sie dann aber anhand des maxLevels, da dieses von der BlockEntity kommt.
     */
	public static ResourceLocation getOrCreate(UUID shrineId, int maxLevel) {
			return TEXTURE_CACHE.computeIfAbsent(new CacheKey(shrineId, maxLevel), key -> {
				int exactSlotCount;
				
				// Legacy Fix: Alte Schreine (Level 100) nutzen die alte Berechnung
				if (key.param() == 100) {
					exactSlotCount = calculateLegacySlotCount(key.id());
				} else {
					exactSlotCount = ShrineLayout.generateDeterministicLayout(key.id(), key.param()).size();
				}
				
				return generate(key.id(), exactSlotCount);
			});
		}

	/**
	 * Rekonstruiert die exakte Slot-Anzahl der alten Schrein-Generation für Legacy-Texturen.
	 */
	private static int calculateLegacySlotCount(UUID id) {
		long seed = (id.getMostSignificantBits() ^ id.getLeastSignificantBits());
		java.util.Random rand = new java.util.Random(seed);
		int count = 0;

		// 1. Reguläre Runen-Slots (alte Logik)
		for (int i = 0; i < 11; i++) {
			float roll = rand.nextFloat();
			if (i == 0) {
				count++;
			} else {
				if (roll < 0.60f) {
					count++;
				}
			}
		}

		// 2. Milestones (alte Logik)
		count += rand.nextInt(8);

		return Math.max(1, count);
	}
	
    public static ResourceLocation getOrCreateExact(UUID shrineId, int exactSlotCount) {
        if (exactSlotCount < 1) exactSlotCount = 8;
        return TEXTURE_CACHE.computeIfAbsent(new CacheKey(shrineId, exactSlotCount), key -> generate(key.id(), key.param()));
    }

    public static ResourceLocation generate(UUID shrineId, int slotCount) {
        NativeImage img = new NativeImage(256, 256, true);
        for (int y = 0; y < 256; y++) {
            for (int x = 0; x < 256; x++) {
                img.setPixelRGBA(x, y, 0); 
            }
        }

        List<Vec2> positions = ShrineLayout.generateSpiralPositions(slotCount);
        List<int[]> connections = ShrineLayout.generateConnections(positions);
        
        float centerX = 128.0f;
        float centerY = 128.0f;

        float scale = 0.85f; 
        if (slotCount > 19) {
            float baselineRadiusFactor = (float) Math.sqrt(19);
            float currentRadiusFactor = (float) Math.sqrt(slotCount);
            scale *= (baselineRadiusFactor / currentRadiusFactor);
        }

        for (int[] conn : connections) {
            Vec2 p1 = positions.get(conn[0]);
            Vec2 p2 = positions.get(conn[1]);
            drawSketchLine(img, centerX + p1.x * scale, centerY + p1.y * scale, 
                           centerX + p2.x * scale, centerY + p2.y * scale, COL_DARK);
        }

        for (int i = 0; i < positions.size(); i++) {
            Vec2 pos = positions.get(i);
            int x = (int)(centerX + pos.x * scale);
            int y = (int)(centerY + pos.y * scale);

            int color = (i == 0) ? COL_WHITE : COL_YELLOW;
            int radius = (i == 0) ? 9 : 7;
            
            drawSketchCircle(img, x, y, radius, color);
        }

        String name = "shrine_blueprint_" + shrineId.toString().substring(0, 8) + "_" + slotCount;
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
        TEXTURE_CACHE.entrySet().removeIf(entry -> entry.getKey().id().equals(id));
    }
}