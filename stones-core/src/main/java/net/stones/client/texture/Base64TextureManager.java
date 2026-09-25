package net.stones.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Base64TextureManager {

    private static final Map<String, ResourceLocation> CACHE = new ConcurrentHashMap<>();
    private static final ResourceLocation FALLBACK = new ResourceLocation("minecraft", "textures/particle/glint.png");

    public static ResourceLocation getOrCreateOrbTexture(String base64Data) {
        if (base64Data == null || base64Data.isBlank()) return FALLBACK;

        String cleanBase64 = base64Data.replaceFirst("^data:image/[^;]+;base64,", "");
        String hash = DigestUtils.md5Hex(cleanBase64);

        return CACHE.computeIfAbsent(hash, h -> {
            try {
                byte[] bytes = Base64.getDecoder().decode(cleanBase64);
                ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
                NativeImage image = NativeImage.read(stream);
                DynamicTexture dynamicTexture = new DynamicTexture(image);

                ResourceLocation loc = new ResourceLocation("stones", "dynamic/orb_" + h);
                Minecraft.getInstance().execute(() -> {
                    Minecraft.getInstance().getTextureManager().register(loc, dynamicTexture);
                });
                return loc;
            } catch (Exception e) {
                return FALLBACK;
            }
        });
    }
}