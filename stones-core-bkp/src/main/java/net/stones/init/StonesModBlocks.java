
/*
 *    MCreator note: This file will be REGENERATED on each build.
 * heey you found me :)
 *DO NOT DELETE! ITS A RELIC *.+ ****
 * It is here since the beginning of the mod. (made the initial blocks and Items via MCreator to save some time)
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
}