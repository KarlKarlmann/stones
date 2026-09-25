package net.stones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.stones.StonesMod;
import net.stones.event.StonesActionDispatcher;

import java.util.function.Supplier;

/**
 * C2S-Paket für Actionbar-Skills.
 * Feuert bei Aktivierung ein Event an KubeJS / den Dispatcher.
 */
public class PacketPerformAction {
    private final String runeId;
    private final int slot;

    public PacketPerformAction(String id, int s) { 
        this.runeId = id; 
        this.slot = s; 
    }

    public PacketPerformAction(FriendlyByteBuf b) { 
        this.runeId = b.readUtf(); 
        this.slot = b.readInt(); 
    }

    public void encode(FriendlyByteBuf b) { 
        b.writeUtf(runeId); 
        b.writeInt(slot); 
    }

    public static void handle(PacketPerformAction msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) {
                // Delegierung an die Schnittstelle für Addons & Mixins
                validateAndExecute(p, msg.runeId, msg.slot);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * ZENTRALER EINSTIEGSPUNKT FÜR DIESES PAKET
     * Dient als stabiles Mixin-Ziel für Addons (wie stones_irons_bridge).
     */
    public static void validateAndExecute(ServerPlayer player, String runeId, int slot) {
        // Logging zur Laufzeit-Diagnose
        StonesMod.LOGGER.info("[PacketPerformAction] validateAndExecute aufgerufen | Spieler: {} | RuneID: '{}' | Slot: {}", 
            player.getScoreboardName(), runeId, slot);

        // Direkter Aufruf des Dispatchers für KubeJS!
        StonesActionDispatcher.dispatch(player, runeId, slot);
    }
}