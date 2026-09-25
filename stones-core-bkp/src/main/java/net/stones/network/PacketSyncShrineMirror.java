package net.stones.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkEvent;
import net.stones.data.ShrineInstance;
import net.stones.features.ActionSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Synchronisiert das Inventar und Layout eines Schreins zum Client.
 * Fungiert als "Mirror" (Spiegel), damit der Client Berechnungen lokal durchführen kann.
 * * SICHERHEIT: Diese Klasse liegt im network-Package und kann vom Server geladen werden.
 * Der Zugriff auf Client-Klassen (ClientShrineCache) ist via DistExecutor isoliert.
 */
public class PacketSyncShrineMirror {
    private final CompoundTag invTag;
    private final List<ShrineInstance.SlotConfig> layout;

    public PacketSyncShrineMirror(ItemStackHandler inv, List<ShrineInstance.SlotConfig> layout) {
        this.invTag = inv.serializeNBT();
        this.layout = layout;
    }

    public PacketSyncShrineMirror(FriendlyByteBuf buf) {
        this.invTag = buf.readNbt();
        this.layout = buf.readCollection(ArrayList::new, b -> new ShrineInstance.SlotConfig(
            b.readEnum(ShrineInstance.SlotType.class), b.readInt(), b.readInt()
        ));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(invTag);
        buf.writeCollection(layout, (b, c) -> {
            b.writeEnum(c.type); b.writeInt(c.requiredLevel); b.writeInt(c.inventoryIndex);
        });
    }

    public static void handle(PacketSyncShrineMirror msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Isolierter Client-Code Aufruf
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handlePacket(msg));
        });
        ctx.get().setPacketHandled(true);
    }

    // Interne Klasse, die nur vom Client geladen wird
    private static class ClientHandler {
        public static void handlePacket(PacketSyncShrineMirror msg) {
            // Hier dürfen wir ClientShrineCache sicher benutzen
            net.stones.client.cache.ClientShrineCache.INVENTORY.deserializeNBT(msg.invTag);
            net.stones.client.cache.ClientShrineCache.LAYOUT.clear();
            net.stones.client.cache.ClientShrineCache.LAYOUT.addAll(msg.layout);
            
            // Triggere Neuberechnung der verfügbaren Skills im ActionSystem
            ActionSystem.refreshCalculatedActions();
        }
    }
}