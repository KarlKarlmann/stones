package net.stones.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerXpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.PacketDistributor;
import net.stones.StonesMod;
import net.stones.cap.PlayerShrineCapProvider;
import net.stones.data.ShrineInstance;
import net.stones.data.ShrineSavedData;
import net.stones.logic.RuneCalculator;
import net.stones.network.PacketSyncLevelUpInfo;
import net.stones.network.PacketSyncShrineMirror;
import net.stones.network.PacketSyncPlayerShrine;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Verwaltet Attributberechnungen und Synchronisationen von Spieler-Events.
 * Integriert den periodischen Combo-Timer Decay auf dem Server-Thread für alle LivingEntities.
 */
@Mod.EventBusSubscriber(modid = StonesMod.MODID)
public class StonesAttributeEvents {

    private static final Map<UUID, Integer> DIRTY_PLAYERS = new HashMap<>();
    private static final int UPDATE_COOLDOWN = 20; 
	private static final Map<UUID, Integer> LAST_KNOWN_LEVELS = new HashMap<>();	

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.LevelChange event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            DIRTY_PLAYERS.putIfAbsent(player.getUUID(), 0);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StonesMod.queueServerWork(5, () -> {
                syncMirrorToClient(player);
                recalculateAttributes(player);
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(PlayerShrineCapProvider.SHRINE_LINK).ifPresent(cap -> {
                StonesMod.PACKET_HANDLER.send(
                    PacketDistributor.PLAYER.with(() -> player), 
                    new PacketSyncPlayerShrine(cap.getLinkedShrine(), cap.getShrinePos())
                );
            });
            syncMirrorToClient(player);
            recalculateAttributes(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        LAST_KNOWN_LEVELS.remove(uuid);
        DIRTY_PLAYERS.remove(uuid);
        RuneCalculator.ACTIVE_MILESTONES.remove(uuid); 
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncMirrorToClient(player);
            recalculateAttributes(player);
        }
    }

    /**
     * Periodischer Check für den Zerfall (Decay) aller Mobs und Entities in der Welt.
     * Erlaubt flüssigen Abbau ohne Performance-Verlust (gedrosselt auf 20 Ticks).
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        // Drosselung auf 1 Sekunde (20 Ticks)
        if (entity.tickCount % 20 == 0) {
            if (entity instanceof ServerPlayer player) {
                RuneCalculator.tickCombos(player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side.isClient()) return;

        ServerPlayer player = (ServerPlayer) event.player;
        UUID uuid = player.getUUID();

        // LEVEL CHANGE DETECTION (Alle 5 Sekunden)
        if (player.tickCount % 100 == 0) {
            int currentLevel = player.experienceLevel;
            Integer lastLevel = LAST_KNOWN_LEVELS.get(uuid);
            
            if (lastLevel == null || lastLevel != currentLevel) {
                LAST_KNOWN_LEVELS.put(uuid, currentLevel);
                DIRTY_PLAYERS.putIfAbsent(uuid, 0); 
            }
        }

        if (DIRTY_PLAYERS.containsKey(uuid)) {
            int ticksSinceLastUpdate = DIRTY_PLAYERS.get(uuid);
            if (ticksSinceLastUpdate >= UPDATE_COOLDOWN) {
                recalculateAttributes(player);
                DIRTY_PLAYERS.remove(uuid);
            } else {
                DIRTY_PLAYERS.put(uuid, ticksSinceLastUpdate + 1);
            }
        }
    }

    private static void recalculateAttributes(ServerPlayer player) {
		LAST_KNOWN_LEVELS.put(player.getUUID(), player.experienceLevel);
        RuneCalculator.updatePlayer(player);
        player.getCapability(PlayerShrineCapProvider.SHRINE_LINK).ifPresent(cap -> {
            if (cap.isLinked()) {
                ShrineInstance shrine = ShrineSavedData.get(player.serverLevel()).getShrine(cap.getLinkedShrine());
                if (shrine != null) {
                    for (int i = 0; i < shrine.getInventory().getSlots(); i++) {
                        if (!shrine.getInventory().getStackInSlot(i).isEmpty()) {
                            net.stones.advancement.StonesAdvancementHelper.grantAdvancement(player, "power/growing_resonance");
                            break;
                        }
                    }
                }
            }
        });
        StonesMod.PACKET_HANDLER.send(
            PacketDistributor.PLAYER.with(() -> player),
            new PacketSyncLevelUpInfo(player.experienceLevel)
        );
    }

    private static void syncMirrorToClient(ServerPlayer player) {
        player.getCapability(PlayerShrineCapProvider.SHRINE_LINK).ifPresent(cap -> {
            if (cap.isLinked()) {
                ShrineInstance shrine = ShrineSavedData.get(player.serverLevel()).getShrine(cap.getLinkedShrine());
                if (shrine != null) {
                    StonesMod.PACKET_HANDLER.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new PacketSyncShrineMirror((ItemStackHandler) shrine.getInventory(), shrine.getLayout())
                    );
                }
            }
        });
    }
}