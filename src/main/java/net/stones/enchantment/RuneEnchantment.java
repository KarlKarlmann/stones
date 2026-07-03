package net.stones.enchantment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.ChatFormatting;
import net.stones.enchantment.behavior.RuneAction;
import net.stones.enchantment.behavior.RuneBehavior;
import net.stones.enchantment.behavior.RuneCondition;
import net.stones.enchantment.behavior.TriggerType;
import net.stones.init.ConditionRegistry;
import net.stones.init.MilestoneActionRegistry;
import net.stones.item.StoneItem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.stones.enchantment.behavior.ActionContext;

import javax.annotation.Nullable;
import java.lang.StackWalker;
import java.util.ArrayList;
import java.util.List;

public class RuneEnchantment extends Enchantment {

    private static final Logger LOGGER = LogManager.getLogger();

    public enum Type { MINOR, MAJOR, MILESTONE }

    // Mutabel für das dynamische Datapack Loading
    public Type type;
    
    // Server-spezifischer Bindungs-Zustand
    private boolean isAwake = false;
    private boolean hasServerLogic = false; // Guard für den Singleplayer-Bug!
    private String logicalId; 
    
    @Nullable public Attribute targetAttribute;
    @Nullable public AttributeModifier.Operation operation;
    @Nullable public MobEffect targetEffect; 
    
    public double factor;
    public float baseRequiredLevel;
    private boolean discoverable;
    private boolean isCurseFlag; // Bestimmt, ob diese Rune ein Fluch ist
    private String customName;
    private String customDescription;
    private String iconPath;
    
    private final List<RuneStat> stats = new ArrayList<>();
    private final List<RuneBehavior> behaviors = new ArrayList<>();
    private int maxLevel = 20;
    
    private static final StackWalker STACK_WALKER = StackWalker.getInstance();
    public static final EnchantmentCategory RUNE_CATEGORY = EnchantmentCategory.create("RUNE_STONE", item -> item instanceof StoneItem);

    // 1. Konstruktor: Parameterlose Hülle für dynamische Slots (startet im Schlaf)
    public RuneEnchantment(Type type) {
        super(Rarity.COMMON, RUNE_CATEGORY, EquipmentSlot.values());
        this.type = type;
        this.sleep(); 
    }

    // 2. Konstruktor: Für programmatische Attribute-Verzauberungen
    public RuneEnchantment(Type type, Attribute attribute, AttributeModifier.Operation operation, double factor, @Nullable String customName, @Nullable String customDescription, @Nullable String iconPath, float baseRequiredLevel, boolean discoverable) {
        super(Rarity.COMMON, RUNE_CATEGORY, EquipmentSlot.values());
        this.type = type;
        this.targetAttribute = attribute;
        this.operation = operation;
        this.factor = factor;
        this.customName = customName;
        this.customDescription = customDescription;
        this.iconPath = iconPath;
        this.baseRequiredLevel = baseRequiredLevel;
        this.discoverable = discoverable;
        this.isCurseFlag = false;
        this.hasServerLogic = true; 
        this.isAwake = true; 
    }

    // 3. Konstruktor: Für programmatische MobEffect-Verzauberungen
    public RuneEnchantment(Type type, MobEffect effect, double amplifier, @Nullable String customName, @Nullable String customDescription, @Nullable String iconPath, float baseRequiredLevel, boolean discoverable) {
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
        this.discoverable = discoverable;
        this.isCurseFlag = false;
        this.hasServerLogic = true; 
        this.isAwake = true; 
    }

    public void sleep() {
        this.isAwake = false;
        this.hasServerLogic = false; 
        this.logicalId = null;
        this.targetAttribute = null;
        this.operation = null;
        this.targetEffect = null;
        this.factor = 0.0;
        this.baseRequiredLevel = 1.0f;
        this.discoverable = false;
        this.isCurseFlag = false;
        this.customName = null;
        this.customDescription = null;
        this.iconPath = null;
        this.stats.clear();
        this.behaviors.clear();
        this.maxLevel = 1;
    }

    public void setAwake(boolean awake) {
        this.isAwake = awake;
    }

    public String getLogicalId() {
        if (this.logicalId != null) {
            return this.logicalId;
        }
        ResourceLocation key = ForgeRegistries.ENCHANTMENTS.getKey(this);
        return key != null ? key.getPath() : "unknown";
    }

    public void addStat(RuneStat stat) {
        this.stats.add(stat);
    }
    
    public void addBehavior(RuneBehavior behavior) {
        this.behaviors.add(behavior);
    }

