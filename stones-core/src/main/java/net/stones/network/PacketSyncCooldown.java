package net.stones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncCooldown {
    public final String runeId;
    public final long endTick;

    public PacketSyncCooldown(String runeId, long endTick) {
        this.runeId = runeId;
        this.endTick = endTick;
    }

    public PacketSyncCooldown(FriendlyByteBuf buf) {
        this.runeId = buf.readUtf();
        this.endTick = buf.readLong();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(runeId);
        buf.writeLong(endTick);
    }

    public static void handle(PacketSyncCooldown msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Isolierter Client-Code Aufruf
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handlePacket(msg));
        });
        ctx.get().setPacketHandled(true);
    }

    // Interne Klasse, die nur vom Client geladen wird
    private static class ClientHandler {
        public static void handlePacket(PacketSyncCooldown msg) {
            net.stones.features.ActionSystem.CLIENT_COOLDOWNS.put(msg.runeId, msg.endTick);
        }
    }
}