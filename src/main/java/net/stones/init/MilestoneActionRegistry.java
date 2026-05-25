package net.stones.init;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.stones.StonesMod;
import net.stones.particle.XrayParticleOptions;
import net.stones.enchantment.behavior.ActionContext;
import net.stones.enchantment.behavior.RuneAction;
import net.stones.enchantment.behavior.reflection.ReflectionCallParser;
import net.stones.enchantment.behavior.reflection.ReflectionInvoker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.phys.HitResult;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import java.util.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.stones.enchantment.behavior.RuneCondition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class MilestoneActionRegistry {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<String, RuneAction> ACTIONS = new HashMap<>();

    public static void register(RuneAction action) { ACTIONS.put(action.getId(), action); }
    public static RuneAction get(String id) { return ACTIONS.get(id); }
    
    // --- SMART PARSING ENGINE (NUR NOCH $ SYNTAX) ---
    
    public static float resolveFloat(ActionContext ctx, JsonObject params, String key, float def) {
        if (!params.has(key)) return def;
        JsonElement el = params.get(key);
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String str = el.getAsString();
            if (str.startsWith("$")) {
                Object val = resolve(ctx, str.substring(1));
                if (val instanceof Number n) return n.floatValue();
                return def;
            }
            try { return Float.parseFloat(str); } catch (NumberFormatException e) { return def; }
        }
        return el.getAsFloat();
    }

    public static int resolveInt(ActionContext ctx, JsonObject params, String key, int def) {
        if (!params.has(key)) return def;
        JsonElement el = params.get(key);
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String str = el.getAsString();
            if (str.startsWith("$")) {
                Object val = resolve(ctx, str.substring(1));
                if (val instanceof Number n) return n.intValue();
                return def;
            }
            try { return Integer.parseInt(str); } catch (NumberFormatException e) { return def; }
        }
        return el.getAsInt();
    }

    public static String resolveString(ActionContext ctx, JsonObject params, String key, String def) {
        if (!params.has(key)) return def;
        String str = params.get(key).getAsString();
        if (str.startsWith("$")) {
            Object val = resolve(ctx, str.substring(1));
            return val != null ? val.toString() : def;
        }
        return str;
    }

    public static Object resolveObject(ActionContext ctx, JsonObject params, String key) {
        if (!params.has(key)) return null;
        JsonElement el = params.get(key);
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String str = el.getAsString();
            if (str.startsWith("$")) return resolve(ctx, str.substring(1));
            return str;
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) return el.getAsBoolean();
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) return el.getAsFloat();
        return null;
    }

    public static Object resolve(ActionContext ctx, String name) {
        if (name.equalsIgnoreCase("player")) return ctx.getPlayer();
        if (name.equalsIgnoreCase("level")) return ctx.getPlayer().level();
        
        String work = name.startsWith("$") ? name.substring(1) : name;
        
        if (!work.contains(".")) {
            Object val = ctx.getVariable(work);
            if (val == null) LOGGER.debug("[Stones] Variable '{}' wurde nicht im Kontext gefunden.", work);
            return val;
        }
        
        String[] parts = work.split("\\.");
        Object current = ctx.getVariable(parts[0]);

        for (int i = 1; i < parts.length; i++) {
            if (current == null) return null;
            Object next = ReflectionInvoker.resolveMember(current, parts[i]);
            current = next;
        }
        return current;
    }

    public static Vec3 resolveVec3(ActionContext ctx, JsonObject params) {
        Object raw = resolveObject(ctx, params, "pos");
        if (raw instanceof BlockPos bp) return Vec3.atCenterOf(bp);
        if (raw instanceof Vec3 v) return v;
        return ctx.getPlayer().position().add(0, 1.0, 0); // Fallback
    }

    public static BlockPos getTargetPos(ActionContext ctx, JsonObject params) {
        Object raw = resolveObject(ctx, params, "pos");
        if (raw instanceof BlockPos bp) return bp;
        if (raw instanceof Vec3 v) return BlockPos.containing(v);
        
        BlockPos pos = ctx.getVariable("blockPos", BlockPos.class);
        if (pos == null) {
            double dist = resolveFloat(ctx, params, "distance", 5.0f);
            HitResult hit = ctx.getPlayer().pick(dist, 0.0F, false);
            if (hit.getType() == HitResult.Type.BLOCK) {
                pos = ((BlockHitResult) hit).getBlockPos();
            }
        }
        return pos;
    }

    public static List<Object> resolveArgs(ActionContext ctx, List<ReflectionCallParser.Argument> raw) {
        List<Object> args = new ArrayList<>();
        for (ReflectionCallParser.Argument a : raw) {
            if (a.rawValue instanceof String s && s.startsWith("$")) {
                args.add(resolve(ctx, s.substring(1)));
            } else {
                args.add(a.rawValue);
            }
        }
        return args;
    }
	
    public static void executeActionList(ActionContext ctx, JsonArray actions) {
        if (actions == null) return;
        for (JsonElement e : actions) {
            JsonObject actObj = e.getAsJsonObject();
            RuneAction action = ACTIONS.get(actObj.get("type").getAsString());
            if (action != null) action.execute(ctx, actObj);
        }
    }
	
    private static void drawBox(ServerLevel sl, Vec3 p, XrayParticleOptions options) {
        double size = 1.0;
        for (double i = 0; i <= size; i += 0.25) {
            sl.sendParticles(options, p.x + i, p.y, p.z, 1, 0, 0, 0, 0);
            sl.sendParticles(options, p.x + i, p.y + size, p.z, 1, 0, 0, 0, 0);
            sl.sendParticles(options, p.x + i, p.y, p.z + size, 1, 0, 0, 0, 0);
            sl.sendParticles(options, p.x + i, p.y + size, p.z + size, 1, 0, 0, 0, 0);
            sl.sendParticles(options, p.x, p.y + i, p.z, 1, 0, 0, 0, 0);
            sl.sendParticles(options, p.x + size, p.y + i, p.z, 1, 0, 0, 0, 0);
            sl.sendParticles(options, p.x, p.y + i, p.z + size, 1, 0, 0, 0, 0);
            sl.sendParticles(options, p.x + size, p.y + i, p.z + size, 1, 0, 0, 0, 0);
            sl.sendParticles(options, p.x, p.y, p.z + i, 1, 0, 0, 0, 0);
            sl.sendParticles(options, p.x + size, p.y, p.z + i, 1, 0, 0, 0, 0);
            sl.sendParticles(options, p.x, p.y + size, p.z + i, 1, 0, 0, 0, 0);
            sl.sendParticles(options, p.x + size, p.y + size, p.z + i, 1, 0, 0, 0, 0);
        }
    }

    public static void init() {
        // === PHYSIK ===
		
		register(new RuneAction() {
			@Override public String getId() { return "stones:delay"; }
			@Override public void execute(ActionContext ctx, JsonObject params) {
				int ticks = resolveInt(ctx, params, "ticks", 20);
				JsonArray delayedActions = params.getAsJsonArray("actions");
				if (delayedActions == null) return;
			
				StonesMod.queueServerWork(ticks, () -> {
					for (JsonElement e : delayedActions) {
						JsonObject actObj = e.getAsJsonObject();
						RuneAction action = ACTIONS.get(actObj.get("type").getAsString());
						if (action != null) action.execute(ctx, actObj);
					}
				});
			}
		});
		
		register(new RuneAction() {
			@Override public String getId() { return "stones:update_combo"; }
			@Override public void execute(ActionContext ctx, JsonObject params) {
			    ServerPlayer player = ctx.getPlayer();
			    if (player == null) return;
			    
				String id = resolveString(ctx, params, "id", ctx.getRuneId());
				int count = resolveInt(ctx, params, "count", 0);
				
				if (count <= 0) {
					net.minecraftforge.network.PacketDistributor.PacketTarget target = net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player);
					net.stones.StonesMod.PACKET_HANDLER.send(target, new net.stones.network.PacketSyncCombo(id, 0, 1, "minecraft:textures/particle/glint.png", 0, 0, 0, 0, 0, 0, 0, 0));
					return;
				}

				int max = resolveInt(ctx, params, "max", 5);
				String texture = resolveString(ctx, params, "texture", "minecraft:textures/particle/glint.png");
				float size = resolveFloat(ctx, params, "size", 0.4f);
				float radius = resolveFloat(ctx, params, "radius", 1.2f);
				float speed = resolveFloat(ctx, params, "speed", 0.1f);
				int timeout = resolveInt(ctx, params, "timeout", 100);

				float r = 1f, g = 1f, b = 1f, a = 1f;
				String hexStr = resolveString(ctx, params, "color", "");
				if (!hexStr.isEmpty()) {
					String hex = hexStr.replace("#", "");
					if (hex.length() >= 6) {
					    try {
						    r = Integer.valueOf(hex.substring(0, 2), 16) / 255f;
						    g = Integer.valueOf(hex.substring(2, 4), 16) / 255f;
						    b = Integer.valueOf(hex.substring(4, 6), 16) / 255f;
						    if (hex.length() == 8) a = Integer.valueOf(hex.substring(6, 8), 16) / 255f;
					    } catch (Exception ignored) {}
					}
				}

				net.minecraftforge.network.PacketDistributor.PacketTarget target = net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player);
				net.stones.StonesMod.PACKET_HANDLER.send(target, new net.stones.network.PacketSyncCombo(
					id, count, max, texture, size, radius, speed, r, g, b, a, timeout
				));
			}
		});

        register(new RuneAction() {
            @Override public String getId() { return "stones:get_persistent_var"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                String name = resolveString(ctx, params, "name", "");
                String into = resolveString(ctx, params, "into", "");
                if (name.isEmpty() || into.isEmpty()) return;

                Tag tag = ctx.getPlayer().getPersistentData().get("stones_" + name);
                if (tag instanceof NumericTag n) ctx.setVariable(into, n.getAsFloat());
                else if (tag instanceof StringTag s) ctx.setVariable(into, s.getAsString());
                else if (tag != null) ctx.setVariable(into, tag.getAsString());
                else ctx.setVariable(into, 0.0f);
            }
        });

        register(new RuneAction() {
            @Override public String getId() { return "stones:set_persistent_var"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                String name = resolveString(ctx, params, "name", "");
                if (name.isEmpty()) return;

                Object val = resolveObject(ctx, params, "value");
                if (val instanceof Number n) ctx.getPlayer().getPersistentData().putFloat("stones_" + name, n.floatValue());
                else if (val instanceof Boolean b) ctx.getPlayer().getPersistentData().putBoolean("stones_" + name, b);
                else if (val != null) ctx.getPlayer().getPersistentData().putString("stones_" + name, val.toString());
            }
        });

		register(new RuneAction() {
				@Override public String getId() { return "stones:get_attribute"; }
				@Override public void execute(ActionContext ctx, JsonObject params) {
					Attribute attr = ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation(resolveString(ctx, params, "attribute", "")));
					if (attr != null) ctx.setVariable(resolveString(ctx, params, "into", ""), (float)ctx.getPlayer().getAttributeValue(attr));
				}
			});

		register(new RuneAction() {
				@Override public String getId() { return "stones:random"; }
				@Override public void execute(ActionContext ctx, JsonObject params) {
					float min = resolveFloat(ctx, params, "min", 0.0f);
					float max = resolveFloat(ctx, params, "max", 1.0f);
					ctx.setVariable(resolveString(ctx, params, "into", "roll"), min + ctx.getPlayer().getRandom().nextFloat() * (max - min));
				}
			});	
			
        register(new RuneAction() {
            @Override public String getId() { return "stones:add_velocity"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                ServerPlayer player = ctx.getPlayer();
                if (player == null) return;
                Object vecObj = resolveObject(ctx, params, "vec");
                Vec3 impulse = (vecObj instanceof Vec3 v) ? v : new Vec3(resolveFloat(ctx, params, "x", 0), resolveFloat(ctx, params, "y", 0), resolveFloat(ctx, params, "z", 0));
                player.setDeltaMovement(player.getDeltaMovement().add(impulse.scale(resolveFloat(ctx, params, "scale", 1.0f))));
                player.hurtMarked = true;
                player.connection.send(new ClientboundSetEntityMotionPacket(player));
            }
        });

        // === REFLECTION ===
        register(new RuneAction() {
            @Override public String getId() { return "stones:invoke"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                String callStr = resolveString(ctx, params, "call", "");
                List<ReflectionCallParser.Argument> argDefinitions = new ArrayList<>();
                
                String methodName = callStr;
                String[] explicitTypes = null;
                if (callStr.contains("(") && callStr.contains(")")) {
                    methodName = callStr.substring(0, callStr.indexOf("("));
                    String sig = callStr.substring(callStr.indexOf("(") + 1, callStr.lastIndexOf(")"));
                    if (!sig.trim().isEmpty()) explicitTypes = sig.split(",");
                }

                if (params.has("args")) {
                    JsonArray array = params.getAsJsonArray("args");
                    for (int i = 0; i < array.size(); i++) {
                        String valStr = array.get(i).isJsonPrimitive() && array.get(i).getAsJsonPrimitive().isString() 
                                       ? array.get(i).getAsString() : array.get(i).toString();
                        
                        String forcedType = (explicitTypes != null && i < explicitTypes.length) ? explicitTypes[i].trim() : null;
                        
                        ReflectionCallParser.ParsedCall p = ReflectionCallParser.parse("d(" + valStr + ")");
                        if (!p.args.isEmpty()) {
                            ReflectionCallParser.Argument baseArg = p.args.get(0);
                            argDefinitions.add(new ReflectionCallParser.Argument(baseArg.rawValue, forcedType != null ? forcedType : baseArg.explicitType));
                        }
                    }
                } else if (callStr.contains("(")) {
                    argDefinitions.addAll(ReflectionCallParser.parse(callStr).args);
                }

                ReflectionCallParser.ParsedCall structure = ReflectionCallParser.parse(methodName);
                ReflectionCallParser.ParsedCall finalCall = new ReflectionCallParser.ParsedCall(
                    structure.root, structure.path, structure.method, argDefinitions, false
                );
                
                List<Object> resolvedValues = resolveArgs(ctx, argDefinitions);
                Object res = ReflectionInvoker.execute(ctx.getPlayer(), ctx.getVariables(), finalCall, resolvedValues);
                
                if (res != null && params.has("save_result_to")) {
                    ctx.setVariable(resolveString(ctx, params, "save_result_to", ""), res);
                }
            }
        });

        register(new RuneAction() {
            @Override public String getId() { return "stones:set_field"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                Object target = resolveObject(ctx, params, "target");
                String fieldName = resolveString(ctx, params, "field", "");
                Object val = resolveObject(ctx, params, "value");
                if (target != null && val != null) {
                    ReflectionInvoker.setField(target, fieldName, val);
                }
            }
        });

        register(new RuneAction() {
            @Override public String getId() { return "stones:new"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                String className = resolveString(ctx, params, "class", "");
                List<ReflectionCallParser.Argument> argDefinitions = new ArrayList<>();
                if (params.has("args")) {
                    for (JsonElement e : params.getAsJsonArray("args")) {
                        String s = e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() ? e.getAsString() : e.toString();
                        ReflectionCallParser.ParsedCall p = ReflectionCallParser.parse("d(" + s + ")");
                        if (!p.args.isEmpty()) argDefinitions.add(p.args.get(0));
                    }
                }
                List<Object> resolvedValues = resolveArgs(ctx, argDefinitions);
                Object res = ReflectionInvoker.instantiate(className, argDefinitions, resolvedValues);
                if (res != null && params.has("save_to")) {
                    ctx.setVariable(resolveString(ctx, params, "save_to", ""), res);
                }
            }
        });
		
        register(new RuneAction() {
            @Override public String getId() { return "stones:modify_damage"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                float mult = resolveFloat(ctx, params, "multiplier", 1.0f);
                float add = resolveFloat(ctx, params, "add", 0f);
                ctx.modifyDamage(mult, add);
            }
        });

        register(new RuneAction() {
            @Override public String getId() { return "stones:heal"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                ServerPlayer p = ctx.getPlayer();
                if (params.has("amount")) p.heal(resolveFloat(ctx, params, "amount", 0f));
                else if (params.has("percent_of_max_health")) p.heal(p.getMaxHealth() * resolveFloat(ctx, params, "percent_of_max_health", 0f));
                else if (params.has("percent_of_damage")) p.heal(ctx.getFloat("damage", 0) * resolveFloat(ctx, params, "percent_of_damage", 0f));
            }
        });

        register(new RuneAction() {
            @Override public String getId() { return "stones:apply_effect"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                MobEffect e = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(resolveString(ctx, params, "effect", "")));
                if (e != null) {
                    int duration = resolveInt(ctx, params, "duration", 100);
                    int amplifier = resolveInt(ctx, params, "amplifier", 0);
                    ctx.getPlayer().addEffect(new MobEffectInstance(e, duration, amplifier));
                }
            }
        });

        register(new RuneAction() {
            @Override public String getId() { return "stones:explode"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                float radius = resolveFloat(ctx, params, "radius", 3.0f);
                boolean fire = params.has("fire") && params.get("fire").getAsBoolean();
                ctx.getPlayer().level().explode(ctx.getPlayer(), ctx.getPlayer().getX(), ctx.getPlayer().getY(), ctx.getPlayer().getZ(), radius, fire, Level.ExplosionInteraction.NONE);
            }
        });

        register(new RuneAction() {
            @Override public String getId() { return "stones:math"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                String var = resolveString(ctx, params, "variable", "");
                if (var.isEmpty()) return;
                
                float cur = ctx.getFloat(var, 0);
                float val = resolveFloat(ctx, params, "value", 0.0f);
                String op = resolveString(ctx, params, "operation", "").toLowerCase();
                
                float res = switch(op) {
                    case "add" -> cur + val;
                    case "subtract" -> cur - val;
                    case "multiply" -> cur * val;
                    case "divide" -> (val != 0) ? cur / val : cur;
                    default -> cur;
                };
                ctx.setVariable(var, res);
            }
        });

        register(new RuneAction() {
            @Override public String getId() { return "stones:cooldown"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                // Hier wurde jegliche Beeinflussung wie split() oder toLowerCase() entfernt
                String name = resolveString(ctx, params, "name", ctx.getRuneId());
                float ticks = resolveFloat(ctx, params, "ticks", 0f);
                
                LOGGER.info("[Stones-Debug] Cooldown-Action aufgerufen für: {}", name);
                LOGGER.info("[Stones-Debug] Berechnete Ticks: {}", ticks);

                if (name == null || name.isEmpty()) {
                    LOGGER.error("[Stones-Debug] Cooldown abgebrochen: Kein Name gefunden (JSON oder Kontext)!");
                    return;
                }

                ResourceLocation effectId = new ResourceLocation(StonesMod.MODID, "cooldown_" + name);
                MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectId);
                
                if (effect == null) {
                    LOGGER.error("[Stones-Debug] Cooldown abgebrochen: MobEffect '{}' nicht in Registry gefunden!", effectId);
                    return;
                }

                if (ticks <= 0) {
                    LOGGER.warn("[Stones-Debug] Cooldown ignoriert: Ticks sind 0 oder negativ.");
                    return;
                }

                try {
                    MobEffectInstance instance = new MobEffectInstance(effect, (int)ticks, 0, false, false, true);
                    instance.setCurativeItems(new ArrayList<>());
                    ctx.getPlayer().addEffect(instance);
                    LOGGER.info("[Stones-Debug] Cooldown erfolgreich gesetzt.");
                } catch (Exception e) {
                    LOGGER.error("[Stones-Debug] Fehler beim Hinzufügen des Potion-Effekts!", e);
                }
            }
        });

        register(new RuneAction() {
            @Override public String getId() { return "stones:cancel"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                if (ctx.getEvent() != null && ctx.getEvent().isCancelable()) ctx.getEvent().setCanceled(true);
            }
        });
		
        register(new RuneAction() {
            @Override public String getId() { return "stones:play_sound"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                String soundId = resolveString(ctx, params, "sound", "");
                SoundEvent event = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundId));
                if (event == null) return;
                
                SoundSource source = params.has("source") ? SoundSource.valueOf(params.get("source").getAsString().toUpperCase()) : SoundSource.PLAYERS;
                float vol = resolveFloat(ctx, params, "volume", 1.0f);
                float pitch = resolveFloat(ctx, params, "pitch", 1.0f);
                
                ctx.getPlayer().level().playSound(null, ctx.getPlayer().getX(), ctx.getPlayer().getY(), ctx.getPlayer().getZ(), event, source, vol, pitch);
            }
        });
		
        register(new RuneAction() {
            @Override public String getId() { return "stones:spawn_particles"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                String typeStr = resolveString(ctx, params, "particle", "");
                ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation(typeStr));
                if (!(type instanceof ParticleOptions options)) return;
                
                int count = resolveInt(ctx, params, "count", 10);
                float speed = resolveFloat(ctx, params, "speed", 0.0f);
                float spread = resolveFloat(ctx, params, "spread", 0.2f);
                
                Vec3 pos = resolveVec3(ctx, params);
                if (ctx.getPlayer().level() instanceof ServerLevel sl) {
                    sl.sendParticles(options, pos.x, pos.y, pos.z, count, spread, spread, spread, speed);
                }
            }
        });

        register(new RuneAction() {
            @Override public String getId() { return "stones:particle_orbit"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                if (ctx.getPlayer().level() instanceof ServerLevel sl) {
                    int count = resolveInt(ctx, params, "count", 1);
                    var pt = ForgeRegistries.PARTICLE_TYPES.getValue(new ResourceLocation(resolveString(ctx, params, "particle", "")));
                    if (pt instanceof ParticleOptions po) {
                        double angle = (sl.getGameTime() * 0.2);
                        for(int i=0; i<count; i++) {
                            double a = angle + (i * (6.28 / count));
                            sl.sendParticles(po, ctx.getPlayer().getX() + Math.cos(a), ctx.getPlayer().getY() + 1.1, ctx.getPlayer().getZ() + Math.sin(a), 1, 0, 0, 0, 0);
                        }
                    }
                }
            }
        });
        
        register(new RuneAction() {
            @Override public String getId() { return "stones:set_variable"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                String name = resolveString(ctx, params, "name", "");
                if (name.isEmpty()) return;
                Object val = resolveObject(ctx, params, "value");
                ctx.setVariable(name, val);
            }
        });
        
        // --- WELT-AKTIONEN ---
        register(new RuneAction() {
            @Override public String getId() { return "stones:set_block"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                if (!params.has("pos")) return;

                Object raw = resolveObject(ctx, params, "pos");
                BlockPos pos = null;
                
                if (raw instanceof BlockPos bp) pos = bp;
                else if (raw instanceof Vec3 v) pos = BlockPos.containing(v.x, v.y, v.z);

                if (pos != null && params.has("block")) {
                    String blockId = resolveString(ctx, params, "block", "");
                    Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
                    if (block != null && ctx.getPlayer().level() instanceof ServerLevel sl) {
                        sl.setBlock(pos, block.defaultBlockState(), 3);
                    }
                }
            }
        });
		
        register(new RuneAction() {
            @Override public String getId() { return "stones:for_each"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                Object listObj = resolveObject(ctx, params, "from");
                if (!(listObj instanceof List<?> list)) return;
                
                String varName = resolveString(ctx, params, "as", "");
                JsonArray actionsArray = params.getAsJsonArray("actions");

                for (Object item : list) {
                    ctx.setVariable(varName, item);
                    for (JsonElement e : actionsArray) {
                        JsonObject actObj = e.getAsJsonObject();
                        RuneAction action = ACTIONS.get(actObj.get("type").getAsString());
                        if (action != null) action.execute(ctx, actObj);
                    }
                }
            }
        });

        register(new RuneAction() {
            @Override public String getId() { return "stones:marker"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                ServerLevel sl = (ServerLevel) ctx.getPlayer().level();
                
                // Position bestimmen exakt wie in deinem Original (Box-Korrektur)
                Vec3 pos;
                Object raw = resolveObject(ctx, params, "pos");
                if (raw instanceof BlockPos bp) pos = Vec3.atLowerCornerOf(bp);
                else if (raw instanceof Vec3 v) pos = v;
                else pos = ctx.getPlayer().position();

                int duration = resolveInt(ctx, params, "duration", 100);
                float size = resolveFloat(ctx, params, "size", 1.0f);
                XrayParticleOptions options = new XrayParticleOptions(duration, size);

                String mode = resolveString(ctx, params, "mode", "point");

                if (mode.equalsIgnoreCase("box")) {
                    drawBox(sl, pos, options);
                } else {
                    sl.sendParticles(options, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 1, 0, 0, 0, 0);
                }
            }
        });

        register(new RuneAction() {
            @Override public String getId() { return "stones:case"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                if (params.has("cases")) {
                    for (JsonElement e : params.getAsJsonArray("cases")) {
                        JsonObject caseObj = e.getAsJsonObject();
                        if (caseObj.has("condition")) {
                            JsonObject condObj = caseObj.getAsJsonObject("condition");
                            RuneCondition condition = ConditionRegistry.create(condObj.get("type").getAsString(), condObj);
                            if (condition != null && condition.test(ctx)) {
                                executeActionList(ctx, caseObj.getAsJsonArray("actions"));
                                return;
                            }
                        }
                    }
                }
                if (params.has("default")) executeActionList(ctx, params.getAsJsonArray("default"));
            }
        });
		
        register(new RuneAction() {
            @Override public String getId() { return "stones:find_blocks"; }
            @Override public void execute(ActionContext ctx, JsonObject params) {
                ServerPlayer player = ctx.getPlayer();
                if (!(player.level() instanceof ServerLevel sl)) return;

                List<BlockPos> results = new ArrayList<>();
                String mode = resolveString(ctx, params, "mode", "radius");
                
                if (mode.equals("raycast")) {
                    double dist = resolveFloat(ctx, params, "distance", 5.0f);
                    HitResult hit = player.pick(dist, 0.0F, false);
                    if (hit.getType() == HitResult.Type.BLOCK) {
                        results.add(((BlockHitResult) hit).getBlockPos().immutable());
                    }
                } else {
                    int rx = resolveInt(ctx, params, "rx", resolveInt(ctx, params, "radius", 5));
                    int ry = resolveInt(ctx, params, "ry", resolveInt(ctx, params, "radius", 5));
                    int rz = resolveInt(ctx, params, "rz", resolveInt(ctx, params, "radius", 5));
                    
                    BlockPos center = player.blockPosition();
                    boolean los = params.has("line_of_sight") && params.get("line_of_sight").getAsBoolean();
                    Vec3 eyePos = player.getEyePosition();

                    for (BlockPos pos : BlockPos.betweenClosed(center.offset(-rx, -ry, -rz), center.offset(rx, ry, rz))) {
                        if (matchesFilter(sl, pos, params)) {
                            if (los) {
                                BlockHitResult hit = sl.clip(new ClipContext(eyePos, Vec3.atCenterOf(pos), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
                                if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(pos)) continue;
                            }
                            results.add(pos.immutable());
                        }
                    }
                }

                if (params.has("save_to")) {
                    String varName = resolveString(ctx, params, "save_to", "");
                    ctx.setVariable(varName, results);
                    ctx.setVariable(varName + "_count", (float)results.size());
                }
            }

            private boolean matchesFilter(ServerLevel sl, BlockPos pos, JsonObject params) {
                BlockState state = sl.getBlockState(pos);
                if (params.has("blocks")) {
                    for (JsonElement e : params.getAsJsonArray("blocks")) {
                        if (ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString().equals(e.getAsString())) return true;
                    }
                }
                if (params.has("tags")) {
                    for (JsonElement e : params.getAsJsonArray("tags")) {
                        if (state.is(BlockTags.create(new ResourceLocation(e.getAsString())))) return true;
                    }
                }
                return !params.has("blocks") && !params.has("tags");
            }
        });		
		register(new RuneAction() {
			@Override public String getId() { return "stones:command"; }
			@Override public void execute(ActionContext ctx, JsonObject params) {
				String cmd = resolveString(ctx, params, "command", "");
				if (!cmd.isEmpty() && ctx.getPlayer().getServer() != null) {
					ctx.getPlayer().getServer().getCommands().performPrefixedCommand(
						ctx.getPlayer().createCommandSourceStack().withPermission(4).withSuppressedOutput(), cmd
					);
				}
			}
		});

		register(new RuneAction() {
			@Override public String getId() { return "stones:remove_random_enchantment"; }
			@Override public void execute(ActionContext ctx, JsonObject params) {
				ItemStack stack = ctx.getPlayer().getMainHandItem();
				Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
				if (enchants.isEmpty()) return;
				List<Enchantment> list = new ArrayList<>(enchants.keySet());
				Enchantment target = list.get(ctx.getPlayer().getRandom().nextInt(list.size()));
				int lvl = enchants.remove(target);
				EnchantmentHelper.setEnchantments(enchants, stack);
				String into = resolveString(ctx, params, "save_level_to", "");
				if (!into.isEmpty()) ctx.setVariable(into, (float)lvl);
			}
		});
        LOGGER.info("MilestoneActionRegistry initialisiert.");
    }
	


}