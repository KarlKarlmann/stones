package net.stones;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import net.stones.init.StonesModItems;
import net.stones.init.StonesModBlocks;
import net.stones.init.StonesModBlockEntities;
import net.stones.init.StonesModMenus;
import net.stones.init.StonesModParticles;
import net.stones.init.StonesModEntities;
import net.stones.network.*;
// WICHTIG: ActionSystem import entfernen oder sicherstellen, dass er nicht für Packets genutzt wird!
// import net.stones.features.ActionSystem; 
import net.stones.enchantment.behavior.reflection.ReflectionInvoker;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.util.thread.SidedThreadGroups;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.MinecraftForge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.io.InputStream;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.function.BiConsumer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;
import java.util.Collection;
import java.util.ArrayList;
import java.util.AbstractMap;

@Mod(StonesMod.MODID)
public class StonesMod {
	public static final Logger LOGGER = LogManager.getLogger(StonesMod.class);
	public static final String MODID = "stones";

	private static final String PROTOCOL_VERSION = "1";
	
	public static final SimpleChannel PACKET_HANDLER = NetworkRegistry.newSimpleChannel(
		new ResourceLocation(MODID, "stones"), 
		() -> PROTOCOL_VERSION, 
		PROTOCOL_VERSION::equals, 
		PROTOCOL_VERSION::equals
	);

	private static int messageID = 0;

	// --- SOUND REGISTRY ---
	public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MODID);
	public static final RegistryObject<SoundEvent> ECHO_TRADER_EMERGE = SOUNDS.register("echo_trader_emerge",
		() -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "echo_trader_emerge")));
	public static final RegistryObject<SoundEvent> SHRINE_BIND = SOUNDS.register("shrine_bind",
		() -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "shrine_bind")));
	public StonesMod() {
		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

		StonesModBlocks.REGISTRY.register(bus);
		StonesModBlockEntities.REGISTRY.register(bus);
		StonesModItems.REGISTRY.register(bus);
		StonesModMenus.REGISTRY.register(bus);
		StonesModParticles.REGISTRY.register(bus);
		StonesModEntities.REGISTRY.register(bus);

		// Sounds am Event-Bus registrieren
		SOUNDS.register(bus);

		bus.addListener(this::setup);
		
		MinecraftForge.EVENT_BUS.register(this);
	}

	private void setup(final FMLCommonSetupEvent event) {
		LOGGER.info("Registriere optimierte Netzwerk-Pakete für Stones Mod...");
		
		// --- C2S ---
		addNetworkMessage(PacketBindShrine.class, PacketBindShrine::toBytes, PacketBindShrine::new, PacketBindShrine::handle);
		addNetworkMessage(PacketOpenShrine.class, PacketOpenShrine::toBytes, PacketOpenShrine::new, PacketOpenShrine::handle);
		addNetworkMessage(PacketPerformAction.class, PacketPerformAction::encode, PacketPerformAction::new, PacketPerformAction::handle);	
		addNetworkMessage(PacketClaimReward.class, PacketClaimReward::toBytes, PacketClaimReward::new, PacketClaimReward::handle);
		addNetworkMessage(PacketBuyEcho.class, PacketBuyEcho::toBytes, PacketBuyEcho::new, PacketBuyEcho::handle);
		addNetworkMessage(PacketUpdateEpitaph.class, PacketUpdateEpitaph::toBytes, PacketUpdateEpitaph::new, PacketUpdateEpitaph::handle); // Registrierung des Epitaph-Netzwerkpakets

		// --- S2C ---
		addNetworkMessage(PacketSyncPlayerShrine.class, PacketSyncPlayerShrine::toBytes, PacketSyncPlayerShrine::new, PacketSyncPlayerShrine::handle);
		addNetworkMessage(PacketSyncShrineMirror.class, PacketSyncShrineMirror::encode, PacketSyncShrineMirror::new, PacketSyncShrineMirror::handle);
		addNetworkMessage(PacketOpenLeaderboard.class, PacketOpenLeaderboard::toBytes, PacketOpenLeaderboard::new, PacketOpenLeaderboard::handle);
		addNetworkMessage(PacketSyncLevelUpInfo.class, PacketSyncLevelUpInfo::encode, PacketSyncLevelUpInfo::new, PacketSyncLevelUpInfo::handle);			
		addNetworkMessage(PacketSyncCombo.class, PacketSyncCombo::encode, PacketSyncCombo::new, PacketSyncCombo::handle);

		event.enqueueWork(() -> {
			try {
				InputStream mappingStream = StonesMod.class.getResourceAsStream("/assets/stones/data/mappings.json");
				if (mappingStream != null) {
					ReflectionInvoker.init(mappingStream);
				} else {
					LOGGER.error("[Stones] mappings.json konnte nicht in den Ressourcen gefunden werden!");
				}
			} catch (Exception e) {
				LOGGER.error("[Stones] Fehler beim Initialisieren des ReflectionInvokers", e);
			}
		});
	}

	public static <T> void addNetworkMessage(Class<T> messageType, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder, BiConsumer<T, Supplier<NetworkEvent.Context>> messageConsumer) {
		PACKET_HANDLER.registerMessage(messageID, messageType, encoder, decoder, messageConsumer);
		messageID++;
	}

	// --- Server Work Queue ---
	private static final Collection<AbstractMap.SimpleEntry<Runnable, Integer>> workQueue = new ConcurrentLinkedQueue<>();

	public static void queueServerWork(int tick, Runnable action) {
		if (Thread.currentThread().getThreadGroup() == SidedThreadGroups.SERVER)
			workQueue.add(new AbstractMap.SimpleEntry<>(action, tick));
	}

	@SubscribeEvent
	public void tick(TickEvent.ServerTickEvent event) {
		if (event.phase == TickEvent.Phase.END) {
			List<AbstractMap.SimpleEntry<Runnable, Integer>> actions = new ArrayList<>();
			workQueue.forEach(work -> {
				work.setValue(work.getValue() - 1);
				if (work.getValue() == 0)
					actions.add(work);
			});
			actions.forEach(e -> e.getKey().run());
			workQueue.removeAll(actions);
		}
	}
}