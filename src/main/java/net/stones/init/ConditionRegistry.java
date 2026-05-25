package net.stones.init;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.stones.enchantment.behavior.ActionContext;
import net.stones.enchantment.behavior.RuneCondition;
import net.stones.StonesMod;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import java.util.*;
import java.util.function.Function;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.effect.MobEffect;

public class ConditionRegistry {
    
    private static final Map<String, ConditionFactory> CONDITIONS = new HashMap<>();
    
    @FunctionalInterface
    public interface ConditionFactory {
        RuneCondition create(JsonObject params);
    }
    
    public static void register(String id, ConditionFactory factory) {
        CONDITIONS.put(id, factory);
    }
    
    public static RuneCondition create(String id, JsonObject params) {
        ConditionFactory factory = CONDITIONS.get(id);
        if (factory == null) return null;
        return factory.create(params);
    }

    // --- HOCHPERFORMANTE RESOLVER (Wird nur beim Laden der Rune ausgeführt) ---

    private static Function<ActionContext, Float> getFloatResolver(JsonObject params, String key, float def) {
        if (!params.has(key)) return ctx -> def;
        JsonElement el = params.get(key);
        
        // 1. Fall: Es ist eine Variable (z.B. "$t")
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String str = el.getAsString();
            if (str.startsWith("$")) {
                String varName = str.substring(1);
                return ctx -> {
                    Object val = ctx.getVariable(varName);
                    return (val instanceof Number n) ? n.floatValue() : def;
                };
            }
            // Fallback: String ist eine Zahl als Text
            try {
                float f = Float.parseFloat(str);
                return ctx -> f;
            } catch (NumberFormatException e) { return ctx -> def; }
        }
        
