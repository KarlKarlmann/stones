package net.stones.util;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.stones.enchantment.RuneEnchantment;
import net.stones.init.StonesModItems;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Kern-Logik für die Loot-Generierung.
 * Erzeugt Runen, mischt Vanilla/Mod Chest-Loot und eskaliert im Endgame.
 */
public class RuneGenerator {

    /**
     * Erzeugt eine spezifische Rune eines Typs mit Level-Scaling.
     */
    public static ItemStack generateRune(RuneEnchantment.Type type, int minLevel, int maxLevel, RandomSource random) {
        ItemStack stack = new ItemStack(switch (type) {
            case MAJOR -> StonesModItems.RUNE_MAJOR.get();
            case MILESTONE -> StonesModItems.RUNE_MILESTONE.get();
            default -> StonesModItems.RUNE_MINOR.get();
        });

        List<Enchantment> validEnchants = ForgeRegistries.ENCHANTMENTS.getValues().stream()
                .filter(e -> e instanceof RuneEnchantment r && r.type == type)
                .collect(Collectors.toList());

        if (!validEnchants.isEmpty()) {
            Enchantment selected = validEnchants.get(random.nextInt(validEnchants.size()));
            int level = minLevel + random.nextInt(maxLevel - minLevel + 1);
            stack.enchant(selected, level);
        }

        return stack;
    }

    /**
     * Erzeugt das komplette Loot-Paket für eine Resonanz-Box (Skaliert bis Level 15+).
     */
    public static List<ItemStack> generateBoxLoot(int tier, ServerLevel level, Vec3 pos) {
        List<ItemStack> loot = new ArrayList<>();
        RandomSource random = level.getRandom();

        // ==========================================
        // 1. RUNEN GENERIEREN (Garantierter Mod-Loot)
        // ==========================================
        int numRunes = 1 + random.nextInt(1 + (tier / 2)); // Level 10 -> bis zu 6 Runen!
        
        for (int i = 0; i < numRunes; i++) {
            RuneEnchantment.Type type;
            float roll = random.nextFloat();
            
            // Qualitätschancen extrem nach oben skaliert
            if (tier >= 10) {
                if (roll < 0.6f) type = RuneEnchantment.Type.MILESTONE;
                else if (roll < 0.9f) type = RuneEnchantment.Type.MAJOR;
                else type = RuneEnchantment.Type.MINOR;
            } else if (tier >= 5) {
                if (roll < 0.3f) type = RuneEnchantment.Type.MILESTONE;
                else if (roll < 0.7f) type = RuneEnchantment.Type.MAJOR;
                else type = RuneEnchantment.Type.MINOR;
            } else {
                if (roll < 0.1f) type = RuneEnchantment.Type.MAJOR;
                else type = RuneEnchantment.Type.MINOR;
            }
            
            int minLvl = Math.max(1, tier - 2);
            int maxLvl = tier + 3; // Lvl 15 Box = Runen bis Lvl 18
            loot.add(generateRune(type, minLvl, maxLvl, random));
        }

        // ==========================================
        // 2. MIX AUS ZWEI LOOT-TABELLEN (Varianz)
        // ==========================================
        List<ResourceLocation> pool1 = new ArrayList<>();
        List<ResourceLocation> pool2 = new ArrayList<>();

        if (tier >= 10) {
            pool1.addAll(List.of(BuiltInLootTables.ANCIENT_CITY, BuiltInLootTables.END_CITY_TREASURE));
            pool2.addAll(List.of(BuiltInLootTables.BASTION_TREASURE, BuiltInLootTables.WOODLAND_MANSION));
        } else if (tier >= 7) {
            pool1.addAll(List.of(BuiltInLootTables.END_CITY_TREASURE, BuiltInLootTables.STRONGHOLD_LIBRARY));
            pool2.addAll(List.of(BuiltInLootTables.ANCIENT_CITY, BuiltInLootTables.BURIED_TREASURE));
        } else if (tier >= 4) {
            pool1.addAll(List.of(BuiltInLootTables.BASTION_TREASURE, BuiltInLootTables.DESERT_PYRAMID));
            pool2.addAll(List.of(BuiltInLootTables.SHIPWRECK_TREASURE, BuiltInLootTables.IGLOO_CHEST));
        } else {
            pool1.addAll(List.of(BuiltInLootTables.ABANDONED_MINESHAFT, BuiltInLootTables.SIMPLE_DUNGEON));
            pool2.addAll(List.of(BuiltInLootTables.RUINED_PORTAL, BuiltInLootTables.PILLAGER_OUTPOST));
        }

        ResourceLocation tableId1 = pool1.get(random.nextInt(pool1.size()));
        ResourceLocation tableId2 = pool2.get(random.nextInt(pool2.size()));

        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, pos)
                .create(LootContextParamSets.CHEST);

        List<ItemStack> list1 = level.getServer().getLootData().getLootTable(tableId1).getRandomItems(params);
        List<ItemStack> list2 = level.getServer().getLootData().getLootTable(tableId2).getRandomItems(params);

        List<ItemStack> combinedVanillaLoot = new ArrayList<>();
        combinedVanillaLoot.addAll(list1);
        combinedVanillaLoot.addAll(list2);

        // Mischen und exakt die Hälfte der Items nehmen für extreme Unberechenbarkeit
        Collections.shuffle(combinedVanillaLoot, new java.util.Random(random.nextLong()));
        int halfSize = Math.max(1, combinedVanillaLoot.size() / 2);
        for (int i = 0; i < halfSize; i++) {
            loot.add(combinedVanillaLoot.get(i));
        }

        if (tier >= 10) {
            // Lade ALLE registrierten Items (Minecraft Vanilla + Alle Mods)
            List<Item> allItems = ForgeRegistries.ITEMS.getValues().stream()
                    .filter(item -> item != Items.AIR)
                    .toList();

            // Je höher das Tier über 10, desto mehr wilde Items
            int crazyItemsCount = 3 + random.nextInt(tier); // z.B. bei Tier 15: 3 bis 17 irre Items

            for (int i = 0; i < crazyItemsCount; i++) {
                Item randomItem = allItems.get(random.nextInt(allItems.size()));
                ItemStack crazyStack = new ItemStack(randomItem);
                
                // Setze eine zufällige Menge bis zum Stack-Maximum
                int maxSize = crazyStack.getMaxStackSize();
                if (maxSize > 1) {
                    crazyStack.setCount(1 + random.nextInt(maxSize));
                }

				if (crazyStack.isEnchantable()) {
                    // Verzauberungs-Level skaliert absurd in die Höhe (30 bis 100+)
                    int crazyEnchantLevel = 30 + random.nextInt(tier * 5);
                    try {
                        // FIX: Try-Catch fängt kaputte Fremd-Mods ab, die keine hohen Level vertragen
                        crazyStack = EnchantmentHelper.enchantItem(random, crazyStack, crazyEnchantLevel, true);
                    } catch (Exception e) {
                    }
                }
                
                loot.add(crazyStack);
            }
        }

        // ==========================================
        // 4. INVENTAR-LIMITIERUNG (27 Slots)
        // ==========================================
        // Runen wurden zuerst hinzugefügt, sind also sicher. 
        // Überschüssiger "Beifang" fällt weg.
        if (loot.size() > 27) {
            loot = loot.subList(0, 27);
        }

        // Als letzte Tat mischen wir alles nochmal durch, damit die Runen
        // nicht immer ganz vorne in der Truhe liegen.
        Collections.shuffle(loot, new java.util.Random(random.nextLong()));

        return loot;
    }
}