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

    // Liest native PNG-Bytes direkt in Mojangs NativeImage ohne Base64-Umweg
    public static void registerTexture(String textureId, byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) return;

        Minecraft.getInstance().execute(() -> {
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(imageBytes));
                DynamicTexture dynamicTexture = new DynamicTexture(image);
                ResourceLocation location = new ResourceLocation(StonesMod.MODID, "dynamic_tex_" + Math.abs(textureId.hashCode()));
                
                Minecraft.getInstance().getTextureManager().register(location, dynamicTexture);
                
                CACHE.put(textureId, location);
                PENDING.remove(textureId);
            } catch (Exception e) {
                StonesMod.LOGGER.error("[Stones] Fehler beim Laden der dynamischen Textur {}", textureId, e);
                PENDING.remove(textureId);
            }
        });
    }
}