package net.stones.visuals.client.renderer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class ClientDynamicLabelHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    public record LabelEntry(ResourceLocation location, int size) {}

    private record CoinPalette(int outerRing, int innerRing, int glyphRgb, int shadowRgb, int diamondRgb) {}

    // Master-Pool aller im Schrein-Ökosystem erlaubten Farbtöne (Harmonie ohne Giftfarben)
    // Beinhaltet die Farbfamilien: Arkan-Sonne, Kosmischer Amethyst, Äther-Resonanz, Alchemie-Jade, Blutritual & Mondsilber
    private static final int[] PALETTE = {
        0xE4E595, // Sickly Yellow (Arkan-Gold / Primäridentität)
        0xF5F5E0, // Off-White (Sternenstaub / Alabaster)
        0xFFD54F, // Sonnen-Bernstein
        0xB38848, // Antike Bronze
        0x7E57C2, // Tiefes Purpur (Kosmischer Kern)
        0xC792EA, // Flieder / Amethyst-Resonanz
        0x4DD0E1, // Äther-Türkis / Resonanz
        0x0097A7, // Tiefsee-Azur
        0x2E7D32, // Smaragd / Alchemie-Jade
        0x81C784, // Helle Jade
        0xB87333, // Alchemie-Kupfer
        0xC62828, // Karminrot / Ritualblut
        0xE57373, // Helles Karmin
        0xD84315, // Glutkupfer / Terrakotta
        0xFFAB91, // Lodernde Glut
        0xE0E0E0, // Reines Mondsilber
        0x90A4AE, // Kobaltstahl / Dämmerungszink
        0xE0F7FA  // Eisblau / Diamantscheitel
    };

    private static final Map<UUID, LabelEntry> CACHE = new HashMap<>();
    private static JsonObject fontMetrics;

    private static final String FONT_ATLAS_PATH = "/assets/stones_visuals/textures/font/runic_font3.png";
    private static final String GLOW_ATLAS_PATH = "/assets/stones_visuals/textures/font/runic_font3_glow.png";
    private static final String METRICS_JSON_PATH = "/assets/stones_visuals/textures/font/runic_font3.json";

    private static final String[] PREFIXES = {"Vas", "In", "Kal", "Rel", "Uus"};
    private static final String[] ROOTS = {"Flam", "Nox", "Corp", "Wis", "Ylem"};
    private static final String[] SUFFIXES = {"Sanct", "Lor", "Xen", "Jux", "Tym"};

    private static final int CANVAS_SIZE = 512;
    private static final float CENTER = CANVAS_SIZE / 2.0f;
    private static final float RADIUS_INNER = 162.0f;
    private static final float RADIUS_OUTER = 224.0f;
    private static final float RADIUS_TEXT = 188.0f;

    public static void init() {
        try (InputStream is = ClientDynamicLabelHandler.class.getResourceAsStream(METRICS_JSON_PATH)) {
            if (is != null) {
                fontMetrics = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
                LOGGER.info("[StonesVisuals] Runische Font-Metadaten erfolgreich geladen.");
            } else {
                try (InputStream isFallback = ClientDynamicLabelHandler.class.getResourceAsStream("/assets/stones/textures/font/runic_font3.json")) {
                    if (isFallback != null) {
                        fontMetrics = JsonParser.parseReader(new InputStreamReader(isFallback)).getAsJsonObject();
                        LOGGER.info("[StonesVisuals] Runische Font-Metadaten via Fallback geladen.");
                    } else {
                        LOGGER.error("[StonesVisuals] Font-Metadaten weder unter stones_visuals noch stones gefunden!");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[StonesVisuals] Kritischer Fehler bei Font-Initialisierung!", e);
        }
    }

    public static LabelEntry getOrGenerate(UUID shrineId) {
        return CACHE.computeIfAbsent(shrineId, id -> bakeCoin(id, generateName(id)));
    }

    private static String generateName(UUID id) {
        Random rng = new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits());
        return PREFIXES[rng.nextInt(PREFIXES.length)] + " " +
               ROOTS[rng.nextInt(ROOTS.length)] + " " +
               SUFFIXES[rng.nextInt(SUFFIXES.length)];
    }

    private static NativeImage bufferedToNative(BufferedImage buffered) {
        NativeImage nativeImg = new NativeImage(NativeImage.Format.RGBA, buffered.getWidth(), buffered.getHeight(), false);
        for (int y = 0; y < buffered.getHeight(); y++) {
            for (int x = 0; x < buffered.getWidth(); x++) {
                int argb = buffered.getRGB(x, y);
                int a = (argb >> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                // Little-Endian ABGR Packing fuer Minecraft NativeImage
                nativeImg.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        return nativeImg;
    }

    private static LabelEntry bakeCoin(UUID id, String text) {
        if (fontMetrics == null) {
            init();
            if (fontMetrics == null) return null;
        }

        try {
            InputStream atlasStream = ClientDynamicLabelHandler.class.getResourceAsStream(FONT_ATLAS_PATH);
            InputStream glowStream = ClientDynamicLabelHandler.class.getResourceAsStream(GLOW_ATLAS_PATH);
            if (atlasStream == null) atlasStream = ClientDynamicLabelHandler.class.getResourceAsStream("/assets/stones/textures/font/runic_font3.png");
            if (glowStream == null) glowStream = ClientDynamicLabelHandler.class.getResourceAsStream("/assets/stones/textures/font/runic_font3_glow.png");

            if (atlasStream == null || glowStream == null) return null;

            BufferedImage atlasBuffered = ImageIO.read(atlasStream);
            BufferedImage glowBuffered = ImageIO.read(glowStream);
            if (atlasBuffered == null || glowBuffered == null) return null;

            NativeImage atlas = bufferedToNative(atlasBuffered);
            NativeImage glow = bufferedToNative(glowBuffered);
            NativeImage target = new NativeImage(NativeImage.Format.RGBA, CANVAS_SIZE, CANVAS_SIZE, true);

            long seed = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
            Random rng = new Random(seed ^ 0x9E3779B97F4A7C15L);

            int outerColor = PALETTE[rng.nextInt(PALETTE.length)];
            int innerColor = PALETTE[rng.nextInt(PALETTE.length)];
            int glyphColor = PALETTE[rng.nextInt(PALETTE.length)];
            int diamondColor = PALETTE[rng.nextInt(PALETTE.length)];

            // Schattenfarbe darf nicht blind aus der Palette gezogen werden, um Schlamm/Unlesbarkeit bei hellen Tönen zu verhindern
            int shadowColor = (((int)(((glyphColor >> 16) & 0xFF) * 0.15f)) << 16) |
                              (((int)(((glyphColor >> 8) & 0xFF) * 0.15f)) << 8) |
                              ((int)((glyphColor & 0xFF) * 0.15f));

            CoinPalette palette = new CoinPalette(outerColor, innerColor, glyphColor, shadowColor, diamondColor);

            try {
                // Konzentrische Rillen (Münzfassung) um den Runenreif mit gewählter Palette
                drawConcentricRing(target, CENTER, CENTER, RADIUS_OUTER, 1.2f, palette.outerRing(), 180);
                drawConcentricRing(target, CENTER, CENTER, RADIUS_OUTER - 4.0f, 0.8f, palette.innerRing(), 130);
                drawConcentricRing(target, CENTER, CENTER, RADIUS_INNER + 4.0f, 0.8f, palette.innerRing(), 130);
                drawConcentricRing(target, CENTER, CENTER, RADIUS_INNER, 1.2f, palette.outerRing(), 180);

                // Drei Wiederholungen des Schrein-Namens getrennt durch Raute: NAME ◆ NAME ◆ NAME ◆
                String upperText = text.toUpperCase();
                String token = upperText + " \u25C6 "; 
                String fullSequence = token + token + token;

                JsonObject glyphs = fontMetrics.getAsJsonObject("glyphs");
                int glyphCount = fullSequence.length();
                double angleStep = (2.0 * Math.PI) / (double) glyphCount;

                for (int i = 0; i < glyphCount; i++) {
                    char c = fullSequence.charAt(i);
                    double angle = i * angleStep - (Math.PI / 2.0); 

                    if (c == ' ') continue;

                    if (c == '\u25C6') {
                        drawDiamondSeparator(target, angle, RADIUS_TEXT, palette.diamondRgb());
                        continue;
                    }

                    String s = String.valueOf(c);
                    if (!glyphs.has(s)) continue;

                    JsonObject g = glyphs.getAsJsonObject(s);
                    int sx = g.get("x").getAsInt();
                    int sy = g.get("y").getAsInt();
                    int sw = g.get("w").getAsInt();
                    int sh = g.get("h").getAsInt();

                    renderRotatedGlyph(target, atlas, glow, sx, sy, sw, sh, angle, RADIUS_TEXT, palette);
                }

                String labelName = "shrine_coin_" + id.toString().toLowerCase().replace("-", "_");
                ResourceLocation res = Minecraft.getInstance().getTextureManager().register(labelName, new DynamicTexture(target));
                return new LabelEntry(res, CANVAS_SIZE);

            } finally {
                atlas.close();
                glow.close();
            }
        } catch (Exception e) {
            LOGGER.error("[StonesVisuals] Fehler beim Backen des Münz-Labels für '{}'", text, e);
            return null;
        }
    }

    private static void renderRotatedGlyph(NativeImage target, NativeImage atlas, NativeImage glow, 
                                           int sx, int sy, int sw, int sh, double angle, float radius,
                                           CoinPalette palette) {
        double cosA = Math.cos(angle);
        double sinA = Math.sin(angle);
        double tanX = -sinA;
        double tanY = cosA;

        float glyphDrawScale = 0.28f;
        int renderHalfW = (int) (sw * glyphDrawScale * 0.5f);
        int renderHalfH = (int) (sh * glyphDrawScale * 0.5f);

        double glyphCenterX = CENTER + cosA * radius;
        double glyphCenterY = CENTER + sinA * radius;

        int sR = (palette.shadowRgb() >> 16) & 0xFF;
        int sG = (palette.shadowRgb() >> 8) & 0xFF;
        int sB = palette.shadowRgb() & 0xFF;

        int gR = (palette.glyphRgb() >> 16) & 0xFF;
        int gG = (palette.glyphRgb() >> 8) & 0xFF;
        int gB = palette.glyphRgb() & 0xFF;

        for (int dy = -renderHalfH; dy <= renderHalfH; dy++) {
            for (int dx = -renderHalfW; dx <= renderHalfW; dx++) {
                int srcX = sx + (int) ((dx + renderHalfW) / (renderHalfW * 2.0f) * sw);
                int srcY = sy + (int) ((dy + renderHalfH) / (renderHalfH * 2.0f) * sh);

                if (srcX >= atlas.getWidth() || srcY >= atlas.getHeight()) continue;

                int basePixel = atlas.getPixelRGBA(srcX, srcY);
                int baseA = (basePixel >> 24) & 0xFF;
                if (baseA <= 15) continue;

                int baseR = basePixel & 0xFF;
                int baseG = (basePixel >> 8) & 0xFF;
                int baseB = (basePixel >> 16) & 0xFF;
                float luma = (0.2126f * baseR + 0.7152f * baseG + 0.0722f * baseB) / 255.0f;

                int glowA = 0;
                if (srcX < glow.getWidth() && srcY < glow.getHeight()) {
                    glowA = (glow.getPixelRGBA(srcX, srcY) >> 24) & 0xFF;
                }

                int targetX = (int) Math.round(glyphCenterX + (dx * tanX) + (dy * cosA));
                int targetY = (int) Math.round(glyphCenterY + (dx * tanY) + (dy * sinA));

                if (targetX < 0 || targetX >= CANVAS_SIZE || targetY < 0 || targetY >= CANVAS_SIZE) continue;

                int outR, outG, outB;
                if (luma < 0.42f) {
                    // Erhält den plastischen Atlas-Schlagschatten mit dem abgetönten Schatten der Palette
                    outR = sR; outG = sG; outB = sB;
                } else {
                    // Vordergrundfarbe der Palette mit Helligkeitserhalt und sanftem Glow-Boost
                    outR = Math.min(255, (int) (gR * luma + (glowA > 20 ? 15 : 0)));
                    outG = Math.min(255, (int) (gG * luma + (glowA > 20 ? 15 : 0)));
                    outB = Math.min(255, (int) (gB * luma + (glowA > 20 ? 15 : 0)));
                }

                target.setPixelRGBA(targetX, targetY, (baseA << 24) | (outB << 16) | (outG << 8) | outR);
            }
        }
    }

    private static void drawDiamondSeparator(NativeImage target, double angle, float radius, int diamondRgb) {
        double cx = CENTER + Math.cos(angle) * radius;
        double cy = CENTER + Math.sin(angle) * radius;
        int size = 5;

        int dR = (diamondRgb >> 16) & 0xFF;
        int dG = (diamondRgb >> 8) & 0xFF;
        int dB = diamondRgb & 0xFF;
        int packedAbgr = (230 << 24) | (dB << 16) | (dG << 8) | dR;

        for (int dy = -size; dy <= size; dy++) {
            for (int dx = -size; dx <= size; dx++) {
                if (Math.abs(dx) + Math.abs(dy) <= size) {
                    int px = (int) Math.round(cx + dx);
                    int py = (int) Math.round(cy + dy);
                    if (px >= 0 && px < CANVAS_SIZE && py >= 0 && py < CANVAS_SIZE) {
                        target.setPixelRGBA(px, py, packedAbgr);
                    }
                }
            }
        }
    }

    private static void drawConcentricRing(NativeImage target, float cx, float cy, float radius, float thickness, int colorRgb, int alpha) {
        int r = (colorRgb >> 16) & 0xFF;
        int g = (colorRgb >> 8) & 0xFF;
        int b = colorRgb & 0xFF;
        int packedAbgr = (alpha << 24) | (b << 16) | (g << 8) | r;

        double step = 1.0 / (radius * 2.0 * Math.PI);
        for (double t = 0; t < Math.PI * 2; t += step * 0.5) {
            for (float d = -thickness; d <= thickness; d += 0.5f) {
                int px = (int) Math.round(cx + Math.cos(t) * (radius + d));
                int py = (int) Math.round(cy + Math.sin(t) * (radius + d));
                if (px >= 0 && px < CANVAS_SIZE && py >= 0 && py < CANVAS_SIZE) {
                    target.setPixelRGBA(px, py, packedAbgr);
                }
            }
        }
    }

    public static void clearCache() {
        CACHE.clear();
    }
}