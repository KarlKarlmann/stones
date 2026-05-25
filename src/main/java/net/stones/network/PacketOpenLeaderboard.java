package net.stones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.stones.data.GlobalLeaderboardData;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketOpenLeaderboard {
    public final List<Integer> personalScores;
    public final List<GlobalLeaderboardData.LeaderboardEntry> globalEntries;

    public PacketOpenLeaderboard(List<Integer> personal, List<GlobalLeaderboardData.LeaderboardEntry> global) {
        this.personalScores = personal;
        this.globalEntries = global;
    }

    public PacketOpenLeaderboard(FriendlyByteBuf buf) {
        this.personalScores = buf.readCollection(ArrayList::new, FriendlyByteBuf::readInt);
        this.globalEntries = buf.readCollection(ArrayList::new, b -> 
            new GlobalLeaderboardData.LeaderboardEntry(b.readUtf(), b.readInt(), b.readUtf()));
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeCollection(this.personalScores, FriendlyByteBuf::writeInt);
        buf.writeCollection(this.globalEntries, (b, e) -> {
            b.writeUtf(e.name());
            b.writeInt(e.score());
            b.writeUtf(e.deathReason());
        });
    }

    public static void handle(PacketOpenLeaderboard msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Isolierter Client-Code Aufruf (Firewall gegen Server-Crashes)
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handlePacket(msg));
        });
        ctx.get().setPacketHandled(true);
    }

    // Interne Klasse, die nur vom Client geladen wird
    private static class ClientHandler {
        public static void handlePacket(PacketOpenLeaderboard msg) {
            net.minecraft.client.Minecraft.getInstance().setScreen(
                new net.stones.client.gui.ResonanceLeaderboardScreen(msg.personalScores, msg.globalEntries)
            );
        }
    }
}