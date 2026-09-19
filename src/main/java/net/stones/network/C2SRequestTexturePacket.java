package net.stones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.stones.StonesMod;
import net.stones.data.ServerTextureRegistry;

import java.util.function.Supplier;

public class C2SRequestTexturePacket {
    private final String textureId;

    public C2SRequestTexturePacket(String textureId) {
        this.textureId = textureId;
    }

    public C2SRequestTexturePacket(FriendlyByteBuf buf) {
        this.textureId = buf.readUtf(256);
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.textureId, 256);
    }

    public static void handle(C2SRequestTexturePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            String base64 = ServerTextureRegistry.getTextureBase64(msg.textureId);
            if (base64 != null && !base64.isEmpty()) {
                StonesMod.PACKET_HANDLER.sendTo(
                    new S2CSendTexturePacket(msg.textureId, base64),
                    player.connection.connection,
                    NetworkDirection.PLAY_TO_CLIENT
                );
            }
        });
        ctx.setPacketHandled(true);
    }
}