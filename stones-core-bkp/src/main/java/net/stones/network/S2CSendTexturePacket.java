package net.stones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.stones.client.cache.ClientTextureCache;

import java.util.function.Supplier;

public class S2CSendTexturePacket {
    private final String textureId;
    private final String base64Data;

    public S2CSendTexturePacket(String textureId, String base64Data) {
        this.textureId = textureId;
        this.base64Data = base64Data;
    }

    public S2CSendTexturePacket(FriendlyByteBuf buf) {
        this.textureId = buf.readUtf(256);
        this.base64Data = buf.readUtf(32767);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.textureId, 256);
        buf.writeUtf(this.base64Data, 32767);
    }

    public static void handle(S2CSendTexturePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> 
                ClientTextureCache.registerTexture(msg.textureId, msg.base64Data)
            );
        });
        ctx.setPacketHandled(true);
    }
}