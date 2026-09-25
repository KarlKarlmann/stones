package net.stones.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.stones.StonesMod;
import net.stones.enchantment.RuneEnchantment;
import net.stones.features.ActionSystem;

import java.util.function.Supplier;

/**
 * S2C Paket: Synchronisiert alle aktiven (erwachten) Runen-Daten vom Server zum Client.
 * Ermöglicht fehlerfreien Tooltip- und Actionbar-Abgleich im Dedicated Multiplayer.
 */
public class PacketSyncEnchantments {
    private final CompoundTag data;

    public PacketSyncEnchantments(CompoundTag data) {
        this.data = data;
    }

    public PacketSyncEnchantments(FriendlyByteBuf buf) {
        this.data = buf.readNbt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    /**
     * Erstellt das NBT-Paket mit allen serverseitig geladenen Runen-Werten.
     */
    public static PacketSyncEnchantments build() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();

        for (Enchantment enchantment : ForgeRegistries.ENCHANTMENTS.getValues()) {
            if (enchantment instanceof RuneEnchantment rune && rune.isAwake()) {
                ResourceLocation registryId = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
                if (registryId != null) {
                    CompoundTag runeTag = rune.serializeNBT();
                    runeTag.putString("registryId", registryId.toString());
                    list.add(runeTag);
                }
            }
        }
        root.put("runes", list);
        return new PacketSyncEnchantments(root);
    }

    public static void handle(PacketSyncEnchantments msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Client-isoliertes Ausführen zur Vermeidung von Dedicated Server Abstürzen
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handlePacket(msg.data));
        });
        ctx.get().setPacketHandled(true);
    }

    private static class ClientHandler {
        public static void handlePacket(CompoundTag data) {
            
            // ==========================================
            // DER SINGLEPLAYER GUARD (Behebt den Milestone Bug)
            // ==========================================
            // Im Singleplayer (Integrated Server) teilen sich Server und Client das RAM.
            // Der Server hat die JSONs inkl. der komplexen Aktionen bereits perfekt geladen.
            // Würde der Client hier fortfahren, würde er die echten Server-Aktionen mit 
            // den NBT-Dummys überschreiben. Daher ignorieren wir das Paket als Host!
            if (Minecraft.getInstance().isLocalServer()) {
                StonesMod.LOGGER.debug("[Stones] Singleplayer erkannt: Sync-Paket ignoriert, um Server-Logik zu schuetzen!");
                return;
            }

            // Alle clientseitigen Slots zuerst schlafen legen (Reset)
            ForgeRegistries.ENCHANTMENTS.getValues().stream()
                .filter(e -> e instanceof RuneEnchantment)
                .map(e -> (RuneEnchantment) e)
                .forEach(RuneEnchantment::sleep);

            ListTag list = data.getList("runes", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag runeTag = list.getCompound(i);
                ResourceLocation regId = new ResourceLocation(runeTag.getString("registryId"));
                Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(regId);
                
                if (enchantment instanceof RuneEnchantment rune) {
                    rune.deserializeNBT(runeTag);
                }
            }

            // Client-Cache für Actionbar und HUD-Berechnungen sofort aktualisieren
            ActionSystem.refreshCalculatedActions();
        }
    }
}