package net.stones.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler; // NEU: Import für den Cast
import net.minecraftforge.network.PacketDistributor;
import net.stones.StonesMod;
import net.stones.cap.PlayerShrineCapProvider;
import net.stones.data.ShrineInstance;
import net.stones.data.ShrineSavedData;
import net.stones.logic.RuneCalculator;
import net.stones.network.PacketSyncLevelUpInfo;
import net.stones.network.PacketSyncShrineMirror;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * VERSCHLANKTE EVENT LOGIK
 * Verwaltet die Synchronisation von Attributen und Daten basierend auf Spieler-Events.
 * Ersetzt die ehemalige ShrineSyncLogic Klasse.
 */
@Mod.EventBusSubscriber(modid = StonesMod.MODID)
public class StonesAttributeEvents {

    // Throttling: Verhindert Paket-Spam bei schnellen XP-Änderungen
    private static final Map<UUID, Integer> DIRTY_PLAYERS = new HashMap<>();
    private static final int UPDATE_COOLDOWN = 20; // Max 1 Update pro Sekunde
	private static final Map<UUID, Integer> LAST_KNOWN_LEVELS = new HashMap<>();	
    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.LevelChange event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Markiert Spieler als "dirty" für das verzögerte Update
            DIRTY_PLAYERS.putIfAbsent(player.getUUID(), 0);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Beim Login brauchen wir alles: Mirror (Daten) + Attribute (Werte)
            StonesMod.queueServerWork(5, () -> {
                syncMirrorToClient(player);
                recalculateAttributes(player);
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Respawn: Alles neu laden
            syncMirrorToClient(player);
            recalculateAttributes(player);
        }
    }
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        // Memory Leak verhindern
        LAST_KNOWN_LEVELS.remove(event.getEntity().getUUID());
        DIRTY_PLAYERS.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Dimensionswechsel: Alles neu laden (Client wirft Cache oft weg)
            syncMirrorToClient(player);
            recalculateAttributes(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side.isClient()) return;

        ServerPlayer player = (ServerPlayer) event.player;
        UUID uuid = player.getUUID();
		// --- DER ROUTINE CHECK (Als Sicherheitsnetz gedrosselt auf alle 5 Sekunden) ---
        // Wir müssen nicht jeden einzelnen Tick prüfen. Alle 100 Ticks (5s) reicht völlig,
        // da normale Leveländerungen ohnehin durch Events abgedeckt sein sollten.
        if (player.tickCount % 100 == 0) {
            int currentLevel = player.experienceLevel;
            Integer lastLevel = LAST_KNOWN_LEVELS.get(uuid);
            
            if (lastLevel == null || lastLevel != currentLevel) {
                LAST_KNOWN_LEVELS.put(uuid, currentLevel);
                DIRTY_PLAYERS.putIfAbsent(uuid, 0); // Markiert den Spieler für ein Update
            }
        }
        if (DIRTY_PLAYERS.containsKey(uuid)) {
            int ticksSinceLastUpdate = DIRTY_PLAYERS.get(uuid);
            if (ticksSinceLastUpdate >= UPDATE_COOLDOWN) {
                // Bei XP-Änderung: NUR Attribute neu berechnen!
                // Das Inventar hat sich nicht geändert, also senden wir keinen Mirror.
                recalculateAttributes(player);
                DIRTY_PLAYERS.remove(uuid);
            } else {
                DIRTY_PLAYERS.put(uuid, ticksSinceLastUpdate + 1);
            }
        }
    }

    /**
     * Berechnet Attribute serverseitig neu und informiert den Client über das Level (für Toasts).
     * Leichtgewichtig.
     */
    private static void recalculateAttributes(ServerPlayer player) {
        // 1. Serverseitige Attribute setzen (Single Source of Truth)
		LAST_KNOWN_LEVELS.put(player.getUUID(), player.experienceLevel);
        RuneCalculator.updatePlayer(player);

        // 2. Client informieren (Level für Toasts)
        StonesMod.PACKET_HANDLER.send(
            PacketDistributor.PLAYER.with(() -> player),
            new PacketSyncLevelUpInfo(player.experienceLevel)
        );
    }

    /**
     * Sendet das komplette Inventar und Layout an den Client.
     * Schwergewichtig - nur bei Login/DimChange nutzen.
     */
    private static void syncMirrorToClient(ServerPlayer player) {
        player.getCapability(PlayerShrineCapProvider.SHRINE_LINK).ifPresent(cap -> {
            if (cap.isLinked()) {
                ShrineInstance shrine = ShrineSavedData.get(player.serverLevel()).getShrine(cap.getLinkedShrine());
                if (shrine != null) {
                    // FIX: Expliziter Cast zu ItemStackHandler, da das Paket diesen konkreten Typ
                    // für die NBT-Serialisierung benötigt. Wir wissen, dass ShrineInstance
                    // intern einen ItemStackHandler verwendet.
                    StonesMod.PACKET_HANDLER.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new PacketSyncShrineMirror((ItemStackHandler) shrine.getInventory(), shrine.getLayout())
                    );
                }
            }
        });
    }
}