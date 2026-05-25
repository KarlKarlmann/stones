package net.stones.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
// WICHTIG: Keine direkten Imports von Client-Klassen (ClientShrineCache, ClientAdvancementHelper) hier!
// Das würde den Server crashen.

import java.util.List;
import java.util.function.Supplier;

/**
 * OPTIMIERTES PAKET:
 * Überträgt KEINE Textkomponenten mehr. Nur das neue Level.
 * Der Client berechnet die Boni lokal aus seinem Mirror.
 */
public class PacketSyncLevelUpInfo {
    private final int level;

    public PacketSyncLevelUpInfo(int level) {
        this.level = level;
    }

    public PacketSyncLevelUpInfo(FriendlyByteBuf buf) {
        this.level = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(level);
    }

    public static void handle(PacketSyncLevelUpInfo msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Wir nutzen DistExecutor, um sicherzustellen, dass dieser Codeblock 
            // NUR auf dem Client ausgeführt und geladen wird.
            // Dies verhindert, dass der Server versucht, Client-Klassen zu laden.
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handleLevelUp(msg.level));
        });
        ctx.get().setPacketHandled(true);
    }

    // Interne statische Klasse, die nur vom Client-Classloader berührt wird
    // Dies fungiert als "Firewall" gegen Server-Crashes.
    private static class ClientHandler {
        public static void handleLevelUp(int level) {
            // Hier sind die Imports sicher, da diese Methode nur auf dem Client aufgerufen wird
            List<Component> localBonuses = net.stones.logic.RuneCalculator.calculateBonusesLocally(
                net.stones.client.cache.ClientShrineCache.INVENTORY, 
                net.stones.client.cache.ClientShrineCache.LAYOUT, 
                level
            );
			net.stones.features.ActionSystem.refreshCalculatedActions();           
            net.stones.client.util.ClientAdvancementHelper.showLevelUpToast(level, localBonuses);
        }
    }
}