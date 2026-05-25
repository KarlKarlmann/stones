
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.stones.init;

import net.stones.block.entity.*;
import net.stones.StonesMod;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.Block;

public class StonesModBlockEntities {
	public static final DeferredRegister<BlockEntityType<?>> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, StonesMod.MODID);
	public static final RegistryObject<BlockEntityType<?>> RUNESTONE = register("runestone", StonesModBlocks.RUNESTONE, RunestoneBlockEntity::new);

    public static final RegistryObject<BlockEntityType<ResonanceBoxBlockEntity>> RESONANCE_BOX = REGISTRY.register("resonance_box",
        () -> BlockEntityType.Builder.of(ResonanceBoxBlockEntity::new, StonesModBlocks.RESONANCE_BOX.get()).build(null));

	private static RegistryObject<BlockEntityType<?>> register(String registryname, RegistryObject<Block> block, BlockEntityType.BlockEntitySupplier<?> supplier) {
		return REGISTRY.register(registryname, () -> BlockEntityType.Builder.of(supplier, block.get()).build(null));
	}
}