        // 2. Fall: Es ist eine direkte Zahl im JSON (Statisch)
        float staticVal = el.getAsFloat();
        return ctx -> staticVal;
    }

    private static Function<ActionContext, Object> getObjectResolver(JsonObject params, String key) {
        if (!params.has(key)) return ctx -> null;
        JsonElement el = params.get(key);
        
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String str = el.getAsString();
            if (str.startsWith("$")) {
                String varName = str.substring(1);
                return ctx -> ctx.getVariable(varName);
            }
            return ctx -> str;
        }
        
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            float f = el.getAsFloat();
            return ctx -> f;
        }
        
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) {
            boolean b = el.getAsBoolean();
            return ctx -> b;
        }
        
        return ctx -> null;
    }

    public static void init() {
        
        register("stones:is_raining", params -> new RuneCondition() {
            @Override public String getId() { return "stones:is_raining"; }
            @Override public boolean test(ActionContext ctx) { return ctx.getPlayer().level().isRaining(); }
        });

        register("stones:is_thundering", params -> new RuneCondition() {
            @Override public String getId() { return "stones:is_thundering"; }
            @Override public boolean test(ActionContext ctx) { return ctx.getPlayer().level().isThundering(); }
        });

        register("stones:is_on_fire", params -> new RuneCondition() {
            @Override public String getId() { return "stones:is_on_fire"; }
            @Override public boolean test(ActionContext ctx) { return ctx.getPlayer().getRemainingFireTicks() > 0; }
        });
    
        register("stones:health_below", params -> {
            // Resolver wird nur EINMAL beim Erstellen der Bedingung erzeugt
            final var resolver = getFloatResolver(params, "percent", 0.5f);
            return new RuneCondition() {
                @Override public String getId() { return "stones:health_below"; }
                @Override public boolean test(ActionContext ctx) {
                    return (ctx.getPlayer().getHealth() / ctx.getPlayer().getMaxHealth()) <= resolver.apply(ctx);
                }
            };
        });

        register("stones:chance", params -> {
            final var resolver = getFloatResolver(params, "value", 0.5f);
            return new RuneCondition() {
                @Override public String getId() { return "stones:chance"; }
                @Override public boolean test(ActionContext ctx) {
                    return ctx.getPlayer().getRandom().nextFloat() < resolver.apply(ctx);
                }
            };
        });
        
        register("stones:is_day", params -> new RuneCondition() {
            @Override public String getId() { return "stones:is_day"; }
            @Override public boolean test(ActionContext ctx) { return ctx.getPlayer().level().isDay(); }
        });

        register("stones:variable_compare", params -> {
            final String varName = params.get("variable").getAsString();
            final String operator = params.has("operator") ? params.get("operator").getAsString() : ">";
            final var valueResolver = getObjectResolver(params, "value");
            
            return new RuneCondition() {
                @Override public String getId() { return "stones:variable_compare"; }
                @Override public boolean test(ActionContext ctx) {
                    Object objA = ctx.getVariable(varName);
                    Object objB = valueResolver.apply(ctx);

                    if (operator.equals("!=")) return !Objects.equals(objA, objB);
                    if (operator.equals("==")) return Objects.equals(objA, objB);

                    float valA = (objA instanceof Number n) ? n.floatValue() : 0.0f;
                    float valB = (objB instanceof Number n) ? n.floatValue() : 0.0f;

                    return switch (operator) {
                        case ">"  -> valA > valB;
                        case "<"  -> valA < valB;
                        case ">=" -> valA >= valB;
                        case "<=" -> valA <= valB;
                        default   -> false;
                    };
                }
            };
        });

        register("stones:has_air", params -> {
            final var resolver = getFloatResolver(params, "min", 1.0f);
            return new RuneCondition() {
                @Override public String getId() { return "stones:has_air"; }
                @Override public boolean test(ActionContext ctx) { 
                    return ctx.getPlayer().getAirSupply() >= resolver.apply(ctx).intValue(); 
                }
            };
        });

        register("stones:persistent_var_compare", params -> {
            final String pVarName = params.get("name").getAsString();
            final String op = params.get("operator").getAsString();
            final var valResolver = getFloatResolver(params, "value", 0.0f);

            return new RuneCondition() {
                @Override public String getId() { return "stones:persistent_var_compare"; }
                @Override public boolean test(ActionContext ctx) {
                    float cur = ctx.getPlayer().getPersistentData().getFloat("stones_" + pVarName);
                    float target = valResolver.apply(ctx);
                    return switch (op) {
                        case ">" -> cur > target;
                        case "<" -> cur < target;
                        case "<=" -> cur <= target;
                        default -> cur >= target;
                    };
                }
            };
        });
        
        register("stones:is_ready", params -> new RuneCondition() {
            @Override public String getId() { return "stones:is_ready"; }
            @Override public boolean test(ActionContext context) {
                String name = context.getRuneId();
                ResourceLocation effectId = new ResourceLocation(StonesMod.MODID, "cooldown_" + name);
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectId);
                return effect == null || !context.getPlayer().hasEffect(effect);
            }
        });
        
        register("stones:has_enchantment", params -> {
            final String targetName = params.has("target") ? params.get("target").getAsString() : "attacker";
            return new RuneCondition() {
                @Override public String getId() { return "stones:has_enchantment"; }
                @Override public boolean test(ActionContext ctx) {
                    LivingEntity target = ctx.getVariable(targetName, LivingEntity.class);
                    if (target == null && targetName.equals("player")) target = ctx.getPlayer();
                    if (target != null) {
                        ItemStack mainHand = target.getMainHandItem();
                        return !mainHand.isEmpty() && mainHand.isEnchanted();
                    }
                    return false;
                }
            };
        });
        
        register("stones:block_check", params -> {
            // Komplexe Listen werden EINMAL beim Laden extrahiert
            final Set<String> allowedBlocks = new HashSet<>();
            if (params.has("blocks")) {
                for (JsonElement e : params.getAsJsonArray("blocks")) allowedBlocks.add(e.getAsString());
            }
            if (params.has("block")) allowedBlocks.add(params.get("block").getAsString());

            final List<TagKey<Block>> allowedTags = new ArrayList<>();
            if (params.has("tags")) {
                for (JsonElement e : params.getAsJsonArray("tags")) {
                    allowedTags.add(BlockTags.create(new ResourceLocation(e.getAsString())));
                }
            }

            return new RuneCondition() {
                @Override public String getId() { return "stones:block_check"; }
                @Override public boolean test(ActionContext ctx) {
                    // params wird hier nur für den Raycast-Abstand genutzt, falls nötig
                    BlockPos pos = MilestoneActionRegistry.getTargetPos(ctx, params);
                    if (pos == null) return false;

                    BlockState state = ctx.getPlayer().level().getBlockState(pos);
                    ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
                    if (blockId == null) return false;

                    if (allowedBlocks.contains(blockId.toString())) return true;
                    for (var tag : allowedTags) {
                        if (state.is(tag)) return true;
                    }
                    return false;
                }
            };
        });
    }
}