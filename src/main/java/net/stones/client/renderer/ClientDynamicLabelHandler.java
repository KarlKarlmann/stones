package net.stones.client.renderer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.stones.StonesMod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Handler für dynamische Runenschrein-Titel mit Glow-Effekt.
 * Forge 1.20.1 kompatibel.
 */
public class ClientDynamicLabelHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<UUID, ResourceLocation> CACHE = new HashMap<>();
    
    private static JsonObject fontMetrics;
    
    // Pfade zu den Assets
    private static final String FONT_ATLAS_PATH = "/assets/stones/textures/font/runic_font3.png";
    private static final String GLOW_ATLAS_PATH = "/assets/stones/textures/font/runic_font3_glow.png";
    private static final ResourceLocation JSON_METRICS = new ResourceLocation(StonesMod.MODID, "textures/font/runic_font3.json");
    
    // Namensgenerator-Komponenten
    private static final String[] PREFIXES = {"Vas", "In", "Kal", "Rel", "Uus"};
    private static final String[] ROOTS = {"Flam", "Nox", "Corp", "Wis", "Ylem"};
    private static final String[] SUFFIXES = {"Sanct", "Lor", "Xen", "Jux", "Tym"};
    
    /**
     * Initialisiert die Font-Metadaten aus der JSON.
     * Sollte beim Client-Setup aufgerufen werden.
     */
    public static void init() {
        try {
            Minecraft.getInstance().getResourceManager().getResource(JSON_METRICS).ifPresent(resource -> {
                try (InputStream is = resource.open()) {
                    fontMetrics = JsonParser.parseReader(new InputStreamReader(is)).getAsJsonObject();
                    LOGGER.info("[Stones] Runische Font-Metadaten erfolgreich geladen.");
                } catch (Exception e) {
                    LOGGER.error("[Stones] Fehler beim Lesen der Font-Metadaten!", e);
                }
            });
        } catch (Exception e) {
            LOGGER.error("[Stones] Kritischer Fehler bei Font-Initialisierung!", e);
        }
    }
    
    /**
     * Holt oder generiert eine Textur für einen Runenschrein.
     * 
     * @param shrineId Eindeutige UUID des Schreins
     * @return ResourceLocation der generierten Textur
     */
    public static ResourceLocation getOrGenerate(UUID shrineId) {
        return CACHE.computeIfAbsent(shrineId, id -> bake(id, generateName(id)));
    }
    
    /**
     * Generiert einen zufälligen aber deterministischen Namen basierend auf UUID.
     */
    private static String generateName(UUID id) {
        Random rng = new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits());
        return PREFIXES[rng.nextInt(PREFIXES.length)] + " " + 
               ROOTS[rng.nextInt(ROOTS.length)] + " " + 
               SUFFIXES[rng.nextInt(SUFFIXES.length)];
    }
    
    /**
     * Konvertiert BufferedImage zu NativeImage OHNE Minecraft's Auto-Konvertierung.
     */
    private static NativeImage bufferedToNative(BufferedImage buffered) {
        NativeImage nativeImg = new NativeImage(NativeImage.Format.RGBA, buffered.getWidth(), buffered.getHeight(), false);
        
        for (int y = 0; y < buffered.getHeight(); y++) {
            for (int x = 0; x < buffered.getWidth(); x++) {
                int argb = buffered.getRGB(x, y);
                
                // Konvertiere ARGB zu ABGR (NativeImage Format)
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
    
    /**
     * Backt eine dynamische Textur aus den Runen-Glyphen.
     * Lädt Texturen mit ImageIO um NativeImage.read() Bugs zu umgehen.
     */
    private static ResourceLocation bake(UUID id, String text) {
        if (fontMetrics == null) {
            init();
            if (fontMetrics == null) {
                LOGGER.error("[Stones] Konnte Font-Metadaten nicht laden!");
                return new ResourceLocation("missingno");
            }
        }
        
        try {
            int cellSize = fontMetrics.get("cell_size").getAsInt();
            JsonObject glyphs = fontMetrics.getAsJsonObject("glyphs");
            
            // Berechne Gesamtbreite
            String upperText = text.toUpperCase();
            int totalWidth = 0;
            for (char c : upperText.toCharArray()) {
                if (c == ' ') {
                    totalWidth += cellSize / 2;
                } else if (glyphs.has(String.valueOf(c))) {
                    totalWidth += cellSize;
                }
            }
            
            // Lade mit ImageIO statt NativeImage.read()
            InputStream atlasStream = ClientDynamicLabelHandler.class.getResourceAsStream(FONT_ATLAS_PATH);
            InputStream glowStream = ClientDynamicLabelHandler.class.getResourceAsStream(GLOW_ATLAS_PATH);
            
            if (atlasStream == null || glowStream == null) {
                LOGGER.error("[Stones] Konnte Textur-Streams nicht öffnen!");
                return new ResourceLocation("missingno");
            }
            
            // Lade mit Java ImageIO (umgeht Minecraft komplett)
            BufferedImage atlasBuffered = ImageIO.read(atlasStream);
            BufferedImage glowBuffered = ImageIO.read(glowStream);
            
            if (atlasBuffered == null || glowBuffered == null) {
                LOGGER.error("[Stones] ImageIO konnte PNG nicht lesen!");
                return new ResourceLocation("missingno");
            }
            
            // Konvertiere zu NativeImage
            NativeImage atlas = bufferedToNative(atlasBuffered);
            NativeImage glow = bufferedToNative(glowBuffered);
            NativeImage target = new NativeImage(NativeImage.Format.RGBA, totalWidth, cellSize, true);
            
            try {
                // Sicherheitscheck
                if (atlas.getHeight() < 512) {
                    LOGGER.error("[Stones] Atlas hat nur {}px Höhe! Erwartet mindestens 512px.", atlas.getHeight());
                    return new ResourceLocation("missingno");
                }
                
                // Rendere jeden Buchstaben
                int currentX = 0;
                for (char c : upperText.toCharArray()) {
                    String s = String.valueOf(c);
                    
                    if (c == ' ') {
                        currentX += cellSize / 2;
                        continue;
                    }
                    
                    if (!glyphs.has(s)) continue;
                    
                    // Hole Glyph-Koordinaten
                    JsonObject g = glyphs.getAsJsonObject(s);
                    int sx = g.get("x").getAsInt();
                    int sy = g.get("y").getAsInt();
                    int w = g.get("w").getAsInt();
                    int h = g.get("h").getAsInt();
                    
                    // Bounds-Check
                    if (sx + w > atlas.getWidth() || sy + h > atlas.getHeight()) {
                        LOGGER.warn("[Stones] Zeichen '{}' übersprungen - ungültige Koordinaten.", s);
                        currentX += cellSize;
                        continue;
                    }
                    
                    // Kopiere Base-Glyph MANUELL (copyRect hat einen Bug mit unterschiedlichen Bildgrößen!)
                    for (int py = 0; py < h; py++) {
                        for (int px = 0; px < w; px++) {
                            int pixel = atlas.getPixelRGBA(sx + px, sy + py);
                            target.setPixelRGBA(currentX + px, py, pixel);
                        }
                    }
                    
                    // Füge Glow-Layer hinzu
                    if (sx + w <= glow.getWidth() && sy + h <= glow.getHeight()) {
                        for (int py = 0; py < h; py++) {
                            for (int px = 0; px < w; px++) {
                                int glowPixel = glow.getPixelRGBA(sx + px, sy + py);
                                int glowAlpha = (glowPixel >> 24) & 0xFF;
                                
                                // Nur Pixel mit sichtbarem Glow
                                if (glowAlpha > 20) {
                                    int basePixel = target.getPixelRGBA(currentX + px, py);
                                    int baseAlpha = (basePixel >> 24) & 0xFF;
                                    
                                    // Mische Cyan-Glow (ABGR Format: Alpha, Blue, Green, Red)
                                    int cyan = (baseAlpha << 24) | (0xCC << 16) | (0xFF << 8) | 0x00;
                                    target.setPixelRGBA(currentX + px, py, cyan);
                                }
                            }
                        }
                    }
                    
                    currentX += w;
                }
                
                // Registriere als DynamicTexture
                String labelName = "shrine_label_" + id.toString().toLowerCase().replace("-", "_");
                DynamicTexture dynamicTexture = new DynamicTexture(target);
                return Minecraft.getInstance().getTextureManager().register(labelName, dynamicTexture);
                
            } finally {
                atlas.close();
                glow.close();
            }
            
        } catch (Exception e) {
            LOGGER.error("[Stones] Fehler beim Backen des Labels für '{}'", text, e);
            return new ResourceLocation("missingno");
        }
    }
    
    /**
     * Leert den Cache (z.B. beim Ressourcen-Reload).
     */
    public static void clearCache() {
        CACHE.clear();
    }
}