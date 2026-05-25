package net.stones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncCombo {
    public final String comboId;
    public final int count;
    public final int maxCount;
    public final String texture;
    public final float size;
    public final float radius;
    public final float speed;
    public final float r, g, b, a;
    public final int timeoutTicks;

    public PacketSyncCombo(String comboId, int count, int maxCount, String texture, float size, float radius, float speed, float r, float g, float b, float a, int timeoutTicks) {
        this.comboId = comboId;
        this.count = count;
        this.maxCount = maxCount;
        this.texture = texture;
        this.size = size;
        this.radius = radius;
        this.speed = speed;
        this.r = r; this.g = g; this.b = b; this.a = a;
        this.timeoutTicks = timeoutTicks;
    }

    public PacketSyncCombo(FriendlyByteBuf buf) {
        this.comboId = buf.readUtf();
        this.count = buf.readInt();
        this.maxCount = buf.readInt();
        this.texture = buf.readUtf();
        this.size = buf.readFloat();
        this.radius = buf.readFloat();
        this.speed = buf.readFloat();
        this.r = buf.readFloat();
        this.g = buf.readFloat();
        this.b = buf.readFloat();
        this.a = buf.readFloat();
        this.timeoutTicks = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(comboId);
        buf.writeInt(count);
        buf.writeInt(maxCount);
        buf.writeUtf(texture);
        buf.writeFloat(size);
        buf.writeFloat(radius);
        buf.writeFloat(speed);
        buf.writeFloat(r);
        buf.writeFloat(g);
        buf.writeFloat(b);
        buf.writeFloat(a);
        buf.writeInt(timeoutTicks);
    }

    public static void handle(PacketSyncCombo msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Isolierter Client-Code Aufruf (Firewall gegen Server-Crashes)
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handlePacket(msg));
        });
        ctx.get().setPacketHandled(true);
    }

    private static class ClientHandler {
        public static void handlePacket(PacketSyncCombo msg) {
            net.stones.client.renderer.combo.ClientComboRenderer.updateCombo(msg);
        }
    }
}