package net.stones.event;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.stones.StonesMod;
import net.stones.cap.IPlayerShrineLink;
import net.stones.cap.PlayerShrineCapProvider;
import net.stones.data.ShrineInstance;
import net.stones.data.ShrineSavedData;
import net.stones.network.PacketSyncPlayerShrine;

@Mod.EventBusSubscriber(modid = StonesMod.MODID)
public class StonesCapabilityEvents {

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IPlayerShrineLink.class);
    }

    @SubscribeEvent
    public static void onAttachCapabilitiesPlayer(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            if (!event.getObject().getCapability(PlayerShrineCapProvider.SHRINE_LINK).isPresent()) {
                event.addCapability(new ResourceLocation(StonesMod.MODID, "shrine_link"), new PlayerShrineCapProvider());
            }
        }
    }

    // --- SEELENBINDUNG-LOGIK ---
    
    // 1. Beim Tod: Wir lassen die Bindung nun bestehen!
    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
    }

    // 2. Beim Respawn (Cloning): Capability IMMER kopieren
    @SubscribeEvent
	public static void onPlayerCloned(PlayerEvent.Clone event) {
		event.getOriginal().reviveCaps(); // WICHTIG: Caps des toten Players reaktivieren
		event.getOriginal().getCapability(PlayerShrineCapProvider.SHRINE_LINK).ifPresent(oldStore -> {
			event.getEntity().getCapability(PlayerShrineCapProvider.SHRINE_LINK).ifPresent(newStore -> {
				newStore.copyFrom(oldStore);
			});
		});
		event.getOriginal().invalidateCaps(); // danach wieder invalidieren
	}

    // Sync beim Login
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(PlayerShrineCapProvider.SHRINE_LINK).ifPresent(cap -> {
                StonesMod.PACKET_HANDLER.send(
                    PacketDistributor.PLAYER.with(() -> player), 
                    new PacketSyncPlayerShrine(cap.getLinkedShrine(), cap.getShrinePos())
                );
            });
        }
    }
    
    // Sync beim Dimensionswechsel
    @SubscribeEvent
    public static void onPlayerDimChange(PlayerEvent.PlayerChangedDimensionEvent event) {
         if (event.getEntity() instanceof ServerPlayer player) {
            player.getCapability(PlayerShrineCapProvider.SHRINE_LINK).ifPresent(cap -> {
                StonesMod.PACKET_HANDLER.send(
                    PacketDistributor.PLAYER.with(() -> player), 
                    new PacketSyncPlayerShrine(cap.getLinkedShrine(), cap.getShrinePos())
                );
            });
         }
    }
}