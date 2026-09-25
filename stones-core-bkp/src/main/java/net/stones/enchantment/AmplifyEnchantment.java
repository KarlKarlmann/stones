package net.stones.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.stones.item.StoneItem;

/**
 * Das Amplify Enchantment verstärkt die Effekte der Runen, in denen es gesockelt ist.
 * Es skaliert am Zaubertisch nur bis 30, kann aber in Drops bis 100 vorkommen.
 */
public class AmplifyEnchantment extends Enchantment {
    
    public static final EnchantmentCategory RUNE_CATEGORY = EnchantmentCategory.create("RUNE_STONE", item -> item instanceof StoneItem);

    public AmplifyEnchantment() {
        // Wir nutzen Rarity.COMMON, damit die XP-Kosten am Amboss niedrig bleiben.
        // Wir nutzen alle EquipmentSlots, um Kombinationen im Amboss-Slot nicht zu blockieren.
        super(Rarity.COMMON, RUNE_CATEGORY, EquipmentSlot.values());
    }

    @Override
    public int getMaxLevel() {
        // Am Zaubertisch wird dieser Wert als Limit genommen.
        return 100;
    }


    @Override
    public int getMinCost(int level) {
        // Minecraft Zaubertisch Logik:
        // Ein Level-30 Slot am Zaubertisch kann nur Verzauberungen wählen, 
        // deren minCost <= 30 ist. 
        // Indem wir die Kosten linear zum Level setzen, wird Level 31 unmöglich am Tisch.
        return 1 + (level - 1) * 3; 
    }

    @Override
    public int getMaxCost(int level) {
        // Standard-Spanne für die Generierung
        return this.getMinCost(level) + 10;
    }

    @Override
    public boolean isTradeable() {
        return false;
    }

    @Override
    public boolean isDiscoverable() {
        return false;
    }

    @Override
    public boolean isAllowedOnBooks() { return false; }
    /**
     * Berechnet den Multiplikator basierend auf dem Amplify-Level.
     * Formel: 1 + maxBonus * (1 - e^(-amplify / k))
     */
    public static double getMultiplier(int amplifyLevel) {
        if (amplifyLevel <= 0) return 1.0;
        
        double maxBonus = 1.0; // Maximal 100% zusätzliche Stärke (insgesamt 2.0x)
        double k = 50.0;       // Streckungsfaktor der Kurve
        
        return 1.0 + maxBonus * (1.0 - Math.exp(-amplifyLevel / k));
    }
}