package net.stones.enchantment;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.stones.enchantment.behavior.RuneBehavior;
import net.stones.item.StoneItem;
import java.lang.StackWalker;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Erweiterte Verzauberung für Runensteine.
 * Unterstützt Attribute, Effekte, Behaviors und dynamische Statistiken.
 * Beinhaltet das zentrale DICT-System für flexible Übersetzungen.
 */
public class RuneEnchantment extends Enchantment {

    public enum Type { MINOR, MAJOR, MILESTONE }

    public final Type type;
    
    @Nullable public final Attribute targetAttribute;
    @Nullable public final AttributeModifier.Operation operation;
    @Nullable public final MobEffect targetEffect;
    
    public final double factor;
    public final float baseRequiredLevel;
    private final String customName;
    private final String customDescription;
    private final String iconPath;
    private static final StackWalker STACK_WALKER = StackWalker.getInstance();
    private final List<RuneStat> stats = new ArrayList<>();
    private final List<RuneBehavior> behaviors = new ArrayList<>();
    private int maxLevel = 20;
    
    public static final EnchantmentCategory RUNE_CATEGORY = EnchantmentCategory.create("RUNE_STONE", item -> item instanceof StoneItem);

    public RuneEnchantment(Type type, Attribute attribute, AttributeModifier.Operation operation, double factor, @Nullable String customName, @Nullable String customDescription, @Nullable String iconPath, float baseRequiredLevel) {
        super(Rarity.COMMON, RUNE_CATEGORY, EquipmentSlot.values());
        this.type = type;
        this.targetAttribute = attribute;
        this.operation = operation;
        this.targetEffect = null;
        this.factor = factor;
        this.customName = customName;
        this.customDescription = customDescription;
        this.iconPath = iconPath;
        this.baseRequiredLevel = baseRequiredLevel;
    }

    public RuneEnchantment(Type type, MobEffect effect, double amplifier, @Nullable String customName, @Nullable String customDescription, @Nullable String iconPath, float baseRequiredLevel) {
        super(Rarity.COMMON, RUNE_CATEGORY, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
        this.type = type;
        this.targetAttribute = null;
        this.operation = null;
        this.targetEffect = effect;
        this.factor = amplifier;
        this.customName = customName;
        this.customDescription = customDescription;
        this.iconPath = iconPath;
        this.baseRequiredLevel = baseRequiredLevel;
    }
    
    /**
     * Zentrales System zur Auflösung von Strings aus der JSON.
     * Nutzt "DICT:" für Übersetzungen (Translatable), ansonsten wird der Text als Literal übernommen.
     */
    public static Component resolveComponent(@Nullable String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        
        if (input.startsWith("DICT:")) {
            return Component.translatable(input.substring(5));
        }
        
        return Component.literal(input);
    }
    
    public void addStat(RuneStat stat) { 
        this.stats.add(stat); 
    }

    @Override
    public int getMinCost(int level) {
        return 1 + (level - 1) * 3; 
    }

    @Override
    public int getMaxCost(int level) {
        return this.getMinCost(level) + 10;
    }
    
    public List<RuneStat> getStats() { 
        return stats; 
    }

    public void addBehavior(RuneBehavior behavior) { 
        this.behaviors.add(behavior); 
    }

    public List<RuneBehavior> getBehaviors() { 
        return behaviors; 
    }
    
    public Component getCustomDescription(int level) { 
        if (this.customDescription == null || this.customDescription.isEmpty()) return Component.empty();
        
        String romanLevel = Component.translatable("enchantment.level." + level).getString();
        
        if (this.customDescription.startsWith("DICT:")) {
            return Component.translatable(this.customDescription.substring(5), level, romanLevel);
        }
        
        String replaced = this.customDescription
                .replace("%level%", String.valueOf(level))
                .replace("%lvl%", String.valueOf(level))
                .replace("%roman%", romanLevel);
                
        return Component.literal(replaced);
    }
    
    @Override
    public boolean isAllowedOnBooks() { return false; }
    
    @Nullable public String getIconPath() { return iconPath; }
    
    @Override
    public Component getFullname(int level) {
        MutableComponent name = resolveComponent(this.customName).copy();
        if (level != 1 || this.getMaxLevel() != 1) {
            name.append(" ").append(Component.translatable("enchantment.level." + level));
        }
        return name;
    }

	public double calculateBonus(int runeLevel, int playerLevel, int socketLevel) {
        if (type == Type.MINOR) {
            return runeLevel * factor;
        } else if (type == Type.MAJOR) {
            int levelDiff = Math.max(0, playerLevel - socketLevel);
            return levelDiff * factor * runeLevel; // <-- runeLevel hinzugefügt!
        }
        return 0;
    }

    @Override public boolean isTradeable() { return false; }
    
	@Override
    public boolean isDiscoverable() {
        return STACK_WALKER.walk(frames -> frames.noneMatch(frame -> {
            String className = frame.getClassName();
            return className.contains("LootItemEnchantRandomlyFunction") || 
                   className.contains("EnchantRandomly");
        }));
    }

    public void setMaxLevel(int maxLevel) {
        this.maxLevel = maxLevel;
    }

    @Override
    public int getMaxLevel() {
        return this.maxLevel;
    }
}