package net.stones.data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServerTextureRegistry {
    private static final Map<String, String> TEXTURES = new ConcurrentHashMap<>();

    public static void register(String textureId, String base64Data) {
        TEXTURES.put(textureId, base64Data);
    }

    public static String getTextureBase64(String textureId) {
        return TEXTURES.get(textureId);
    }

    public static String processTextureForNetwork(String rawTexture) {
        if (rawTexture == null || rawTexture.isBlank()) {
            return "minecraft:textures/particle/glint.png";
        }
        
        // Sobald es Base64 ist, wird es im Server registriert und auf eine ID gekürzt!
        if (rawTexture.startsWith("data:image/") || rawTexture.length() > 100) {
            String textureId = "dynamic:" + Math.abs(rawTexture.hashCode());
            register(textureId, rawTexture);
            return textureId;
        }
        
        return rawTexture;
    }
}