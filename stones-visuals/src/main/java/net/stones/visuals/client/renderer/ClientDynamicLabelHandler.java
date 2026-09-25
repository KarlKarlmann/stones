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
import java.util.*;

public class ClientDynamicLabelHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<UUID, ResourceLocation> CACHE = new HashMap<>();
    
    private static JsonObject fontMetrics;
    
    // Direkte Classpath-Pfade passend zu deinem Ordner: /assets/stones-visuals/textures/font/
    private static final String FONT_ATLAS_PATH = "/assets/stones_visuals/textures/font/runic_font3.png";
    private static final String GLOW_ATLAS_PATH = "/assets/stones_visuals/textures/font/runic_font3_glow.png";
    private static final String METRICS_JSON_PATH = "/assets/stones_visuals/textures/font/runic_font3.json";
    
    private static final String[] PREFIXES = {"Vas", "In", "Kal", "Rel", "Uus"};
    private static final String[] ROOTS = {"Flam", "Nox", "Corp", "Wis", "Ylem"};
    private static final String[] SUFFIXES = {"Sanct", "Lor", "Xen", "Jux", "Tym"};
    
    public static void init() {
        // Lädt die JSON direkt über ClassLoader - 100% unabhängig vom ResourceManager-Timing!
        try (InputStream is = ClientDynamicLabelHandler.class.getResourceAsStream(METRICS_JSON_PATH)) {
            if (is != null) {
                fontMetrics = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
                LOGGER.info("[StonesVisuals] Runische Font-Metadaten erfolgreich geladen.");
            } else {
                // Fallback falls Namespace 'stones' genutzt wird
                try (InputStream isFallback = ClientDynamicLabelHandler.class.getResourceAsStream("/assets/stones/textures/font/runic_font3.json")) {
                    if (isFallback != null) {
                        fontMetrics = JsonParser.parseReader(new InputStreamReader(isFallback)).getAsJsonObject();
                        LOGGER.info("[StonesVisuals] Runische Font-Metadaten via Fallback geladen.");
                    } else {
                        LOGGER.error("[StonesVisuals] Font-Metadaten weder unter stones-visuals noch stones gefunden!");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("[StonesVisuals] Kritischer Fehler bei Font-Initialisierung!", e);
        }
    }
    
    public static ResourceLocation getOrGenerate(UUID shrineId) {
        return CACHE.computeIfAbsent(shrineId, id -> bake(id, generateName(id)));
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
                
                int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                nativeImg.setPixelRGBA(x, y, abgr);
            }
        }
        return nativeImg;
    }
    
    private static ResourceLocation bake(UUID id, String text) {
        if (fontMetrics == null) {
            init();
            if (fontMetrics == null) {
                LOGGER.error("[StonesVisuals] Konnte Font-Metadaten nicht laden!");
                return null;
            }
        }
        
        try {
            int cellSize = fontMetrics.get("cell_size").getAsInt();
            JsonObject glyphs = fontMetrics.getAsJsonObject("glyphs");
            
            String upperText = text.toUpperCase();
            int totalWidth = 0;
            for (char c : upperText.toCharArray()) {
                if (c == ' ') {
                    totalWidth += cellSize / 2;
                } else if (glyphs.has(String.valueOf(c))) {
                    totalWidth += cellSize;
                }
            }
            
            InputStream atlasStream = ClientDynamicLabelHandler.class.getResourceAsStream(FONT_ATLAS_PATH);
            InputStream glowStream = ClientDynamicLabelHandler.class.getResourceAsStream(GLOW_ATLAS_PATH);
            
            // Fallback auf stones-Pfad falls nötig
            if (atlasStream == null) atlasStream = ClientDynamicLabelHandler.class.getResourceAsStream("/assets/stones/textures/font/runic_font3.png");
            if (glowStream == null) glowStream = ClientDynamicLabelHandler.class.getResourceAsStream("/assets/stones/textures/font/runic_font3_glow.png");
            
            if (atlasStream == null || glowStream == null) {
                LOGGER.error("[StonesVisuals] Konnte Textur-Streams nicht öffnen!");
                return null;
            }
            
            BufferedImage atlasBuffered = ImageIO.read(atlasStream);
            BufferedImage glowBuffered = ImageIO.read(glowStream);
            
            if (atlasBuffered == null || glowBuffered == null) {
                LOGGER.error("[StonesVisuals] ImageIO konnte PNG nicht lesen!");
                return null;
            }
            
            NativeImage atlas = bufferedToNative(atlasBuffered);
            NativeImage glow = bufferedToNative(glowBuffered);
            NativeImage target = new NativeImage(NativeImage.Format.RGBA, totalWidth, cellSize, true);
            
            try {
                if (atlas.getHeight() < 512) {
                    LOGGER.error("[StonesVisuals] Atlas hat nur {}px Höhe!", atlas.getHeight());
                    return null;
                }
                
                int currentX = 0;
                for (char c : upperText.toCharArray()) {
                    String s = String.valueOf(c);
                    
                    if (c == ' ') {
                        currentX += cellSize / 2;
                        continue;
                    }
                    
                    if (!glyphs.has(s)) continue;
                    
                    JsonObject g = glyphs.getAsJsonObject(s);
                    int sx = g.get("x").getAsInt();
                    int sy = g.get("y").getAsInt();
                    int w = g.get("w").getAsInt();
                    int h = g.get("h").getAsInt();
                    
                    if (sx + w > atlas.getWidth() || sy + h > atlas.getHeight()) {
                        currentX += cellSize;
                        continue;
                    }
                    
                    for (int py = 0; py < h; py++) {
                        for (int px = 0; px < w; px++) {
                            int pixel = atlas.getPixelRGBA(sx + px, sy + py);
                            target.setPixelRGBA(currentX + px, py, pixel);
                        }
                    }
                    
                    if (sx + w <= glow.getWidth() && sy + h <= glow.getHeight()) {
                        for (int py = 0; py < h; py++) {
                            for (int px = 0; px < w; px++) {
                                int glowPixel = glow.getPixelRGBA(sx + px, sy + py);
                                int glowAlpha = (glowPixel >> 24) & 0xFF;
                                
                                if (glowAlpha > 20) {
                                    int basePixel = target.getPixelRGBA(currentX + px, py);
                                    int baseAlpha = (basePixel >> 24) & 0xFF;
                                    int cyan = (baseAlpha << 24) | (0xCC << 16) | (0xFF << 8) | 0x00;
                                    target.setPixelRGBA(currentX + px, py, cyan);
                                }
                            }
                        }
                    }
                    
                    currentX += w;
                }
                
                String labelName = "shrine_label_" + id.toString().toLowerCase().replace("-", "_");
                DynamicTexture dynamicTexture = new DynamicTexture(target);
                return Minecraft.getInstance().getTextureManager().register(labelName, dynamicTexture);
                
            } finally {
                atlas.close();
                glow.close();
            }
            
        } catch (Exception e) {
            LOGGER.error("[StonesVisuals] Fehler beim Backen des Labels für '{}'", text, e);
            return null;
        }
    }
    
    public static void clearCache() {
        CACHE.clear();
    }
}