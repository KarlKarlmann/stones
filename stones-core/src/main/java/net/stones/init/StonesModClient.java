package net.stones.init;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent; 
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.stones.StonesMod;
import net.stones.client.integration.StonesUIManager;
import net.stones.client.renderer.StonesProjectileRenderer;
import net.stones.gui.RunestoneMenu;

@Mod.EventBusSubscriber(modid = StonesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class StonesModClient {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Type-Witness <RunestoneMenu, AbstractContainerScreen<RunestoneMenu>> vor register
            MenuScreens.<RunestoneMenu, AbstractContainerScreen<RunestoneMenu>>register(
                StonesModMenus.RUNESTONE_MENU.get(),
                StonesUIManager::createRunestoneScreen
            );


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
    }

    private static void registerAmplifyProperty(net.minecraft.world.item.Item item) {
        ItemProperties.register(item, new ResourceLocation(StonesMod.MODID, "amplify"), (stack, level, entity, seed) -> {
            try {
                if (stack == null || stack.isEmpty()) {
                    return 0.0f;
                }

                net.minecraft.world.item.enchantment.Enchantment amplify = net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(StonesMod.MODID, "amplify"));
                if (amplify == null) {
                    return 0.0f;
                }

                int ampLvl = EnchantmentHelper.getItemEnchantmentLevel(amplify, stack);
                if (ampLvl <= 0) {
                    return 0.0f; 
                }
                
                return net.minecraft.util.Mth.clamp(ampLvl / 100.0f, 0.0f, 1.0f);

            } catch (Exception e) {
                return 0.0f; 
            }
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        
        event.registerEntityRenderer(
            StonesModEntities.STONES_PROJECTILE.get(), 
            StonesProjectileRenderer::new
        );
    }
}