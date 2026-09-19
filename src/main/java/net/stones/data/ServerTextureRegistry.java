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
}