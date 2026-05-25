package net.stones.init;

import net.stones.StonesMod;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.BlockItem;
import net.stones.item.*;
import net.minecraft.world.item.Rarity;

public class StonesModItems {
	public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(ForgeRegistries.ITEMS, StonesMod.MODID);
	
    public static final RegistryObject<Item> RUNESTONE = block(StonesModBlocks.RUNESTONE);
	
    public static final RegistryObject<Item> RESONANCE_BOX = REGISTRY.register("resonance_box", 
        () -> new ResonanceBoxBlockItem(StonesModBlocks.RESONANCE_BOX.get(), new Item.Properties().rarity(Rarity.EPIC)));
        
    public static final RegistryObject<Item> RUNE_MINOR = REGISTRY.register("rune_minor", 
        () -> new StoneItem(StoneItem.Type.MINOR));
    public static final RegistryObject<Item> RUNE_MAJOR = REGISTRY.register("rune_major", 
        () -> new StoneItem(StoneItem.Type.MAJOR));
    public static final RegistryObject<Item> RUNE_MILESTONE = REGISTRY.register("rune_milestone", 
        () -> new StoneItem(StoneItem.Type.MILESTONE));
		
    public static final RegistryObject<Item> CLUSTER_JEWEL_MINOR = REGISTRY.register("cluster_jewel_minor", 
        () -> new ClusterJewelItem(StoneItem.Type.MINOR, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
        
    public static final RegistryObject<Item> CLUSTER_JEWEL_MAJOR = REGISTRY.register("cluster_jewel_major", 
        () -> new ClusterJewelItem(StoneItem.Type.MAJOR, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
        
    public static final RegistryObject<Item> CLUSTER_JEWEL_MILESTONE = REGISTRY.register("cluster_jewel_milestone", 
        () -> new ClusterJewelItem(StoneItem.Type.MILESTONE, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));


	private static RegistryObject<Item> block(RegistryObject<Block> block) {
		return REGISTRY.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
	}
}