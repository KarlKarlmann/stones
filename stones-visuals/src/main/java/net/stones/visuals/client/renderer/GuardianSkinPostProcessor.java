package net.stones.visuals.client.renderer;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Verarbeitet Standard-Spielerskins deterministisch zu stilisierten
 * Wächter-Texturen (Tag- und Nacht-Variante mit Kanten-Jitter und Farbquantisierung).
 */
public class GuardianSkinPostProcessor {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<UUID, ResourceLocation> DAY_CACHE = new HashMap<>();
    private static final Map<UUID, ResourceLocation> NIGHT_CACHE = new HashMap<>();

    private static final int SCALE = 2; // 2x Upscaling für feinere Jitter-Details

    // ABGR Format für NativeImage Little-Endian
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

            Random rng = new Random(playerId.hashCode());

            for (int y = 0; y < h * SCALE; y++) {
                for (int x = 0; x < w * SCALE; x++) {
                    float noiseX = (rng.nextFloat() - 0.5f) * 1.6f;
                    float noiseY = (rng.nextFloat() - 0.5f) * 1.6f;
                    
                    int sX = (int) Math.floor((x / (float)SCALE) + noiseX);
                    int sY = (int) Math.floor((y / (float)SCALE) + noiseY);

                    sX = Math.max(0, Math.min(w - 1, sX));
                    sY = Math.max(0, Math.min(h - 1, sY));

                    int argb = original.getPixelRGBA(sX, sY);
                    int a = (argb >> 24) & 0xFF;
                    int b = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int r = argb & 0xFF;
                    
                    float luma = (0.2126f * r + 0.7152f * g + 0.0722f * b);

                    int sketchColor = 0;
                    if (a > 50) {
                        if (isOutline(original, sX, sY, w, h)) {
                            sketchColor = COL_DEEP_BLACK;
                        } else if (r > 100 && r > g * 1.4f && r > b * 1.4f) {
                            sketchColor = COL_ACCENT_RED;
                        } else {
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
                            int rS = sketchColor & 0xFF;
                            int gS = (sketchColor >> 8) & 0xFF;
                            int bS = (sketchColor >> 16) & 0xFF;

                            resR = (rS + r) / 2;
                            resG = (gS + g) / 2;
                            resB = (bS + b) / 2;
                            resA = 127;
                        } else {
                            resR = r;
                            resG = g;
                            resB = b;
                            resA = a / 2;
                        }
                        finalDayColor = (resA << 24) | (resB << 16) | (resG << 8) | resR;
                    }
                    dayImg.setPixelRGBA(x, y, finalDayColor);

                    int nightColor = 0;
                    if (a > 100) {
                        if (isOutline(original, sX, sY, w, h)) {
                            nightColor = COL_DEEP_BLACK;
                        } else if (luma > 200) {
                            nightColor = COL_OFF_WHITE;
                        }
                    }
                    nightImg.setPixelRGBA(x, y, nightColor);
                }
            }

            String suffix = playerId.toString().substring(0, 8);
            DAY_CACHE.put(playerId, mc.getTextureManager().register("guardian_day_" + suffix, new DynamicTexture(dayImg)));
            NIGHT_CACHE.put(playerId, mc.getTextureManager().register("guardian_night_" + suffix, new DynamicTexture(nightImg)));

        } catch (Exception e) {
            LOGGER.error("Fehler beim Erzeugen der Wächter-Textur für Spieler " + playerId, e);
        }
    }

    private static boolean isOutline(NativeImage img, int x, int y, int w, int h) {
        if (x == 0 || x == w - 1 || y == 0 || y == h - 1) return true;
        if (((img.getPixelRGBA(x + 1, y) >> 24) & 0xFF) < 128 ||
            ((img.getPixelRGBA(x - 1, y) >> 24) & 0xFF) < 128 ||
            ((img.getPixelRGBA(x, y + 1) >> 24) & 0xFF) < 128 ||
            ((img.getPixelRGBA(x, y - 1) >> 24) & 0xFF) < 128) return true;

        if (y < 16 && x <= 32) {
            if (y == 0 || y == 8 || y == 15) return true;
            if (x % 8 == 0) return true;
        }
        if (y >= 16 && y < 32) {
            if (y == 16 || y == 24 || y == 31) return true;
            if (x == 16 || x == 20 || x == 28 || x == 32 || x == 40) return true;
        }
        if (y >= 32 && (x % 4 == 0)) return true;

        return false;
    }

    public static void clearCache() {
        DAY_CACHE.clear();
        NIGHT_CACHE.clear();
    }
}