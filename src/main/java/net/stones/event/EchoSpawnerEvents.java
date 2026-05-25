package net.stones.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stones.StonesMod;
import net.stones.entity.EchoTraderEntity;
import net.stones.init.StonesModEntities;

@Mod.EventBusSubscriber(modid = StonesMod.MODID)
public class EchoSpawnerEvents {

    // Konfiguration
    private static final int SPAWN_INTERVAL = 24000; // 1x pro Minecraft Tag
    private static final int SPAWN_CHANCE = 15; // 15% Chance pro Intervall
    private static int timer = 0;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide) return;
        if (event.level.dimension() != Level.OVERWORLD) return;

        timer++;
        if (timer >= SPAWN_INTERVAL) {
            timer = 0;
            ServerLevel level = (ServerLevel) event.level;
            
            if (level.random.nextInt(100) < SPAWN_CHANCE) {
                spawnEchoTrader(level);
            }
        }
    }

    private static void spawnEchoTrader(ServerLevel level) {
        Player player = level.getRandomPlayer();
        if (player == null) return;

        int rad = 30; // Radius um Spieler
        int x = (int)player.getX() + level.random.nextInt(rad * 2) - rad;
        int z = (int)player.getZ() + level.random.nextInt(rad * 2) - rad;
        
        // Vertikale Suche (von oben nach unten, auch in Höhlen)
        int playerY = (int)player.getY();
        // Suche +/- 20 Blöcke vertikal um den Spieler
        for (int y = playerY - 20; y < playerY + 20; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            
            // Spawn-Bedingung: Luft für Kopf & Körper, fester Boden darunter
            if (level.getBlockState(pos).isAir() && 
                level.getBlockState(pos.above()).isAir() && 
                level.getBlockState(pos.below()).isValidSpawn(level, pos.below(), StonesModEntities.ECHO_TRADER.get())) {
                
                EchoTraderEntity trader = StonesModEntities.ECHO_TRADER.get().create(level);
                if (trader != null) {
                    trader.moveTo(x + 0.5, y, z + 0.5, 0.0f, 0.0f);
                    trader.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null, null);
                    level.addFreshEntity(trader);
                    
                    // Registrierten Sound lokal in der Welt abspielen
                    level.playSound(null, trader.getX(), trader.getY(), trader.getZ(), StonesMod.ECHO_TRADER_EMERGE.get(), SoundSource.NEUTRAL, 1.0f, 1.0f);
                    
                    return; 
                }
            }
        }
    }
}