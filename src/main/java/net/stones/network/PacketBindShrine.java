package net.stones.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.stones.StonesMod;
import net.stones.block.entity.RunestoneBlockEntity;
import net.stones.cap.PlayerShrineCapProvider;
import net.stones.data.ShrineInstance;
import net.stones.data.ShrineSavedData;
import net.stones.logic.RuneCalculator;

import java.util.UUID;
import java.util.function.Supplier;

public class PacketBindShrine {

    private final BlockPos pos;

    public PacketBindShrine(BlockPos pos) {
        this.pos = pos;
    }

    public PacketBindShrine(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null) {
                ServerLevel level = player.serverLevel();
                
                if (level.isLoaded(pos)) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof RunestoneBlockEntity runeBe) {
                        UUID shrineId = runeBe.getShrineId();
                        if (shrineId != null) {
                            
                            ShrineInstance shrine = ShrineSavedData.get(level).getShrine(shrineId);
                            if (shrine != null) {
                                GlobalPos globalPos = GlobalPos.of(level.dimension(), pos);
                                
                                // 1. CLEANUP: Aus altem Schrein austragen (falls vorhanden)
                                player.getCapability(PlayerShrineCapProvider.SHRINE_LINK).ifPresent(cap -> {
                                    if (cap.isLinked() && !cap.getLinkedShrine().equals(shrineId)) {
                                        ShrineInstance oldShrine = ShrineSavedData.get(level).getShrine(cap.getLinkedShrine());
                                        if (oldShrine != null) {
                                            oldShrine.removeOwner(player.getUUID());
                                            
                                            // Optional: Alten Block updaten (Visuals entfernen)
                                            if (oldShrine.getLocation() != null && oldShrine.getLocation().dimension() == level.dimension()) {
                                                BlockPos oldPos = oldShrine.getLocation().pos();
                                                if (level.isLoaded(oldPos)) {
                                                    level.sendBlockUpdated(oldPos, level.getBlockState(oldPos), level.getBlockState(oldPos), 3);
                                                }
                                            }
                                        }
                                    }
                                    
                                    // 2. BINDUNG: Neuen Link setzen
                                    cap.setLinkedShrine(shrineId, globalPos);
                                    
                                    // 3. TEAM: Spieler zum neuen Schrein hinzufügen
                                    shrine.addOwner(player.getUUID());
                                    shrine.setLocation(globalPos);
                                    
                                    // Block Update für neue Visuals
                                    level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
                                    
                                    // Feedback & Sync
                                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.5f);
                                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d[Runestone] §fSeelengebunden an Schrein: §e" + shrineId.toString().substring(0, 8) + "..."));
                                    
                                    RuneCalculator.updatePlayer(player);
                                    StonesMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new PacketSyncPlayerShrine(shrineId, globalPos));
                                });
                            }
                        }
                    }
                }
            }
        });
        return true;
    }
}