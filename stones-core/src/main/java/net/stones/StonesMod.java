package net.stones;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import net.stones.init.StonesModItems;
import net.stones.init.StonesModBlocks;
import net.stones.init.StonesModBlockEntities;
import net.stones.init.StonesModMenus;
import net.stones.init.StonesModParticles;
import net.stones.init.StonesModEntities;
import net.stones.init.StonesModTabs;
import net.stones.init.StonesModConfig;
import net.stones.network.*;
import net.stones.util.NbtItemLootModifier;

import com.mojang.serialization.Codec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.ModLoadingContext;
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
	public static final RegistryObject<SoundEvent> SHRINE_BIND = SOUNDS.register("shrine_bind",
		() -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MODID, "shrine_bind")));

	// --- LOOT MODIFIER REGISTRY ---
	public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS = 
		DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MODID);
	public static final RegistryObject<Codec<NbtItemLootModifier>> ADD_NBT_ITEM = 
		LOOT_MODIFIERS.register("add_nbt_item", () -> NbtItemLootModifier.CODEC);
	
	public StonesMod() {
		IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();

		// Config registrieren
		ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, StonesModConfig.SPEC);

		StonesModBlocks.REGISTRY.register(bus);
		StonesModBlockEntities.REGISTRY.register(bus);
		StonesModItems.REGISTRY.register(bus);
		StonesModMenus.REGISTRY.register(bus);
		StonesModParticles.REGISTRY.register(bus);
		StonesModEntities.REGISTRY.register(bus);
		StonesModTabs.REGISTRY.register(bus);

		// Registrierungen am Mod Event Bus
		SOUNDS.register(bus);
		LOOT_MODIFIERS.register(bus);

		bus.addListener(this::setup);
		
		MinecraftForge.EVENT_BUS.register(this);
	}

	private void setup(final FMLCommonSetupEvent event) {
		LOGGER.info("Registriere optimierte Netzwerk-Pakete für Stones Mod...");
		// --- C2S ---
		addNetworkMessage(PacketBindShrine.class, PacketBindShrine::toBytes, PacketBindShrine::new, PacketBindShrine::handle);
		addNetworkMessage(PacketOpenShrine.class, PacketOpenShrine::toBytes, PacketOpenShrine::new, PacketOpenShrine::handle);
		addNetworkMessage(PacketPerformAction.class, PacketPerformAction::encode, PacketPerformAction::new, PacketPerformAction::handle);	
		addNetworkMessage(C2SRequestTexturePacket.class, C2SRequestTexturePacket::toBytes, C2SRequestTexturePacket::new, C2SRequestTexturePacket::handle);

		// --- S2C ---
		addNetworkMessage(PacketSyncPlayerShrine.class, PacketSyncPlayerShrine::toBytes, PacketSyncPlayerShrine::new, PacketSyncPlayerShrine::handle);
		addNetworkMessage(PacketSyncShrineMirror.class, PacketSyncShrineMirror::encode, PacketSyncShrineMirror::new, PacketSyncShrineMirror::handle);
		addNetworkMessage(PacketSyncLevelUpInfo.class, PacketSyncLevelUpInfo::encode, PacketSyncLevelUpInfo::new, PacketSyncLevelUpInfo::handle);			
		addNetworkMessage(PacketSyncCombo.class, PacketSyncCombo::encode, PacketSyncCombo::new, PacketSyncCombo::handle);
		addNetworkMessage(PacketSyncCooldown.class, PacketSyncCooldown::encode, PacketSyncCooldown::new, PacketSyncCooldown::handle);
		addNetworkMessage(PacketSyncEnchantments.class, PacketSyncEnchantments::toBytes, PacketSyncEnchantments::new, PacketSyncEnchantments::handle);
		addNetworkMessage(S2CSendTexturePacket.class, S2CSendTexturePacket::toBytes, S2CSendTexturePacket::new, S2CSendTexturePacket::handle);
		addNetworkMessage(S2CSpawnSpritePacket.class, S2CSpawnSpritePacket::toBytes, S2CSpawnSpritePacket::new, S2CSpawnSpritePacket::handle);
		addNetworkMessage(S2CSpawnBeamPacket.class, S2CSpawnBeamPacket::toBytes, S2CSpawnBeamPacket::new, S2CSpawnBeamPacket::handle);
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