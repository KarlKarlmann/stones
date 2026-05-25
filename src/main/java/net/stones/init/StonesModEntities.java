package net.stones.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.stones.StonesMod;
import net.stones.entity.EchoTraderEntity;

@Mod.EventBusSubscriber(modid = StonesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class StonesModEntities {
    public static final DeferredRegister<EntityType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, StonesMod.MODID);

    public static final RegistryObject<EntityType<EchoTraderEntity>> ECHO_TRADER = REGISTRY.register("echo_trader",
            () -> EntityType.Builder.of(EchoTraderEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build(new ResourceLocation(StonesMod.MODID, "echo_trader").toString()));

    @SubscribeEvent
    public static void onAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ECHO_TRADER.get(), EchoTraderEntity.createAttributes().build());
    }
}