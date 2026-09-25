package net.stones.client.cache;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.stones.StonesMod;
import net.stones.network.C2SRequestTexturePacket;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ClientTextureCache {

    private static final ResourceLocation FALLBACK = new ResourceLocation("minecraft", "textures/particle/glint.png");
    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = Collections.synchronizedSet(new HashSet<>());

    public static ResourceLocation getOrRequest(String textureId) {
        if (textureId == null || textureId.isEmpty()) return FALLBACK;

        if (CACHE.containsKey(textureId)) {
            return CACHE.get(textureId);
        }

        if (!PENDING.contains(textureId)) {
            PENDING.add(textureId);
            StonesMod.PACKET_HANDLER.sendToServer(new C2SRequestTexturePacket(textureId));
        }

        return FALLBACK;
    }

    public static void registerTexture(String textureId, String base64Data) {
        Minecraft.getInstance().execute(() -> {
            try {
                String cleanBase64 = base64Data.contains("base64,") 
                    ? base64Data.substring(base64Data.indexOf("base64,") + 7).trim() 
                    : base64Data.trim();

                byte[] bytes = Base64.getDecoder().decode(cleanBase64);
                NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                
                DynamicTexture dynamicTexture = new DynamicTexture(image);
                ResourceLocation location = new ResourceLocation(StonesMod.MODID, "dynamic_tex_" + Math.abs(textureId.hashCode()));
                
                Minecraft.getInstance().getTextureManager().register(location, dynamicTexture);
                
                CACHE.put(textureId, location);
                PENDING.remove(textureId);
            } catch (Exception e) {
                StonesMod.LOGGER.error("Fehler beim Laden der dynamischen Base64-Textur {}", textureId, e);
                PENDING.remove(textureId);
            }
        });
    }
}