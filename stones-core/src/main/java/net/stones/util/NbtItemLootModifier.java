package net.stones.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.registries.ForgeRegistries;

public class NbtItemLootModifier extends LootModifier {

    public static final Codec<NbtItemLootModifier> CODEC = RecordCodecBuilder.create(inst -> 
        codecStart(inst).and(inst.group(
            ForgeRegistries.ITEMS.getCodec().fieldOf("item").forGetter(m -> m.item),
            Codec.INT.optionalFieldOf("count", 1).forGetter(m -> m.count),
            Codec.STRING.optionalFieldOf("nbt", "").forGetter(m -> m.nbtString)
        )).apply(inst, NbtItemLootModifier::new)
    );

    private final Item item;
    private final int count;
    private final String nbtString;

    public NbtItemLootModifier(LootItemCondition[] conditionsIn, Item item, int count, String nbtString) {
        super(conditionsIn);
        this.item = item;
        this.count = count;
        this.nbtString = nbtString;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        ItemStack stack = new ItemStack(this.item, this.count);
        
        // NBT-Daten aus dem String parsen und auf das Item anwenden
        if (!this.nbtString.isEmpty()) {
            try {
                stack.setTag(TagParser.parseTag(this.nbtString));
            } catch (CommandSyntaxException e) {
                System.err.println("Fehler beim Parsen der NBT-Daten im LootModifier: " + this.nbtString);
            }
        }
        
        generatedLoot.add(stack);
        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}