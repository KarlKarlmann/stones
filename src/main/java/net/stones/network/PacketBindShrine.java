package net.stones.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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

/**
 * C2S Paket: Verwaltet den stufenweisen Fortschritt und den finalen Abschluss der Seelenbindung.
 * HINWEIS: Sämtliche Schadens- und Wither-Mechaniken wurden temporär auskommentiert.
 * Die atmosphärischen Effekte (Darkness, Nausea, Sounds, Partikel) sind weiterhin aktiv.
 */
public class PacketBindShrine {

    private final BlockPos pos;
    private final int progress; // Kanalisierungs-Fortschritt in Ticks (0 bis 60)

    public PacketBindShrine(BlockPos pos, int progress) {
        this.pos = pos;
        this.progress = progress;
    }

    public PacketBindShrine(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.progress = buf.readInt();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(progress);
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
                                
                                player.getCapability(PlayerShrineCapProvider.SHRINE_LINK).ifPresent(cap -> {
                                    CompoundTag persist = player.getPersistentData();
                                    long currentTime = level.getGameTime();

                                    // =========================================================================
                                    // ANTI-CHEAT: VALIDIERUNG DER SEQUENZ
                                    // =========================================================================
                                    if (this.progress == 10) {
                                        // Startpunkt der Bindung serverseitig fälschungssicher wegschreiben
                                        persist.putLong("stones_bind_start_time", currentTime);
                                        persist.putInt("stones_bind_x", pos.getX());
                                        persist.putInt("stones_bind_y", pos.getY());
                                        persist.putInt("stones_bind_z", pos.getZ());
                                    } else {
                                        // Sicherheitsprüfung für alle darauffolgenden Ticks (30, 50, 60)
                                        if (!persist.contains("stones_bind_start_time") ||
                                            persist.getInt("stones_bind_x") != pos.getX() ||
                                            persist.getInt("stones_bind_y") != pos.getY() ||
                                            persist.getInt("stones_bind_z") != pos.getZ()) {
                                            
                                            // Illegitimer Paketaufruf! Der Spieler hat die Sequenz nicht gestartet oder die Position gewechselt.
                                            StonesMod.LOGGER.warn("Spieler {} versuchte unbefugtes Paket-Spoofing auf Schrein bei Pos {}!", player.getName().getString(), pos);
                                            return;
                                        }

                                        long startTime = persist.getLong("stones_bind_start_time");
                                        long ticksPassed = currentTime - startTime;

                                        // Prüfen, ob die Zeit manipuliert wurde (Pakete kamen zu schnell an)
                                        // Delta-Zeiten basieren auf dem Startpunkt bei Tick 10!
                                        if (this.progress == 30 && ticksPassed < 15) return; 
                                        if (this.progress == 50 && ticksPassed < 35) return; 
                                        if (this.progress == 60 && ticksPassed < 45) {
                                            return;
                                        }
                                    }

                                    // =========================================================================
                                    // STUFENWEISE RESONANZ-BELASTUNG (Atmosphärische Trance-Effekte)
                                    // =========================================================================
                                    if (this.progress < 60) {
                                        if (this.progress == 10) {
                                            // Stufe 1: Erster Frequenzschock (0.5s)
                                            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
                                            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false));
                                            
                                            // --- SCHADEN DEAKTIVIERT ---
                                            /*
                                            float targetHealth = player.getMaxHealth() * 0.6F; // Auf 60% Leben bringen
                                            float currentHealth = player.getHealth();
                                            float damage = currentHealth - targetHealth;
                                            if (damage > 0.0F) {
                                                player.hurt(level.damageSources().magic(), damage);
                                            } else if (currentHealth > 2.0F) {
                                                player.hurt(level.damageSources().magic(), 1.0F); // Token-Schaden
                                            }
                                            */
                                            
                                            // Unheimliches Seelenflüstern
                                            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.SOUL_ESCAPE, net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 0.5f);
                                        } 
                                        else if (this.progress == 30) {
                                            // Stufe 2: Trance vertieft sich (1.5s)
                                            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false));
                                            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 140, 0, false, false));
                                            
                                            // --- SCHADEN DEAKTIVIERT ---
                                            /*
                                            float targetHealth = player.getMaxHealth() * 0.3F;
                                            float currentHealth = player.getHealth();
                                            float damage = currentHealth - targetHealth;
                                            if (damage > 0.0F) {
                                                player.hurt(level.damageSources().magic(), damage);
                                            } else if (currentHealth > 2.0F) {
                                                player.hurt(level.damageSources().magic(), 1.0F);
                                            }
                                            */
                                            
                                            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BEACON_DEACTIVATE, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 0.5f);
                                        } 
                                        else if (this.progress == 50) {
                                            // Stufe 3: Die Schwelle zur Leere (2.5s)
                                            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
                                            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 180, 0, false, false));
                                            
                                            // --- SCHADEN DEAKTIVIERT ---
                                            /*
                                            float currentHealth = player.getHealth();
                                            float damage = currentHealth - 2.0F;
                                            if (damage > 0.0F) {
                                                player.hurt(level.damageSources().magic(), damage);
                                            }
                                            */
                                            
                                            // Unaufdringliche Warnmeldung in der Actionbar
                                            player.displayClientMessage(
                                                net.minecraft.network.chat.Component.translatable("chat.stones.shrine.resonance_overload"), 
                                                true
                                            );
                                            
                                            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CONDUIT_ATTACK_TARGET, net.minecraft.sounds.SoundSource.PLAYERS, 0.9f, 0.5f);
                                        }

                                        // Partikel während des Entzugs (Rein kosmetisch belassen)
                                        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, 
                                            pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 
                                            6, 0.2, 0.2, 0.2, 0.05);
                                        level.sendParticles(ParticleTypes.WITCH, 
                                            pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 
                                            10, 0.3, 0.3, 0.3, 0.1);
                                        
                                        return; // Kanalisierung läuft noch, Bindung erfolgt erst bei 60 Ticks!
                                    }

                                    // =========================================================================
                                    // STUFE 4: DER FINAL COLLAPSE (3.0s erreicht - Versiegelung)
                                    // =========================================================================
                                    
                                    // Cleanup der Anti-Cheat-Daten aus dem NBT
                                    persist.remove("stones_bind_start_time");
                                    persist.remove("stones_bind_x");
                                    persist.remove("stones_bind_y");
                                    persist.remove("stones_bind_z");

                                    // 1. Alten Schrein-Besitz säubern (falls vorhanden)
                                    if (cap.isLinked() && !cap.getLinkedShrine().equals(shrineId)) {
                                        ShrineInstance oldShrine = ShrineSavedData.get(level).getShrine(cap.getLinkedShrine());
                                        if (oldShrine != null) {
                                            oldShrine.removeOwner(player.getUUID());
                                            
                                            if (oldShrine.getLocation() != null && oldShrine.getLocation().dimension() == level.dimension()) {
                                                BlockPos oldPos = oldShrine.getLocation().pos();
                                                if (level.isLoaded(oldPos)) {
                                                    level.sendBlockUpdated(oldPos, level.getBlockState(oldPos), level.getBlockState(oldPos), 3);
                                                }
                                            }
                                        }
                                    }
                                    
                                    // --- WITHER & HP MODIFIKATION DEAKTIVIERT ---
                                    /*
                                    player.setHealth(2.0F);
                                    player.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
                                    */
                                    
                                    // Darkness und Confusion zur Abrundung des Ritualendes belassen
                                    player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
                                    player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));

                                    // Massiver Partikel-Ausbruch am Altar
                                    level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, 
                                        pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 
                                        25, 0.3, 0.4, 0.3, 0.1);
                                    
                                    level.sendParticles(ParticleTypes.WITCH, 
                                        pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 
                                        35, 0.5, 0.5, 0.5, 0.15);

                                    if (player.isAlive()) {
                                        // Neuen Link im Player und im Schrein registrieren
                                        cap.setLinkedShrine(shrineId, globalPos);
                                        shrine.addOwner(player.getUUID());
                                        shrine.setLocation(globalPos);
                                        
                                        // Block-Renderzustand updaten (Der Altar leuchtet auf)
                                        level.setBlock(pos, level.getBlockState(pos).setValue(net.stones.block.RunestoneBlock.ACTIVE, true), 3);
                                        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
                                        
                                        // Kosmischen Saturn-Sound ertönen lassen
                                        level.playSound(null, pos, StonesMod.SHRINE_BIND.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                                        
                                        // Unaufdringliche Erfolgsmeldung in die Actionbar
                                        player.displayClientMessage(
                                            net.minecraft.network.chat.Component.translatable(
                                                "chat.stones.shrine.bound_success", 
                                                shrineId.toString().substring(0, 8) + "..."
                                            ), 
                                            true
                                        );
                                        
                                        RuneCalculator.updatePlayer(player);
                                        StonesMod.PACKET_HANDLER.send(PacketDistributor.PLAYER.with(() -> player), new PacketSyncPlayerShrine(shrineId, globalPos));
                                    } else {
                                        // Fallback falls der Spieler direkt stirbt
                                        level.sendParticles(ParticleTypes.LARGE_SMOKE, 
                                            pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 
                                            15, 0.2, 0.2, 0.2, 0.05);
                                    }
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