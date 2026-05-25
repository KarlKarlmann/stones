package net.stones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.stones.data.GlobalLeaderboardData;
import java.util.function.Supplier;

/**
 * C2S Paket: Aktualisiert das Epitaph (die letzten Worte) des Spielers für den letzten Run.
 */
public class PacketUpdateEpitaph {
    private final String text;
    private final int score;

    public PacketUpdateEpitaph(String text, int score) {
        this.text = text;
        this.score = score;
    }

    public PacketUpdateEpitaph(FriendlyByteBuf buf) {
        this.text = buf.readUtf(256);
        this.score = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeUtf(this.text);
        buf.writeInt(this.score);
    }

    public static void handle(PacketUpdateEpitaph msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                // Wir säubern den Text von Minecraft-Formatierungscodes (§), damit Spieler keine bunten/blinkenden Leaderboard-Einträge erzwingen können
                String cleanText = msg.text.replaceAll("§[0-9a-fk-or]", "").trim();
                if (cleanText.isEmpty()) {
                    cleanText = "Nahm ein stummes Ende";
                }
                
                // Begrenzung auf 45 Zeichen
                if (cleanText.length() > 45) {
                    cleanText = cleanText.substring(0, 42) + "...";
                }

                // Aktualisiere das Leaderboard auf dem Server
                GlobalLeaderboardData data = GlobalLeaderboardData.get(player.serverLevel());
                data.updateLastRunReason(player.getName().getString(), msg.score, cleanText);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}