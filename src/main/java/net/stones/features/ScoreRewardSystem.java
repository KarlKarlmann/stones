package net.stones.features;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.stones.StonesMod;
import net.stones.data.GlobalLeaderboardData;
import net.stones.init.StonesModItems;
import net.stones.network.PacketOpenLeaderboard;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = StonesMod.MODID)
public class ScoreRewardSystem {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String NBT_LIST_KEY = "stones_legacy_runs";

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            int currentRunScore = player.getScore();
            if (currentRunScore <= 0) return;

            // NEU: Wir speichern den Score des Todes IMMER zwischen, 
            // völlig unabhängig davon, ob er eine Kiste wert ist!
            player.getPersistentData().putInt("stones_last_death_score", currentRunScore);

            // Todesgrund parsen
            String deathReason = player.getCombatTracker().getDeathMessage().getString();
            
            // Globales Leaderboard abrufen, um Platzierung zu checken
            GlobalLeaderboardData leaderboard = GlobalLeaderboardData.get(player.serverLevel());
            List<GlobalLeaderboardData.LeaderboardEntry> globalEntries = leaderboard.getEntries();
            
            // Prüfen ob der Score würdig für eine Kiste ist (>= 300 Punkte ODER in den Top 3)
            boolean isTop3 = globalEntries.size() < 3 || currentRunScore >= globalEntries.get(Math.min(globalEntries.size() - 1, 2)).score();
            boolean isWorthy = currentRunScore >= 300 || isTop3;

            // Score und Grund IMMER global speichern (für die Hall of Resonance Statistik)
            leaderboard.addScore(player.getName().getString(), currentRunScore, deathReason);
            
            // Persönliche Belohnung nur eintragen, wenn die Bedingungen erfüllt sind
            if (isWorthy) {
                CompoundTag persist = player.getPersistentData();
                ListTag legacyList = persist.getList(NBT_LIST_KEY, Tag.TAG_INT);
                legacyList.add(IntTag.valueOf(currentRunScore));
                persist.put(NBT_LIST_KEY, legacyList);
                
                LOGGER.info("[Stones] Legacy-Run für Belohnung registriert: {} (Grund: {})", currentRunScore, deathReason);
            } else {
                LOGGER.info("[Stones] Legacy-Run ignoriert (< 300 Punkte & nicht Top 3): {}", currentRunScore);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag oldData = event.getOriginal().getPersistentData();
        // Kopiere Legacy Runs
        if (oldData.contains(NBT_LIST_KEY)) {
            event.getEntity().getPersistentData().put(NBT_LIST_KEY, oldData.get(NBT_LIST_KEY).copy());
        }
        // NEU: Kopiere letzten Todes-Score in den neuen Körper
        if (oldData.contains("stones_last_death_score")) {
            event.getEntity().getPersistentData().putInt("stones_last_death_score", oldData.getInt("stones_last_death_score"));
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ListTag list = player.getPersistentData().getList(NBT_LIST_KEY, Tag.TAG_INT);
            List<Integer> personalScores = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) personalScores.add(list.getInt(i));

            List<GlobalLeaderboardData.LeaderboardEntry> global = GlobalLeaderboardData.get(player.serverLevel()).getEntries();

            // NEU: Hole den Wert des gerade passierten Todes!
            int lastDeathScore = player.getPersistentData().getInt("stones_last_death_score");

            // FIX: Jetzt werden korrekterweise 3 Argumente (inklusive lastDeathScore) übergeben!
            StonesMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), 
                new PacketOpenLeaderboard(personalScores, global, lastDeathScore));
        }
    }

    public static void claimReward(ServerPlayer player) {
        CompoundTag nbt = player.getPersistentData();
        ListTag list = nbt.getList(NBT_LIST_KEY, Tag.TAG_INT);

        if (list.isEmpty()) return;

        int count = 0;
        ListTag remaining = list.copy();
        
        for (int i = list.size() - 1; i >= 0; i--) {
            int score = list.getInt(i);
            // Tier Berechnung: Max Tier ist 10 (für 10.000 Score)
            int tier = Math.max(1, Math.min(10, (score / 1000) + 1));
            
            ItemStack rewardBox = new ItemStack(StonesModItems.RESONANCE_BOX.get());
            
            // Multilang Hover Name
            rewardBox.setHoverName(Component.translatable("item.stones.resonance_gift", tier).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
            rewardBox.getOrCreateTag().putInt("ResonanceLootTier", tier);

            // Lore hinzufügen (Multilang)
            ListTag lore = new ListTag();
            lore.add(net.minecraft.nbt.StringTag.valueOf(
                Component.Serializer.toJson(Component.translatable("tooltip.stones.score", score).withStyle(net.minecraft.ChatFormatting.DARK_GRAY))
            ));
            rewardBox.getOrCreateTag().getCompound("display").put("Lore", lore);

            if (player.getInventory().add(rewardBox)) {
                remaining.remove(i);
                count++;
            } else break; // Inventar voll
        }

        nbt.put(NBT_LIST_KEY, remaining);
        if (count > 0) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), 
                net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.5f);
        }
    }
}