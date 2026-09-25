package net.stones.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.UUID;
import java.util.function.Supplier;

public class PacketSyncPlayerShrine {
    public final UUID shrineId;
    public final GlobalPos shrinePos;

    // Konstruktor Server-Side
    public PacketSyncPlayerShrine(UUID id, GlobalPos pos) {
        this.shrineId = id;
        this.shrinePos = pos;
    }

    // Decoder
    public PacketSyncPlayerShrine(FriendlyByteBuf buf) {
        if (buf.readBoolean()) {
            this.shrineId = buf.readUUID();
            BlockPos bp = buf.readBlockPos();
            ResourceKey<Level> dim = buf.readResourceKey(Registries.DIMENSION);
            this.shrinePos = GlobalPos.of(dim, bp);
        } else {
            this.shrineId = null;
            this.shrinePos = null;
        }
    }

    // Encoder
    public void toBytes(FriendlyByteBuf buf) {
        if (shrineId != null && shrinePos != null) {
            buf.writeBoolean(true);
            buf.writeUUID(shrineId);
            buf.writeBlockPos(shrinePos.pos());
            buf.writeResourceKey(shrinePos.dimension());
        } else {
            buf.writeBoolean(false); // Kein Link -> Reset
        }
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            // Isolierter Client-Code Aufruf (Firewall gegen Server-Crashes)
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handlePacket(this));
        });
        context.setPacketHandled(true);
        return true;
    }

    // Interne Klasse, die nur vom Client geladen wird
    private static class ClientHandler {
        public static void handlePacket(PacketSyncPlayerShrine msg) {
            net.minecraft.world.entity.player.Player player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) {
                player.getCapability(net.stones.cap.PlayerShrineCapProvider.SHRINE_LINK).ifPresent(cap -> {
                    cap.setLinkedShrine(msg.shrineId, msg.shrinePos);
                });
            }
        }
    }
}