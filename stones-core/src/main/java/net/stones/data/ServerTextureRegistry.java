package net.stones.data;

import net.stones.StonesMod;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerTextureRegistry {
    // Cache speichert fertige Binärdaten im RAM, um redundantes Base64-Dekodieren zu verhindern
    private static final Map<String, byte[]> TEXTURES = new ConcurrentHashMap<>();

    public static void register(String textureId, byte[] data) {
        if (textureId != null && data != null) {
            TEXTURES.put(textureId, data);
        }
    }

    public static void registerBase64(String textureId, String base64Data) {
        if (textureId == null || base64Data == null || base64Data.isBlank()) return;
        try {
            String cleanBase64 = base64Data.contains("base64,") 
                ? base64Data.substring(base64Data.indexOf("base64,") + 7).trim() 
                : base64Data.trim();
            byte[] decoded = Base64.getDecoder().decode(cleanBase64);
            register(textureId, decoded);
        } catch (Exception e) {
            StonesMod.LOGGER.error("[Stones] Fehler beim Dekodieren der Textur '{}': ", textureId, e);
        }
    }

    public static byte[] getTextureBytes(String textureId) {
        return TEXTURES.get(textureId);
    }

    public static String processTextureForNetwork(String rawTexture) {
        if (rawTexture == null || rawTexture.isBlank()) {
            return "minecraft:textures/particle/glint.png";
        }
        
        // Wandelt eingehende Base64-Daten sofort serverseitig in ein byte[] um und vergibt eine Cache-ID
        if (rawTexture.startsWith("data:image/") || rawTexture.length() > 100) {
            String textureId = "dynamic:" + Math.abs(rawTexture.hashCode());
            if (!TEXTURES.containsKey(textureId)) {
                registerBase64(textureId, rawTexture);
            }
            return textureId;
        }
        
        return rawTexture;
    }
}