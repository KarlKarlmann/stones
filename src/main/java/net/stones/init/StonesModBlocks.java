
/*
 *    MCreator note: This file will be REGENERATED on each build.
 */
package net.stones.init;

import net.stones.block.*;
import net.stones.StonesMod;

import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
public class StonesModBlocks {
	public static final DeferredRegister<Block> REGISTRY = DeferredRegister.create(ForgeRegistries.BLOCKS, StonesMod.MODID);
	public static final RegistryObject<Block> RUNESTONE = REGISTRY.register("runestone", () -> new RunestoneBlock());
	public static final RegistryObject<Block> RESONANCE_BOX = REGISTRY.register("resonance_box", 
		() -> new ResonanceBoxBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_PURPLE)
			.strength(2.0f)
			.sound(SoundType.WOOD)
			.noOcclusion()));
}