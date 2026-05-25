package net.stones.client.renderer;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.stones.StonesMod;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Blanchitsu Skin-Prozessor (High-Fidelity NPR).
 * Implementiert Upscaling, Koordinaten-Jitter und eine 5-Farben-Quantisierung.
 * * Palette:
 * - DEEP_BLACK: Harte Konturen und tiefste Schatten (#050505)
 * - DARK_BROWN: Sekundäre Schatten (#1E1914)
 * - SICKLY_YELLOW: Mitteltöne / Verfall (#E4E595)
 * - OFF_WHITE: Highlights / Glanzlichter (#F5F5E0)
 * - ACCENT_RED: Blut / Markierungen (#A01414)
 */
public class GrimdarkSkinManager {
    private static final Map<UUID, ResourceLocation> DAY_CACHE = new HashMap<>();
    private static final Map<UUID, ResourceLocation> NIGHT_CACHE = new HashMap<>();

    private static final int SCALE = 2; // 2x Upscaling (128x128) für feinere Jitter-Details

    // --- PALETTEN-DEFINITION (ABGR Format für NativeImage Little-Endian) ---
    // Format: (A << 24) | (B << 16) | (G << 8) | R
    private static final int COL_DEEP_BLACK    = (255 << 24) | (5 << 16) | (5 << 8) | 5;
    private static final int COL_DARK_BROWN    = (255 << 24) | (20 << 16) | (25 << 8) | 30;
    private static final int COL_SICKLY_YELLOW = (255 << 24) | (149 << 16) | (229 << 8) | 228;
    private static final int COL_OFF_WHITE     = (255 << 24) | (224 << 16) | (245 << 8) | 245;
    private static final int COL_ACCENT_RED    = (255 << 24) | (20 << 16) | (20 << 8) | 160;

    public static ResourceLocation getDaySkin(UUID id) { return DAY_CACHE.get(id); }
    public static ResourceLocation getNightSkin(UUID id) { return NIGHT_CACHE.get(id); }

    public static void getOrProcess(UUID playerId) {
        if (DAY_CACHE.containsKey(playerId)) return;

        Minecraft mc = Minecraft.getInstance();
        GameProfile profile = new GameProfile(playerId, null);
        mc.getMinecraftSessionService().fillProfileProperties(profile, false);
        ResourceLocation skinLoc = mc.getSkinManager().getInsecureSkinLocation(profile);

        try {
            var resource = mc.getResourceManager().getResource(skinLoc);
            if (resource.isEmpty()) return;

            NativeImage original = NativeImage.read(resource.get().open());
            int w = original.getWidth();
            int h = original.getHeight();

            NativeImage dayImg = new NativeImage(w * SCALE, h * SCALE, true);
            NativeImage nightImg = new NativeImage(w * SCALE, h * SCALE, true);

            // Deterministischer Zufall pro Spieler für konsistenten Sketch-Look
            Random rng = new Random(playerId.hashCode());

            for (int y = 0; y < h * SCALE; y++) {
                for (int x = 0; x < w * SCALE; x++) {
                    
                    // 1. KOORDINATEN-JITTER (Global angewendet)
                    float noiseX = (rng.nextFloat() - 0.5f) * 1.6f;
                    float noiseY = (rng.nextFloat() - 0.5f) * 1.6f;
                    
                    int sX = (int) Math.floor((x / (float)SCALE) + noiseX);
                    int sY = (int) Math.floor((y / (float)SCALE) + noiseY);

                    sX = Math.max(0, Math.min(w - 1, sX));
                    sY = Math.max(0, Math.min(h - 1, sY));

                    // 2. QUELLDATEN EXTRAHIEREN (Korrektes ABGR Format)
                    int argb = original.getPixelRGBA(sX, sY);
                    int a = (argb >> 24) & 0xFF;
                    int b = (argb >> 16) & 0xFF; // Blau ist an Position 16
                    int g = (argb >> 8) & 0xFF;  // Grün ist an Position 8
                    int r = argb & 0xFF;         // Rot ist an Position 0
                    
                    // Luminanz für die Quantisierung
                    float luma = (0.2126f * r + 0.7152f * g + 0.0722f * b);

                    // --- 3. DAY SKIN LOGIK (Vollständige Skizze mit Blending & Transparenz) ---
                    int sketchColor = 0;
                    if (a > 50) {
                        if (isOutline(original, sX, sY, w, h)) {
                            sketchColor = COL_DEEP_BLACK;
                        } 
                        else if (r > 100 && r > g * 1.4f && r > b * 1.4f) {
                            sketchColor = COL_ACCENT_RED;
                        } 
                        else {
                            if (luma < 45) sketchColor = COL_DEEP_BLACK;
                            else if (luma < 100) sketchColor = COL_DARK_BROWN;
                            else if (luma < 195) sketchColor = COL_SICKLY_YELLOW;
                            else sketchColor = COL_OFF_WHITE;
                        }
                    }

                    int finalDayColor = 0;
                    if (a > 0) {
                        int resR, resG, resB, resA;
                        if (sketchColor != 0) {
                            // Extraktion der Skizzen-Komponenten (ABGR)
                            int rS = sketchColor & 0xFF;
                            int gS = (sketchColor >> 8) & 0xFF;
                            int bS = (sketchColor >> 16) & 0xFF;

                            // Mische 50% Skizze mit 50% Original
                            resR = (rS + r) / 2;
                            resG = (gS + g) / 2;
                            resB = (bS + b) / 2;
                            resA = 127; // Generell 50% durchsichtig am Ende
                        } else {
                            // Nur Original-Hintergrund, ebenfalls 50% durchsichtig
                            resR = r;
                            resG = g;
                            resB = b;
                            resA = a / 2;
                        }
                        
                        finalDayColor = (resA << 24) | (resB << 16) | (resG << 8) | resR;
                    }
                    dayImg.setPixelRGBA(x, y, finalDayColor);

                    // --- 4. NIGHT SKIN LOGIK (Minimalistischer Geisterschein) ---
                    int nightColor = 0;
                    if (a > 100) {
                        if (isOutline(original, sX, sY, w, h)) {
                            nightColor = COL_DEEP_BLACK;
                        } 
                        else if (luma > 200) {
                            nightColor = COL_OFF_WHITE;
                        }
                    }
                    nightImg.setPixelRGBA(x, y, nightColor);
                }
            }

            String suffix = playerId.toString().substring(0, 8);
            DAY_CACHE.put(playerId, mc.getTextureManager().register("stones_day_" + suffix, new DynamicTexture(dayImg)));
            NIGHT_CACHE.put(playerId, mc.getTextureManager().register("stones_night_" + suffix, new DynamicTexture(nightImg)));

        } catch (Exception e) {
            StonesMod.LOGGER.error("Blanchitsu NPR processing failed for " + playerId, e);
        }
    }

    private static boolean isOutline(NativeImage img, int x, int y, int w, int h) {
        if (x == 0 || x == w - 1 || y == 0 || y == h - 1) return true;
        if (((img.getPixelRGBA(x + 1, y) >> 24) & 0xFF) < 128 ||
            ((img.getPixelRGBA(x - 1, y) >> 24) & 0xFF) < 128 ||
            ((img.getPixelRGBA(x, y + 1) >> 24) & 0xFF) < 128 ||
            ((img.getPixelRGBA(x, y - 1) >> 24) & 0xFF) < 128) return true;

        if (y < 16 && x <= 32) { // Kopf
            if (y == 0 || y == 8 || y == 15) return true;
            if (x % 8 == 0) return true;
        }
        if (y >= 16 && y < 32) { // Torso/Arme
            if (y == 16 || y == 24 || y == 31) return true;
            if (x == 16 || x == 20 || x == 28 || x == 32 || x == 40) return true;
        }
        if (y >= 32 && (x % 4 == 0)) return true; // Beine/Füße

        return false;
    }

    public static void clearCache() {
        DAY_CACHE.clear();
        NIGHT_CACHE.clear();
    }
}