    // --- Exakt an das Original Parsing angelehnt ---
    public void loadFromJson(String id, JsonObject json) {
        this.logicalId = id; 

        if (json.has("type")) {
            this.type = Type.valueOf(json.get("type").getAsString().toUpperCase());
        }

        this.customName = json.has("name") ? json.get("name").getAsString() : null;
        this.customDescription = json.has("description") ? json.get("description").getAsString() : null;
        this.iconPath = json.has("icon") ? json.get("icon").getAsString() : null;
        this.factor = json.has("factor") ? json.get("factor").getAsDouble() : 0.0;
        this.baseRequiredLevel = json.has("required_level") ? json.get("required_level").getAsFloat() : 5.0f;
        this.discoverable = !json.has("discoverable") || json.get("discoverable").getAsBoolean();
        this.isCurseFlag = json.has("is_curse") && json.get("is_curse").getAsBoolean();
        
        if (json.has("attribute")) {
            String attrStr = json.get("attribute").getAsString();
            this.targetAttribute = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(attrStr));
            if (this.targetAttribute != null && json.has("operation")) {
                this.operation = AttributeModifier.Operation.valueOf(json.get("operation").getAsString().toUpperCase());
            }
        } else if (json.has("effect")) { 
            String effStr = json.get("effect").getAsString();
            this.targetEffect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(effStr));
        }

        if (json.has("stats")) {
            JsonArray statsArray = json.getAsJsonArray("stats");
            for (JsonElement e : statsArray) {
                JsonObject sObj = e.getAsJsonObject();
                this.addStat(new RuneStat(
                    sObj.get("id").getAsString(),
                    sObj.get("label").getAsString(),
                    sObj.has("type") ? sObj.get("type").getAsString() : "generic",
                    sObj.get("base").getAsFloat(),
                    sObj.has("per_level") ? sObj.get("per_level").getAsFloat() : 0f,
                    sObj.has("scaling") ? sObj.get("scaling").getAsString() : "RUNE_LEVEL",
                    sObj.has("display_factor") ? sObj.get("display_factor").getAsFloat() : 1.0f,
                    sObj.has("suffix") ? sObj.get("suffix").getAsString() : "",
                    sObj.has("min") ? sObj.get("min").getAsFloat() : null,
                    sObj.has("max") ? sObj.get("max").getAsFloat() : null
                ));
            }
        }

        if (json.has("behaviors")) {
            JsonArray behaviorsArr = json.getAsJsonArray("behaviors");
            for (JsonElement el : behaviorsArr) {
                JsonObject bObj = el.getAsJsonObject();
                String trigStr = bObj.get("trigger").getAsString().toUpperCase();
                TriggerType trigger = TriggerType.get(trigStr);
                
                List<RuneCondition> conditionsList = new ArrayList<>();
                if (bObj.has("conditions")) {
                    JsonElement condElement = bObj.get("conditions");
                    if (condElement.isJsonArray()) {
                        for (JsonElement ce : condElement.getAsJsonArray()) parseAndAddCondition(ce.getAsJsonObject(), conditionsList);
                    } else if (condElement.isJsonObject()) {
                        parseAndAddCondition(condElement.getAsJsonObject(), conditionsList);
                    }
                }
                
                List<RuneBehavior.ConfiguredRuneAction> actionsList = new ArrayList<>();
                boolean hasCooldownAction = false;
                
                if (bObj.has("actions")) {
                    for (JsonElement actEl : bObj.getAsJsonArray("actions")) {
                        JsonObject actObj = actEl.getAsJsonObject();
                        if (actObj.has("type")) {
                            String actionType = actObj.get("type").getAsString();
                            if (actionType.equals("stones:cooldown")) {
                                hasCooldownAction = true;
                            }
                            RuneAction action = MilestoneActionRegistry.get(actionType);
                            if (action != null) {
                                actionsList.add(new RuneBehavior.ConfiguredRuneAction(action, actObj));
                            } else {
                                LOGGER.warn("[Stones] Unbekannte Aktion uebersprungen: " + actionType);
                            }
                        }
                    }
                }
                
                // Automatischer Sicherheits-Gurt für den CD
				if (hasCooldownAction && conditionsList.stream().noneMatch(c -> c.getId().equals("stones:is_ready"))) {
					JsonObject condObj = new JsonObject();
					condObj.addProperty("type", "stones:is_ready");
					
					// NEU: Suche den Namen in der Cooldown-Action und kopiere ihn!
					for (RuneBehavior.ConfiguredRuneAction act : actionsList) {
						if (act.action != null && act.action.getId().equals("stones:cooldown") && act.params.has("name")) {
							condObj.addProperty("name", act.params.get("name").getAsString());
							break;
						}
					}
					
					conditionsList.add(ConditionRegistry.create("stones:is_ready", condObj));
				}
                
                this.addBehavior(new RuneBehavior(trigger, conditionsList, actionsList));
            }
        }

        if (json.has("max_level")) {
            this.setMaxLevel(json.get("max_level").getAsInt());
        } else {
            this.setMaxLevel(20);
        }
        
        this.hasServerLogic = true; 
        this.isAwake = true; 
    }

    private void parseAndAddCondition(JsonObject json, List<RuneCondition> list) {
        if (json.has("type")) {
            String type = json.get("type").getAsString();
            RuneCondition condition = ConditionRegistry.create(type, json);
            if (condition != null) {
                list.add(condition);
            } else {
                LOGGER.warn("[Stones] Unbekannte Bedingung uebersprungen: " + type);
            }
        }
    }

    public static Component resolveComponent(@Nullable String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        if (input.startsWith("DICT:")) return Component.translatable(input.substring(5));
        return Component.literal(input);
    }
    
    public List<RuneStat> getStats() { return stats; }
    public List<RuneBehavior> getBehaviors() { return behaviors; }
    public boolean isAwake() { return isAwake; }
    
    @Override
    public boolean isCurse() {
        return this.isCurseFlag;
    }

    @Nullable public String getIconPath() { return iconPath; }
    @Override public boolean isAllowedOnBooks() { return false; }
    @Override public boolean isTradeable() { return isAwake; } 

    @Override
    public int getMinCost(int level) { return 1 + (level - 1) * 3; }
    @Override
    public int getMaxCost(int level) { return this.getMinCost(level) + 10; }
    @Override
    public int getMaxLevel() { return this.maxLevel; }
    public void setMaxLevel(int maxLevel) { this.maxLevel = maxLevel; }

    @Override
    public Component getFullname(int level) {
        if (!this.isAwake) {
            return Component.literal("Erloschene Resonanz").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH);
        }
        MutableComponent name = resolveComponent(this.customName).copy();
        
        // Fügt optional das Vanilla Rot hinzu, wenn es als Curse definiert wurde und keine eigene Farbe gesetzt ist
        if (this.isCurseFlag && name.getStyle().getColor() == null) {
            name.withStyle(ChatFormatting.RED);
        }
        
        if (level != 1 || this.getMaxLevel() != 1) {
            name.append(" ").append(Component.translatable("enchantment.level." + level));
        }
        return name;
    }

    public Component getCustomDescription(int level) { 
        if (!this.isAwake || this.customDescription == null || this.customDescription.isEmpty()) return Component.empty();
        String romanLevel = Component.translatable("enchantment.level." + level).getString();
        if (this.customDescription.startsWith("DICT:")) return Component.translatable(this.customDescription.substring(5), level, romanLevel);
        
        return Component.literal(this.customDescription
                .replace("%level%", String.valueOf(level))
                .replace("%lvl%", String.valueOf(level))
                .replace("%roman%", romanLevel));
    }

    public double calculateBonus(int runeLevel, int playerLevel, int socketLevel) {
        if (!this.isAwake) return 0;
        if (type == Type.MINOR) return runeLevel * factor;
        else if (type == Type.MAJOR) return Math.max(0, playerLevel - socketLevel) * factor * runeLevel;
        return 0;
    }

    @Override
    public boolean isDiscoverable() {
        if (!this.isAwake || !this.discoverable) return false;
        return STACK_WALKER.walk(frames -> frames.noneMatch(frame -> {
            String className = frame.getClassName();
            return className.contains("LootItemEnchantRandomlyFunction") || className.contains("EnchantRandomly");
        }));
    }

    // ==========================================
    // CLIENT/SERVER NETZWERK SYNCHRONISATION (NBT)
    // ==========================================
    
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("logicalId", this.logicalId != null ? this.logicalId : "");
        tag.putString("type", this.type.name());
        tag.putBoolean("isAwake", this.isAwake);
        if (this.targetAttribute != null) {
            tag.putString("attribute", ForgeRegistries.ATTRIBUTES.getKey(this.targetAttribute).toString());
        }
        if (this.operation != null) {
            tag.putInt("operation", this.operation.ordinal());
        }
        if (this.targetEffect != null) {
            tag.putString("effect", ForgeRegistries.MOB_EFFECTS.getKey(this.targetEffect).toString());
        }
        tag.putDouble("factor", this.factor);
        tag.putFloat("baseRequiredLevel", this.baseRequiredLevel);
        tag.putBoolean("discoverable", this.discoverable);
        tag.putBoolean("isCurseFlag", this.isCurseFlag);
        if (this.customName != null) tag.putString("customName", this.customName);
        if (this.customDescription != null) tag.putString("customDescription", this.customDescription);
        if (this.iconPath != null) tag.putString("iconPath", this.iconPath);
        tag.putInt("maxLevel", this.maxLevel);

        ListTag statsList = new ListTag();
        for (RuneStat stat : this.stats) {
            CompoundTag sTag = new CompoundTag();
            sTag.putString("id", stat.id());
            sTag.putString("label", stat.label());
            sTag.putString("type", stat.type());
            sTag.putFloat("base", stat.base());
            sTag.putFloat("perLevel", stat.perLevel());
            sTag.putString("scaling", stat.scaling());
            sTag.putFloat("displayFactor", stat.displayFactor());
            sTag.putString("suffix", stat.suffix());
            if (stat.min() != null) sTag.putFloat("min", stat.min());
            if (stat.max() != null) sTag.putFloat("max", stat.max());
            statsList.add(sTag);
        }
        tag.put("stats", statsList);

        ListTag behaviorsList = new ListTag();
        for (RuneBehavior behavior : this.behaviors) {
            CompoundTag bTag = new CompoundTag();
            bTag.putString("trigger", behavior.trigger.id); 
            
            ListTag actionsList = new ListTag();
            for (RuneBehavior.ConfiguredRuneAction act : behavior.actions) {
                CompoundTag actTag = new CompoundTag();
                if (act.action != null) {
                    actTag.putString("actionId", act.action.getId());
                    if (act.params != null && act.params.has("name")) {
                        actTag.putString("nameParam", act.params.get("name").getAsString());
                    }
                }
                actionsList.add(actTag);
            }
            bTag.put("actions", actionsList);
            behaviorsList.add(bTag);
        }
        tag.put("behaviors", behaviorsList);

        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        if (this.hasServerLogic) {
            return; 
        }

        this.sleep();
        this.isAwake = tag.getBoolean("isAwake");
        if (!this.isAwake) return;

        this.logicalId = tag.getString("logicalId");
        this.type = Type.valueOf(tag.getString("type"));
        if (tag.contains("attribute")) {
            this.targetAttribute = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(tag.getString("attribute")));
        }
        if (tag.contains("operation")) {
            this.operation = AttributeModifier.Operation.values()[tag.getInt("operation")];
        }
        if (tag.contains("effect")) {
            this.targetEffect = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(tag.getString("effect")));
        }
        this.factor = tag.getDouble("factor");
        this.baseRequiredLevel = tag.getFloat("baseRequiredLevel");
        this.discoverable = tag.getBoolean("discoverable");
        this.isCurseFlag = tag.getBoolean("isCurseFlag");
        if (tag.contains("customName")) this.customName = tag.getString("customName");
        if (tag.contains("customDescription")) this.customDescription = tag.getString("customDescription");
        if (tag.contains("iconPath")) this.iconPath = tag.getString("iconPath");
        this.maxLevel = tag.getInt("maxLevel");

        ListTag statsList = tag.getList("stats", 10);
        for (int i = 0; i < statsList.size(); i++) {
            CompoundTag sTag = statsList.getCompound(i);
            Float min = sTag.contains("min") ? sTag.getFloat("min") : null;
            Float max = sTag.contains("max") ? sTag.getFloat("max") : null;
            this.addStat(new RuneStat(
                sTag.getString("id"), sTag.getString("label"), sTag.getString("type"),
                sTag.getFloat("base"), sTag.getFloat("perLevel"), sTag.getString("scaling"),
                sTag.getFloat("displayFactor"), sTag.getString("suffix"), min, max
            ));
        }

        ListTag behaviorsList = tag.getList("behaviors", 10);
        for (int i = 0; i < behaviorsList.size(); i++) {
            CompoundTag bTag = behaviorsList.getCompound(i);
            TriggerType trigger = TriggerType.get(bTag.getString("trigger").toUpperCase());
            
            List<RuneBehavior.ConfiguredRuneAction> actionsList = new ArrayList<>();
            ListTag actionsNBT = bTag.getList("actions", 10);
            for (int j = 0; j < actionsNBT.size(); j++) {
                CompoundTag actNBT = actionsNBT.getCompound(j);
                String actionId = actNBT.getString("actionId");
                
                RuneAction dummyAction = new RuneAction() {
                    @Override public String getId() { return actionId; }
                    @Override public void execute(ActionContext ctx, JsonObject params) {}
                };
                
                JsonObject paramsObj = new JsonObject();
                if (actNBT.contains("nameParam")) {
                    paramsObj.addProperty("name", actNBT.getString("nameParam"));
                }
                
                actionsList.add(new RuneBehavior.ConfiguredRuneAction(dummyAction, paramsObj));
            }
            this.addBehavior(new RuneBehavior(trigger, new ArrayList<>(), actionsList));
        }
    }
}