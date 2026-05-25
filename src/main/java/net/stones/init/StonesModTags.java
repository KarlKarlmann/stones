package net.stones.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.stones.StonesMod;

public class StonesModTags {
    // Definiert tags unter data/stones/tags/items/runes/
    public static final TagKey<Item> RUNE_MINOR = ItemTags.create(new ResourceLocation(StonesMod.MODID, "runes/minor"));
    public static final TagKey<Item> RUNE_MAJOR = ItemTags.create(new ResourceLocation(StonesMod.MODID, "runes/major"));
    public static final TagKey<Item> RUNE_MILESTONE = ItemTags.create(new ResourceLocation(StonesMod.MODID, "runes/milestone"));
}