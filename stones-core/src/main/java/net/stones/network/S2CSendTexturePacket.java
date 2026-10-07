package net.stones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.stones.client.cache.ClientTextureCache;

import java.util.function.Supplier;

public class S2CSendTexturePacket {
    private final String textureId;
    // Rohe PNG-Bytes statt Base64-String: Beseitigt Dropper-Heuristiken und spart 33% Bandbreite
    private final byte[] textureBytes;

    public S2CSendTexturePacket(String textureId, byte[] textureBytes) {
        this.textureId = textureId;
        this.textureBytes = textureBytes;
    }

    public S2CSendTexturePacket(FriendlyByteBuf buf) {
        this.textureId = buf.readUtf(256);
        this.textureBytes = buf.readByteArray();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.textureId, 256);
        buf.writeByteArray(this.textureBytes);
    }

    public static void handle(S2CSendTexturePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> 
                ClientTextureCache.registerTexture(msg.textureId, msg.textureBytes)
            );
        });
        ctx.setPacketHandled(true);
    }
}