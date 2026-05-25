package net.stones.init;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent; 
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.ModList;
import net.stones.StonesMod;
import net.stones.block.entity.RunestoneBlockEntity;
import net.stones.client.gui.EchoTraderScreen;
import net.stones.client.gui.RunestoneScreen;
import net.stones.client.renderer.EchoTraderRenderer;
import net.stones.client.renderer.RunestoneRenderer;
import net.stones.client.renderer.ClientDynamicLabelHandler;
import net.stones.enchantment.AmplifyEnchantment;
import net.stones.block.entity.ResonanceBoxBlockEntity;
import net.stones.client.renderer.ResonanceBoxRenderer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(modid = StonesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class StonesModClient {

    // --- ZENTRALE LISTE ALLER GEFUNDENEN DEKO-MODELLE ---
    // Diese Variable hat in deinem Code gefehlt, weshalb der Renderer abgestürzt ist.
    public static final List<ResourceLocation> SHRINE_ARTIFACTS = new ArrayList<>();

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(StonesModMenus.RUNESTONE_MENU.get(), RunestoneScreen::new);
            MenuScreens.register(StonesModMenus.ECHO_TRADER_MENU.get(), EchoTraderScreen::new);
            
            ClientDynamicLabelHandler.init();

            registerAmplifyProperty(StonesModItems.RUNE_MINOR.get());
            registerAmplifyProperty(StonesModItems.RUNE_MAJOR.get());
            registerAmplifyProperty(StonesModItems.RUNE_MILESTONE.get());
            
            registerAmplifyProperty(StonesModItems.CLUSTER_JEWEL_MINOR.get());
            registerAmplifyProperty(StonesModItems.CLUSTER_JEWEL_MAJOR.get());
            registerAmplifyProperty(StonesModItems.CLUSTER_JEWEL_MILESTONE.get());
        });
    }

    @SubscribeEvent
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        // Cluster Jewels (Manuelle Registrierung)
        event.register(new ResourceLocation(StonesMod.MODID, "item/cluster_jewel_minor_1"));
        event.register(new ResourceLocation(StonesMod.MODID, "item/cluster_jewel_minor_2"));
        event.register(new ResourceLocation(StonesMod.MODID, "item/cluster_jewel_minor_3"));
        event.register(new ResourceLocation(StonesMod.MODID, "item/cluster_jewel_minor_4"));
        event.register(new ResourceLocation(StonesMod.MODID, "item/cluster_jewel_minor_legendary"));
        
        event.register(new ResourceLocation(StonesMod.MODID, "item/cluster_jewel_major_1"));
        event.register(new ResourceLocation(StonesMod.MODID, "item/cluster_jewel_major_2"));
        event.register(new ResourceLocation(StonesMod.MODID, "item/cluster_jewel_major_3"));
        event.register(new ResourceLocation(StonesMod.MODID, "item/cluster_jewel_major_4"));
        event.register(new ResourceLocation(StonesMod.MODID, "item/cluster_jewel_major_legendary"));
        
        event.register(new ResourceLocation(StonesMod.MODID, "item/cluster_jewel_milestone_1"));
        event.register(new ResourceLocation(StonesMod.MODID, "item/cluster_jewel_milestone_2"));
        event.register(new ResourceLocation(StonesMod.MODID, "item/cluster_jewel_milestone_3"));
        event.register(new ResourceLocation(StonesMod.MODID, "item/cluster_jewel_milestone_4"));
        event.register(new ResourceLocation(StonesMod.MODID, "item/cluster_jewel_milestone_legendary"));

        // --- AUTOMATISCHER DEKO-SCANNER ---
        SHRINE_ARTIFACTS.clear();
        try {
            Path decorPath = ModList.get().getModFileById(StonesMod.MODID).getFile()
                .findResource("assets", StonesMod.MODID, "models", "shrine_decor");

            if (Files.exists(decorPath)) {
                try (Stream<Path> walk = Files.walk(decorPath, 1)) {
                    walk.filter(p -> p.toString().endsWith(".json"))
                        .forEach(p -> {
                            String filename = p.getFileName().toString().replace(".json", "");
                            ResourceLocation loc = new ResourceLocation(StonesMod.MODID, "shrine_decor/" + filename);
                            
                            // 1. Für Minecraft registrieren
                            event.register(loc);
                            
                            // 2. Für unseren Renderer merken
                            SHRINE_ARTIFACTS.add(loc);
                        });
                }
                StonesMod.LOGGER.info("Shrine Decor: {} Modelle geladen.", SHRINE_ARTIFACTS.size());
            } else {
                StonesMod.LOGGER.warn("Ordner 'shrine_decor' nicht gefunden - Artefakte bleiben leer.");
            }
        } catch (Exception e) {
            StonesMod.LOGGER.error("Fehler beim Scannen der Shrine Decor Modelle", e);
        }
    }

    private static void registerAmplifyProperty(net.minecraft.world.item.Item item) {
        ItemProperties.register(item, new ResourceLocation(StonesMod.MODID, "amplify"), (stack, level, entity, seed) -> {
            int ampLvl = 0;
            var enchants = EnchantmentHelper.getEnchantments(stack);
            for (var entry : enchants.entrySet()) {
                if (entry.getKey() instanceof AmplifyEnchantment) {
                    ampLvl = entry.getValue();
                    break;
                }
            }
            return ampLvl / 100.0f;
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
            (BlockEntityType<RunestoneBlockEntity>) StonesModBlockEntities.RUNESTONE.get(), 
            RunestoneRenderer::new
        );
        event.registerBlockEntityRenderer(
            (BlockEntityType<ResonanceBoxBlockEntity>) StonesModBlockEntities.RESONANCE_BOX.get(),
            ResonanceBoxRenderer::new
        );
        event.registerEntityRenderer(
            StonesModEntities.ECHO_TRADER.get(), 
            EchoTraderRenderer::new
        );
    }
}