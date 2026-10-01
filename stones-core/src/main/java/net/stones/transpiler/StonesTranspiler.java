package net.stones.transpiler;

import javax.annotation.Nullable;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.stones.data.ServerTextureRegistry;
import net.stones.enchantment.behavior.TriggerType;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Vollstaendige Transpilierungs-Engine fuer Stones Studio:
 * Uebersetzt Studio-JSONs direkt in native KubeJS-Skripte.
 * Frei von Hardcoding - validiert direkt ueber TriggerType.
 */
public class StonesTranspiler {

    private static final Pattern VAR_PATTERN = Pattern.compile("\\$([a-zA-Z0-9_]+)");
    private static final Pattern BASE64_IMAGE_PATTERN = Pattern.compile("data:image/[^\"'\\s]+");
    
    public static String transpile(String fileName, JsonObject json) {
        return transpile(fileName, json, null);
    }

    public static String transpile(String fileName, JsonObject json, @Nullable String resolvedScript) {
        boolean hasRawScript = resolvedScript != null && !resolvedScript.isBlank();
        boolean hasBehaviors = json.has("behaviors") && json.get("behaviors").isJsonArray() && !json.getAsJsonArray("behaviors").isEmpty();

        // Wenn weder externes Skript noch visuelle Behaviors vorhanden sind, abbrechen
        if (!hasRawScript && !hasBehaviors) return "";

        String baseRuneName = fileName.replace(".json", "");
        StringBuilder sb = new StringBuilder();

        // 1. Header-Kommentar
        sb.append("// ========================================================\n");
        sb.append("// GENERIERTER CODE AUS STONES STUDIO: ").append(fileName).append("\n");
        sb.append("// NICHT MANUELL EDITIEREN - WIRD BEIM RELOAD UEBERSCHRIEBEN\n");
        sb.append("// ========================================================\n\n");

        // 2. Gemeinsamer IIFE-Scope & Stat-Initialisierung
        sb.append("(() => {\n");
        sb.append("    // --- Zentrale Stat-Initialisierung fuer diese Rune ---\n");
        sb.append("    function initStats(ctx) {\n");
        sb.append("        let _runeData = global.Stones.getRuneData(ctx.player, 'stones:").append(baseRuneName).append("');\n");
        sb.append("        ctx['RuneLevel'] = _runeData.runeLevel || 1;\n");
        sb.append("        ctx['SocketLevel'] = _runeData.socketLevel || 1;\n");
        sb.append("        ctx['RuneMult'] = _runeData.mult || 1.0;\n");
        sb.append("        // --- Dynamische Berechnung der Stats aus dem JSON ---\n");
        injectStats(json, sb, 2);
        sb.append("    }\n\n");

        // 3. Weiche: Externes Skript (aus .js) ODER Visueller Baum (behaviors)
        if (hasRawScript) {
            sb.append("    // --- NATIVES BENUTZER-SKRIPT (RAW JS) ---\n");
            // Base64-Grafiken auch im Skript finden, im RAM registrieren und als dynamic: ID einsetzen
            String processedScript = processBase64InScript(resolvedScript);

            for (String line : processedScript.split("\\r?\\n")) {
                sb.append("    ").append(line).append("\n");
            }
        } else {
            JsonArray behaviors = json.getAsJsonArray("behaviors");
            for (JsonElement bEl : behaviors) {
                if (bEl.isJsonObject()) {
                    transpileBehavior(baseRuneName, bEl.getAsJsonObject(), sb);
                }
            }
        }

        // 4. Scope-Abschluss
        sb.append("})();\n");
        return sb.toString();
    }

    private static void transpileBehavior(String runeName, JsonObject behavior, StringBuilder sb) {
        if (!behavior.has("trigger")) {
            throw new IllegalArgumentException("CRITICAL STONES ERROR: 'trigger' fehlt im behavior-Block der Rune: " + runeName);
        }

        String rawTrigger = behavior.get("trigger").getAsString().trim();
        // Strikte Validierung ueber TriggerType Registry (knallt sofort bei Tippfehlern!)
        TriggerType triggerType = TriggerType.get(rawTrigger);

        sb.append("    global.Stones.register('").append(triggerType.id).append("', '").append(runeName).append("', ctx => {\n");
        sb.append("        initStats(ctx);\n");
        transpileBody(behavior, sb, 2);
        sb.append("    });\n\n");
    }

    private static void injectStats(JsonObject rootJson, StringBuilder sb, int indent) {
        if (!rootJson.has("stats")) return;
        String pad = "    ".repeat(indent);
        for (JsonElement sEl : rootJson.getAsJsonArray("stats")) {
            if (!sEl.isJsonObject()) continue;
            JsonObject s = sEl.getAsJsonObject();
            String id = getString(s, "id", "");
            if (id.isEmpty()) continue;

            double base = s.has("base") ? s.get("base").getAsDouble() : 0.0;
            double perLevel = s.has("per_level") ? s.get("per_level").getAsDouble() : 0.0;
            String scaling = getString(s, "scaling", "NONE").toUpperCase();

            String valExpr;
            if (perLevel != 0.0) {
                String levelVar = switch (scaling) {
                    case "SOCK_LEVEL", "SOCKET_LEVEL" -> "ctx['SocketLevel']";
                    default -> "ctx['RuneLevel']";
                };
                valExpr = base + " + (" + perLevel + " * Math.max(0, " + levelVar + " - 1))";
            } else {
                valExpr = String.valueOf(base);
            }

            boolean hasMin = s.has("min");
            boolean hasMax = s.has("max");
            if (hasMin && hasMax) {
                double minVal = s.get("min").getAsDouble();
                double maxVal = s.get("max").getAsDouble();
                valExpr = "Math.min(" + maxVal + ", Math.max(" + minVal + ", " + valExpr + "))";
            } else if (hasMin) {
                double minVal = s.get("min").getAsDouble();
                valExpr = "Math.max(" + minVal + ", " + valExpr + ")";
            } else if (hasMax) {
                double maxVal = s.get("max").getAsDouble();
                valExpr = "Math.min(" + maxVal + ", " + valExpr + ")";
            }

            sb.append(pad).append("ctx['").append(id).append("'] = ").append(valExpr).append(";\n");
        }
    }
	
	public static String processBase64InScript(String script) {
		if (script == null || !script.contains("data:image/")) {
			return script;
		}
		
		Matcher matcher = BASE64_IMAGE_PATTERN.matcher(script);
		StringBuilder sb = new StringBuilder();
		
		while (matcher.find()) {
			String rawBase64 = matcher.group();
			// Registriert das Bild im RAM und gibt "dynamic:12345" zurück
			String dynamicId = ServerTextureRegistry.processTextureForNetwork(rawBase64);
			matcher.appendReplacement(sb, Matcher.quoteReplacement(dynamicId));
		}
		matcher.appendTail(sb);
		
		return sb.toString();
	}
	
    private static void transpileBody(JsonObject behavior, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        List<String> conditions = new ArrayList<>();

        if (behavior.has("conditions")) {
            JsonElement el = behavior.get("conditions");
            if (el.isJsonArray()) {
                for (JsonElement c : el.getAsJsonArray()) {
                    if (c.isJsonObject()) conditions.add(transpileCondition(c.getAsJsonObject()));
                }
            } else if (el.isJsonObject()) {
                conditions.add(transpileCondition(el.getAsJsonObject()));
            }
        }

        if (!conditions.isEmpty()) {
            sb.append(pad).append("if (").append(String.join(" && ", conditions)).append(") {\n");
            transpileActions(behavior.get("actions"), sb, indent + 1);
            sb.append(pad).append("}\n");
        } else {
            transpileActions(behavior.get("actions"), sb, indent);
        }
    }

	private static String transpileCondition(JsonObject cond) {
        String type = getString(cond, "type", "");
        return switch (type) {
            // --- Logik-Bausteine (Verschachtelungen) ---
            case "stones:or" -> {
                if (cond.has("conditions") && cond.get("conditions").isJsonArray()) {
                    List<String> inner = new ArrayList<>();
                    for (JsonElement el : cond.getAsJsonArray("conditions")) {
                        if (el.isJsonObject()) inner.add(transpileCondition(el.getAsJsonObject()));
                    }
                    if (!inner.isEmpty()) {
                        yield "(" + String.join(" || ", inner) + ")";
                    }
                }
                yield "false";
            }
            case "stones:and" -> {
                if (cond.has("conditions") && cond.get("conditions").isJsonArray()) {
                    List<String> inner = new ArrayList<>();
                    for (JsonElement el : cond.getAsJsonArray("conditions")) {
                        if (el.isJsonObject()) inner.add(transpileCondition(el.getAsJsonObject()));
                    }
                    if (!inner.isEmpty()) {
                        yield "(" + String.join(" && ", inner) + ")";
                    }
                }
                yield "true";
            }
            case "stones:nor" -> {
                if (cond.has("conditions") && cond.get("conditions").isJsonArray()) {
                    List<String> inner = new ArrayList<>();
                    for (JsonElement el : cond.getAsJsonArray("conditions")) {
                        if (el.isJsonObject()) inner.add(transpileCondition(el.getAsJsonObject()));
                    }
                    if (!inner.isEmpty()) {
                        yield "!(" + String.join(" || ", inner) + ")";
                    }
                }
                yield "true";
            }
            case "stones:nand" -> {
                if (cond.has("conditions") && cond.get("conditions").isJsonArray()) {
                    List<String> inner = new ArrayList<>();
                    for (JsonElement el : cond.getAsJsonArray("conditions")) {
                        if (el.isJsonObject()) inner.add(transpileCondition(el.getAsJsonObject()));
                    }
                    if (!inner.isEmpty()) {
                        yield "!(" + String.join(" && ", inner) + ")";
                    }
                }
                yield "false";
            }

            // --- Standard- & Angriffs-Conditions ---
            case "stones:chance" -> "Math.random() < " + resolveVal(cond, "value", "0.5");
            case "stones:health_below" -> "(ctx.player.health / ctx.player.maxHealth) <= " + resolveVal(cond, "percent", "0.5");
            case "stones:is_ready" -> "global.Stones.isReady(ctx.player, '" + getString(cond, "name", "rune") + "')";
            case "stones:has_air" -> "ctx.player.airSupply >= " + resolveVal(cond, "min", "1");
            case "stones:is_raining" -> "ctx.player.level.isRaining()";
            case "stones:is_thundering" -> "ctx.player.level.isThundering()";
            case "stones:is_on_fire" -> "ctx.player.isOnFire()";
            case "stones:is_day" -> "ctx.player.level.isDay()";
            case "stones:has_enchantment" -> {
                String target = resolveTarget(cond, "target");
                yield "(!" + target + ".mainHandItem.isEmpty() && " + target + ".mainHandItem.isEnchanted())";
            }
            case "stones:block_check" -> {
                String blocksArray = cond.has("blocks") ? cond.getAsJsonArray("blocks").toString() :
                                     (cond.has("block") ? "[\"" + cond.get("block").getAsString() + "\"]" : "[]");
                String tagsArray = cond.has("tags") ? cond.getAsJsonArray("tags").toString() :
                                   (cond.has("tag") ? "[\"" + cond.get("tag").getAsString() + "\"]" : "[]");

                String dist = cond.has("distance") ? resolveVal(cond, "distance", "0") : "0";
                String pos = cond.has("pos") ? resolveVal(cond, "pos", "null") : "null";

                yield "global.Stones.checkBlock(ctx.player, " + dist + ", " + pos + ", " + blocksArray + ", " + tagsArray + ")";
            }
            case "stones:variable_compare" -> {
                String var = resolveVariable(getString(cond, "variable", "var"));
                String op = getString(cond, "operator", "==");
                String val = resolveVal(cond, "value", "0");
                yield "(" + var + ") " + op + " (" + val + ")";
            }
            case "stones:persistent_var_compare" -> {
                String name = getString(cond, "name", "data");
                String op = getString(cond, "operator", ">=");
                String val = resolveVal(cond, "value", "0");
                yield "(ctx.player.persistentData.getFloat('stones_" + name + "') || 0.0) " + op + " (" + val + ")";
            }
            case "stones:is_direct" -> "(ctx.event && ctx.event.source && !ctx.event.source.isIndirect())";
			case "stones:has_damage_tag", "stones:damage_tag" -> {
				String tag = getString(cond, "tag", "minecraft:is_explosion");
				if (!tag.contains(":")) tag = "minecraft:" + tag;
				yield "global.Stones.hasDamageTag(ctx.event, '" + tag + "')";
			}
			case "stones:is_damage_type", "stones:damage_type" -> {
				String damageType = getString(cond, "damage_type", getString(cond, "type_id", "minecraft:in_fire"));
				yield "global.Stones.isDamageType(ctx.event.source, '" + damageType + "')";
			}
            case "stones:damage_amount", "stones:damage_compare" -> {
                String op = getString(cond, "operator", ">=");
                String val = resolveVal(cond, "value", "0.0");
                yield "(ctx.event && typeof ctx.event.damage !== 'undefined' ? ctx.event.damage : 0) " + op + " (" + val + ")";
            }
            default -> "true";
        };
    }

    private static void transpileActions(JsonElement actionsElement, StringBuilder sb, int indent) {
        if (actionsElement == null) return;
        String pad = "    ".repeat(indent);

        JsonArray arr = actionsElement.isJsonArray() ? actionsElement.getAsJsonArray() : new JsonArray();
        if (actionsElement.isJsonObject()) arr.add(actionsElement);

        for (JsonElement el : arr) {
            if (!el.isJsonObject()) continue;
            JsonObject act = el.getAsJsonObject();
            String type = getString(act, "type", "");

            switch (type) {
                case "stones:case", "stones:cases" -> transpileCaseAction(act, sb, indent);
                case "stones:for_each" -> transpileForEachAction(act, sb, indent);
                case "stones:delay" -> transpileDelayAction(act, sb, indent);
                case "stones:spawn_projectile" -> transpileSpawnProjectileAction(act, sb, indent);
                case "stones:explode" -> transpileExplodeAction(act, sb, indent);
                case "stones:set_block" -> transpileSetBlockAction(act, sb, indent);
                case "stones:invoke" -> transpileInvokeAction(act, sb, indent);
                case "stones:add_velocity" -> transpileVelocityAction(act, sb, indent);
                case "stones:heal" -> transpileHealAction(act, sb, indent);
                case "stones:apply_effect" -> transpileApplyEffectAction(act, sb, indent);
				case "stones:deal_damage" -> transpileDealDamageAction(act, sb, indent);
                case "stones:modify_damage" -> transpileModifyDamageAction(act, sb, indent);
                case "stones:cancel" -> sb.append(pad).append("if (ctx.event) ctx.event.cancel();\n");
                case "stones:play_sound" -> transpilePlaySoundAction(act, sb, indent);
                case "stones:spawn_particles" -> transpileSpawnParticlesAction(act, sb, indent);
				case "stones:spawn_sprite" -> transpileSpawnSpriteAction(act, sb, indent);
				case "stones:spawn_beam" -> transpileSpawnBeamAction(act, sb, indent);
                case "stones:particle_orbit" -> transpileParticleOrbitAction(act, sb, indent);
                case "stones:read_nbt" -> transpileReadNbtAction(act, sb, indent);
                case "stones:cooldown" -> transpileCooldownAction(act, sb, indent);
                case "stones:set_variable" -> transpileSetVariableAction(act, sb, indent);
                case "stones:set_persistent_var" -> transpileSetPersistentVarAction(act, sb, indent);
                case "stones:get_persistent_var" -> transpileGetPersistentVarAction(act, sb, indent);
                case "stones:get_attribute" -> transpileGetAttributeAction(act, sb, indent);
                case "stones:random" -> transpileRandomAction(act, sb, indent);
                case "stones:remove_random_enchantment" -> transpileRemoveRandomEnchantmentAction(act, sb, indent);
                case "stones:raw_js", "stones:eval" -> transpileRawJsAction(act, sb, indent);
                case "stones:math" -> transpileMathAction(act, sb, indent);
                case "stones:command" -> transpileCommandAction(act, sb, indent);
                case "stones:add_combo", "stones:update_combo" -> transpileComboAction(act, sb, indent);
                case "stones:get_combo" -> transpileGetComboAction(act, sb, indent);
                case "stones:find_entities" -> transpileFindEntitiesAction(act, sb, indent);
                case "stones:find_blocks" -> transpileFindBlocksAction(act, sb, indent);
                case "stones:marker" -> transpileMarkerAction(act, sb, indent);
                default -> sb.append(pad).append("// Nicht transpiliert: ").append(type).append("\n");
            }
        }
    }

    private static void transpileSpawnProjectileAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String origin = resolveVal(act, "origin", "ctx.player.eyePosition");
        String dir = resolveVal(act, "direction", "ctx.player.lookAngle");
        String spd = resolveVal(act, "speed", "1.8");
        String grav = resolveVal(act, "gravity", "0.0");
        String life = resolveVal(act, "lifetime", "80");
        String hb = resolveVal(act, "hitbox_size", "0.25");
        String mode = "BILLBOARD";

        String rawTex = "minecraft:textures/particle/flame.png";
        if (act.has("visuals") && act.get("visuals").isJsonObject()) {
            JsonObject vis = act.getAsJsonObject("visuals");
            mode = getString(vis, "render_mode", "BILLBOARD");
            rawTex = getString(vis, "texture", rawTex);
        } else if (act.has("texture")) {
            rawTex = getString(act, "texture", rawTex);
        }

        // Base64 nur kuerzen, wenn es wirklich Base64 ist. Standard-Pfade erhalten!
        String safeTexture = rawTex.startsWith("data:") ? ServerTextureRegistry.processTextureForNetwork(rawTex) : rawTex;

        sb.append(pad).append("global.Stones.spawnProjectile(ctx.player, {\n");
        sb.append(pad).append("    origin: ").append(origin).append(",\n");
        sb.append(pad).append("    direction: ").append(dir).append(",\n");
        sb.append(pad).append("    speed: ").append(spd).append(",\n");
        sb.append(pad).append("    gravity: ").append(grav).append(",\n");
        sb.append(pad).append("    lifetime: ").append(life).append(",\n");
        sb.append(pad).append("    hitboxW: ").append(hb).append(",\n");
        sb.append(pad).append("    hitboxH: ").append(hb).append(",\n");
        sb.append(pad).append("    renderMode: '").append(mode).append("',\n");
        sb.append(pad).append("    texture: '").append(safeTexture).append("'\n");
        sb.append(pad).append("},\n");

        if (act.has("on_tick")) {
            sb.append(pad).append("proj => {\n");
            sb.append(pad).append("    ctx['pos'] = proj.position();\n");
            sb.append(pad).append("    ctx['age'] = proj.tickCount;\n");
            sb.append(pad).append("    ctx['projectile'] = proj;\n");
            transpileActions(act.get("on_tick"), sb, indent + 1);
            sb.append(pad).append("},\n");
        } else {
            sb.append(pad).append("null,\n");
        }

        if (act.has("on_hit_entity")) {
            sb.append(pad).append("(proj, hit) => {\n");
            sb.append(pad).append("    ctx['target'] = hit.entity;\n");
            sb.append(pad).append("    ctx['hitPos'] = hit.location;\n");
            sb.append(pad).append("    ctx['pos'] = hit.location;\n");
            transpileActions(act.get("on_hit_entity"), sb, indent + 1);
            sb.append(pad).append("},\n");
        } else {
            sb.append(pad).append("null,\n");
        }

        if (act.has("on_hit_block")) {
            sb.append(pad).append("(proj, hit) => {\n");
            sb.append(pad).append("    ctx['blockPos'] = hit.blockPos;\n");
            sb.append(pad).append("    ctx['hitPos'] = hit.location;\n");
            sb.append(pad).append("    ctx['pos'] = hit.location;\n");
            transpileActions(act.get("on_hit_block"), sb, indent + 1);
            sb.append(pad).append("}\n");
        } else {
            sb.append(pad).append("null\n");
        }

        sb.append(pad).append(");\n");
    }
	
	private static void transpileSpawnSpriteAction(JsonObject act, StringBuilder sb, int indent) {
		String pad = "    ".repeat(indent);

		// 1. Vektor & Parameter auflösen
		String pos = resolveVal(act, "pos", "ctx.player.position()");
		String speed = resolveVal(act, "speed", "0.0");
		String dir = resolveVal(act, "direction", "ctx.player.lookAngle");

		String drag = resolveVal(act, "drag", "0.92");
		String gravity = resolveVal(act, "gravity", "0.0");
		String scale = resolveVal(act, "initial_scale", "1.0");
		String growth = resolveVal(act, "growth_rate", "0.0");
		String spin = resolveVal(act, "spin_speed", "0.0");
		String lifetime = resolveVal(act, "lifetime", "30");

		// 2. Statische Enums & Texturen
		String facing = getString(act, "facing", "BILLBOARD");
		String blend = getString(act, "blend_mode", "ADDITIVE");
		String rawTex = getString(act, "texture", "minecraft:textures/particle/glint.png");
		String safeTex = rawTex.startsWith("data:") ? ServerTextureRegistry.processTextureForNetwork(rawTex) : rawTex;

		// 3. Keyframes als JSON-String
		String keyframes = (act.has("keyframes") && act.get("keyframes").isJsonArray())
				? act.getAsJsonArray("keyframes").toString()
				: "[]";

		// 4. Kapselung in eigenen Block-Scope {}, um 'let _dir' Re-Declaration Errors zu verhindern
		sb.append(pad).append("{\n");
		sb.append(pad).append("    let _dir = ").append(dir).append(";\n");
		sb.append(pad).append("    let _vel = (_dir ? _dir.normalize().scale(").append(speed).append(") : null);\n");

		sb.append(pad).append("    global.Stones.spawnSprite(ctx.player, ").append(pos).append(", _vel, {\n");
		sb.append(pad).append("        drag: ").append(drag).append(",\n");
		sb.append(pad).append("        gravity: ").append(gravity).append(",\n");
		sb.append(pad).append("        initial_scale: ").append(scale).append(",\n");
		sb.append(pad).append("        growth_rate: ").append(growth).append(",\n");
		sb.append(pad).append("        spin_speed: ").append(spin).append(",\n");
		sb.append(pad).append("        lifetime: ").append(lifetime).append(",\n");
		sb.append(pad).append("        facing: '").append(facing).append("',\n");
		sb.append(pad).append("        blend_mode: '").append(blend).append("',\n");
		sb.append(pad).append("        texture: '").append(safeTex).append("',\n");
		sb.append(pad).append("        keyframes: ").append(keyframes).append("\n");
		sb.append(pad).append("    });\n");
		sb.append(pad).append("}\n");
	}

	private static void transpileSpawnBeamAction(JsonObject act, StringBuilder sb, int indent) {
		String pad = "    ".repeat(indent);

		// 1. Vektoren & Parameter auflösen
		String start = resolveVal(act, "start", "ctx.player.eyePosition");
		String end = resolveVal(act, "end", "ctx.player.position()");

		// Falls $player.eye_pos eingegeben wurde -> auf KubeJS eyePosition korrigieren
		start = start.replace(".eye_pos", ".eyePosition");
		end = end.replace(".eye_pos", ".eyePosition");

		// Falls ctx.target direkt als Vektor übergeben wurde -> Koordinaten der Entity abfragen
		if (start.equals("ctx.target")) {
			start = "(ctx.target ? ctx.target.eyePosition : ctx.player.position())";
		}
		if (end.equals("ctx.target")) {
			end = "(ctx.target ? ctx.target.position() : ctx.player.position())";
		}

		String beamType = getString(act, "beam_type", "LASER");

		String rawTex = getString(act, "texture", "minecraft:textures/entity/beacon_beam.png");
		String safeTex = rawTex.startsWith("data:") ? ServerTextureRegistry.processTextureForNetwork(rawTex) : rawTex;

		String coreWidth = resolveVal(act, "core_width", "0.2");
		String coronaWidth = resolveVal(act, "corona_width", "0.8");

		// 2. Hex-Farbe (#RRGGBB) zu R, G, B floats umrechnen
		String hex = getString(act, "color", "#FFFFFF").replace("#", "");
		float r = 1.0f, g = 1.0f, b = 1.0f;
		try {
			int rgb = Integer.parseInt(hex, 16);
			r = ((rgb >> 16) & 0xFF) / 255.0f;
			g = ((rgb >> 8) & 0xFF) / 255.0f;
			b = (rgb & 0xFF) / 255.0f;
		} catch (Exception ignored) {}

		String alpha = resolveVal(act, "alpha", "1.0");
		String scrollSpeed = resolveVal(act, "uv_scroll_speed", "0.2");
		String repeat = resolveVal(act, "uv_repeat", "1.0");

		String helixRadius = resolveVal(act, "helix_radius", "0.0");
		String helixFreq = resolveVal(act, "helix_frequency", "0.0");
		String helixSpeed = resolveVal(act, "helix_speed", "0.0");
		String lifetime = resolveVal(act, "lifetime", "40");

		// 3. Generierung des Aufrufs (ohne Java 'f'-Suffixes für sauberes JS!)
		sb.append(pad).append("global.Stones.spawnBeam(ctx.player, ")
		  .append(start).append(", ")
		  .append(end).append(", '")
		  .append(beamType).append("', '")
		  .append(safeTex).append("', ")
		  .append(coreWidth).append(", ")
		  .append(coronaWidth).append(", ")
		  .append(r).append(", ")
		  .append(g).append(", ")
		  .append(b).append(", ")
		  .append(alpha).append(", ")
		  .append(scrollSpeed).append(", ")
		  .append(repeat).append(", ")
		  .append(helixRadius).append(", ")
		  .append(helixFreq).append(", ")
		  .append(helixSpeed).append(", ")
		  .append(lifetime).append(");\n");
	}
	
    private static void transpileFindBlocksAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String saveTo = getString(act, "save_to", "found_blocks");
        String rad = resolveVal(act, "radius", "5");
        String rx = resolveVal(act, "rx", rad);
        String ry = resolveVal(act, "ry", rad);
        String rz = resolveVal(act, "rz", rad);
        String los = getString(act, "line_of_sight", "false");
        String pos = resolveVal(act, "pos", "null");

        List<String> tags = new ArrayList<>();
        if (act.has("tags") && act.get("tags").isJsonArray()) {
            for (JsonElement t : act.getAsJsonArray("tags")) tags.add("'" + t.getAsString() + "'");
        }
        String tagsList = "[" + String.join(", ", tags) + "]";

        List<String> blocks = new ArrayList<>();
        if (act.has("blocks") && act.get("blocks").isJsonArray()) {
            for (JsonElement b : act.getAsJsonArray("blocks")) blocks.add("'" + b.getAsString() + "'");
        }
        String blocksList = "[" + String.join(", ", blocks) + "]";

        sb.append(pad).append("ctx['").append(saveTo).append("'] = global.Stones.findBlocks(ctx.player, ")
                .append(rx).append(", ").append(ry).append(", ").append(rz).append(", ")
                .append(los).append(", ").append(blocksList).append(", ").append(tagsList).append(", ")
                .append(pos).append(");\n");
    }

    private static void transpileSetBlockAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String block = getString(act, "block", "minecraft:air");
        String pos = resolveVal(act, "pos", "ctx.player.blockPosition()");
        sb.append(pad).append("global.Stones.setBlock(ctx.player.level, ").append(pos).append(", '").append(block).append("');\n");
    }
	
	private static void transpileDealDamageAction(JsonObject act, StringBuilder sb, int indent) {
		String pad = "    ".repeat(indent);
		String target = resolveTarget(act, "target");
		String amount = resolveVal(act, "amount", "5.0");
		String damageType = getString(act, "damage_type", "");

		// Nutzt jetzt resolveEntityOrNull für Angreifer & Schadensquelle!
		String direct = resolveEntityOrNull(act, "direct_attacker", "null");
		String indirect = resolveEntityOrNull(act, "indirect_attacker", "ctx.player");

		sb.append(pad).append("global.Stones.dealDamage(")
		  .append(direct).append(", ")
		  .append(indirect).append(", ")
		  .append(target).append(", ")
		  .append(amount).append(", '")
		  .append(damageType).append("');\n");
	}
	
    private static void transpileExplodeAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String radius = resolveVal(act, "radius", "3.0");
        String fire = resolveVal(act, "fire", "false");
        String pos = resolveVal(act, "pos", "ctx.player.position()");
        sb.append(pad).append("global.Stones.explode(ctx.player, ").append(pos).append(", ").append(radius).append(", ").append(fire).append(");\n");
    }

    private static void transpileForEachAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String from = resolveVal(act, "from", "[]");
        String as = getString(act, "as", "item").replace("$", "");
        sb.append(pad).append("for (let ").append(as).append(" of (").append(from).append(" || [])) {\n");
        sb.append(pad).append("    ctx['").append(as).append("'] = ").append(as).append(";\n");
        transpileActions(act.get("actions"), sb, indent + 1);
        sb.append(pad).append("}\n");
    }

    private static void transpileCaseAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        if (!act.has("cases")) return;

        boolean first = true;
        for (JsonElement cEl : act.getAsJsonArray("cases")) {
            if (!cEl.isJsonObject()) continue;
            JsonObject cObj = cEl.getAsJsonObject();
            String cond = cObj.has("conditions") ? transpileCondition(cObj.getAsJsonObject("conditions")) : "true";

            if (first) {
                sb.append(pad).append("if (").append(cond).append(") {\n");
                first = false;
            } else {
                sb.append(pad).append("} else if (").append(cond).append(") {\n");
            }
            transpileActions(cObj.get("actions"), sb, indent + 1);
        }
        if (act.has("default")) {
            sb.append(pad).append("} else {\n");
            transpileActions(act.get("default"), sb, indent + 1);
        }
        sb.append(pad).append("}\n");
    }

    private static void transpileDelayAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String ticks = resolveVal(act, "ticks", "20");
        sb.append(pad).append("ctx.player.server.scheduleInTicks(").append(ticks).append(", callback => {\n");
        transpileActions(act.get("actions"), sb, indent + 1);
        sb.append(pad).append("});\n");
    }

    private static void transpileComboAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String type = getString(act, "type", "stones:update_combo");
        String target = resolveTarget(act, "target");
        String id = getString(act, "id", "combo");
        String rawVal = resolveVal(act, "value", resolveVal(act, "count", "1"));
        String max = resolveVal(act, "max", "5");
        String rawTex = getString(act, "texture", "minecraft:textures/particle/flame.png");
        String safeTex = rawTex.startsWith("data:") ? ServerTextureRegistry.processTextureForNetwork(rawTex) : rawTex;
        String size = resolveVal(act, "size", "0.4");
        String radius = resolveVal(act, "radius", "1.2");
        String speed = resolveVal(act, "speed", "0.1");
        String color = getString(act, "color", "#FFFFFF");
        String timeout = resolveVal(act, "timeout", "100");

        String valExpr;
        if (type.equals("stones:add_combo")) {
            valExpr = "Math.min(" + max + ", (global.Stones.getComboCount(" + target + ", '" + id + "') || 0) + (" + rawVal + "))";
        } else {
            valExpr = rawVal;
        }

        sb.append(pad).append("global.Stones.updateCombo(").append(target).append(", '").append(id)
          .append("', ").append(valExpr).append(", ").append(max).append(", '").append(safeTex)
          .append("', ").append(size).append(", ").append(radius).append(", ").append(speed)
          .append(", '").append(color).append("', ").append(timeout).append(");\n");
    }

    private static void transpileGetComboAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String target = resolveTarget(act, "target");
        String id = getString(act, "id", "combo");
        String into = getString(act, "into", id);
        sb.append(pad).append("ctx['").append(into).append("'] = global.Stones.getComboCount(").append(target).append(", '").append(id).append("');\n");
    }

    private static void transpileCooldownAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String name = getString(act, "name", "rune");
        String ticks = resolveVal(act, "ticks", "100");
        sb.append(pad).append("global.Stones.setCooldown(ctx.player, '").append(name).append("', ").append(ticks).append(");\n");
    }

    private static void transpileHealAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String target = resolveTarget(act, "target");
        if (act.has("percent_of_max_health")) {
            sb.append(pad).append(target).append(".heal(").append(target).append(".maxHealth * ").append(resolveVal(act, "percent_of_max_health", "0.1")).append(");\n");
        } else if (act.has("percent_of_damage")) {
            sb.append(pad).append(target).append(".heal((ctx.event ? ctx.event.damage : 0) * ").append(resolveVal(act, "percent_of_damage", "0.1")).append(");\n");
        } else {
            sb.append(pad).append(target).append(".heal(").append(resolveVal(act, "amount", "4")).append(");\n");
        }
    }

    private static void transpileApplyEffectAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String target = resolveTarget(act, "target");
        String effect = getString(act, "effect", "minecraft:speed");
        String duration = resolveVal(act, "duration", "100");
        String amp = resolveVal(act, "amplifier", "0");
        sb.append(pad).append(target).append(".potionEffects.add('").append(effect).append("', ").append(duration).append(", ").append(amp).append(");\n");
    }

    private static void transpileModifyDamageAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String mult = resolveVal(act, "multiplier", "1.0");
        String add = resolveVal(act, "add", "0.0");
        sb.append(pad).append("if (ctx.event && typeof ctx.event.damage !== 'undefined') {\n");
        sb.append(pad).append("    ctx.event.damage = (ctx.event.damage * ").append(mult).append(") + ").append(add).append(";\n");
        sb.append(pad).append("}\n");
    }

    private static void transpileVelocityAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String target = resolveTarget(act, "target");
        String vecVar = resolveVal(act, "vec", "ctx.player.lookAngle");
        String scale = resolveVal(act, "scale", "1.0");
        sb.append(pad).append("let imp = (").append(vecVar).append(").scale(").append(scale).append(");\n");
        sb.append(pad).append(target).append(".addDeltaMovement(imp);\n");
        sb.append(pad).append(target).append(".hurtMarked = true;\n");
    }

    private static void transpileRandomAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String min = resolveVal(act, "min", "0.0");
        String max = resolveVal(act, "max", "1.0");
        String into = getString(act, "into", "roll");
        sb.append(pad).append("ctx['").append(into).append("'] = ").append(min).append(" + (Math.random() * ((").append(max).append(") - (").append(min).append(")));\n");
    }

    private static void transpileGetAttributeAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String target = resolveTarget(act, "target");
        String attr = getString(act, "attribute", "minecraft:generic.max_health");
        String into = getString(act, "into", "attr_val");
        sb.append(pad).append("ctx['").append(into).append("'] = ").append(target).append(".getAttributeValue('").append(attr).append("');\n");
    }

    private static void transpileRemoveRandomEnchantmentAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String target = resolveTarget(act, "target");
        String saveTo = getString(act, "save_level_to", "sacrificed_lvl");
        sb.append(pad).append("ctx['").append(saveTo).append("'] = global.Stones.removeRandomEnchantment(").append(target).append(");\n");
    }

	private static void transpileRawJsAction(JsonObject act, StringBuilder sb, int indent) {
		String pad = "    ".repeat(indent);
		String code = getString(act, "code", getString(act, "js", ""));
		
		if (!code.isEmpty()) {
			// Base64 extrahieren, im Server registrieren und durch dynamic: ID ersetzen
			code = processBase64InScript(code);

			for (String line : code.split("\\r?\\n")) {
				if (!line.trim().isEmpty()) {
					sb.append(pad).append(line).append("\n");
				}
			}
		}
	}

    private static void transpilePlaySoundAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String sound = getString(act, "sound", "minecraft:entity.experience_orb.pickup");
        String source = getString(act, "source", "players");
        String vol = resolveVal(act, "volume", "1.0");
        String pitch = resolveVal(act, "pitch", "1.0");
        sb.append(pad).append("global.Stones.playSound(ctx.player, '").append(sound).append("', '").append(source).append("', ").append(vol).append(", ").append(pitch).append(");\n");
    }

    private static void transpileSpawnParticlesAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String typeStr = getString(act, "particle", "minecraft:flame");
        String count = resolveVal(act, "count", "10");
        String spread = resolveVal(act, "spread", "0.2");
        String speed = resolveVal(act, "speed", "0.0");
        String pos = resolveVal(act, "pos", "ctx.player.position()");
        // Sicherer nativer Java-Call ueber StonesScriptBridge statt instabilem Rhino-Field-Zugriff
        sb.append(pad).append("global.Stones.spawnParticles(ctx.player, '").append(typeStr).append("', ").append(pos).append(", ").append(count).append(", ").append(spread).append(", ").append(spread).append(", ").append(spread).append(", ").append(speed).append(");\n");
    }

    private static void transpileParticleOrbitAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String particle = getString(act, "particle", "minecraft:end_rod");
        String count = resolveVal(act, "count", "10");
        sb.append(pad).append("global.Stones.spawnParticles(ctx.player, '").append(particle).append("', ctx.player.position().add(0, 1.0, 0), ").append(count).append(", 0.5, 0.5, 0.5, 0.05);\n");
    }

    private static void transpileFindEntitiesAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String saveTo = getString(act, "save_to", "found_entities");
        String mode = getString(act, "mode", "radius");
        String rad = resolveVal(act, "radius", "5");
        String rx = resolveVal(act, "rx", rad);
        String ry = resolveVal(act, "ry", rad);
        String rz = resolveVal(act, "rz", rad);
        String living = getString(act, "living_only", "true");
        String exclude = getString(act, "exclude_self", "true");
        String los = getString(act, "line_of_sight", "false");
        String pos = resolveVal(act, "pos", "null");
        sb.append(pad).append("ctx['").append(saveTo).append("'] = global.Stones.findEntities(ctx.player, '").append(mode).append("', ").append(rad).append(", ").append(rx).append(", ").append(ry).append(", ").append(rz).append(", ").append(living).append(", ").append(exclude).append(", ").append(los).append(", ").append(pos).append(");\n");
    }

    private static void transpileMarkerAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String mode = getString(act, "mode", "point");
        String size = resolveVal(act, "size", "1.0");
        String duration = resolveVal(act, "duration", "100");
        sb.append(pad).append("global.Stones.drawMarker(ctx.player, ctx.player.position(), '").append(mode).append("', ").append(size).append(", ").append(duration).append(");\n");
    }

    private static void transpileSetVariableAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String name = getString(act, "name", "temp");
        String val = resolveVal(act, "value", "0");
        sb.append(pad).append("ctx['").append(name).append("'] = ").append(val).append(";\n");
    }

    private static void transpileSetPersistentVarAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String name = getString(act, "name", "data");
        String val = resolveVal(act, "value", "0");
        sb.append(pad).append("ctx.player.persistentData.putFloat('stones_").append(name).append("', ").append(val).append(");\n");
    }

    private static void transpileGetPersistentVarAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String name = getString(act, "name", "data");
        String into = getString(act, "into", "val");
        sb.append(pad).append("ctx['").append(into).append("'] = ctx.player.persistentData.getFloat('stones_").append(name).append("') || 0.0;\n");
    }

	private static void transpileReadNbtAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String target = resolveTarget(act, "target");
        String path = getString(act, "path", getString(act, "tag", "data"));
        String saveTo = getString(act, "save_to", getString(act, "tag", "nbt_val")).replace("$", "");

        sb.append(pad).append("ctx['").append(saveTo).append("'] = global.Stones.readNbt(").append(target).append(", '").append(path).append("');\n");
    }

    private static void transpileMathAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String var = getString(act, "variable", "temp");
        String val = resolveVal(act, "value", "1");
        String op = getString(act, "operation", "add").toLowerCase();
        String jsOp = switch (op) {
            case "subtract" -> " - ";
            case "multiply" -> " * ";
            case "divide" -> " / ";
            case "modulo" -> " % ";
            default -> " + ";
        };
        sb.append(pad).append("ctx['").append(var).append("'] = (Number(ctx['").append(var).append("']) || 0)").append(jsOp).append("(").append(val).append(");\n");
    }

	private static void transpileInvokeAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String call = getString(act, "call", "").trim();
        String saveTo = getString(act, "save_result_to", "").trim();

        if (call.isEmpty()) return;

        // 1. Ziel-Objekt bestimmen
        String target = "ctx.player";
        String methodPart = call;

        if (call.startsWith("player.")) {
            target = "ctx.player";
            methodPart = call.substring("player.".length());
        } else if (call.startsWith("target.")) {
            target = "ctx.target";
            methodPart = call.substring("target.".length());
        }

        // 2. Methodennamen und Signatur-Typen aus den Klammern parsen
        String methodName = methodPart;
        String[] paramTypes = new String[0];

        if (methodPart.contains("(") && methodPart.contains(")")) {
            int openParen = methodPart.indexOf('(');
            int closeParen = methodPart.lastIndexOf(')');
            methodName = methodPart.substring(0, openParen).trim();
            String typesStr = methodPart.substring(openParen + 1, closeParen).trim();
            if (!typesStr.isEmpty()) {
                paramTypes = typesStr.split("\\s*,\\s*");
            }
        }

        // 3. Argumente auflösen
        List<String> rawArgs = new ArrayList<>();
        if (act.has("args") && act.get("args").isJsonArray()) {
            for (JsonElement argEl : act.getAsJsonArray("args")) {
                rawArgs.add(argEl.getAsString());
            }
        }

        // 4. Argumente typensicher auf JS-Primitives mappen
        List<String> typedArgs = new ArrayList<>();
        for (int i = 0; i < rawArgs.size(); i++) {
            String rawVal = resolveVal(rawArgs.get(i), "0");
            String expectedType = (i < paramTypes.length) ? paramTypes[i].trim().toLowerCase() : "";

            String formatted = switch (expectedType) {
                case "double", "float", "number" -> "Number(" + rawVal + ")";
                case "int", "integer", "long", "short", "byte" -> "Math.round(" + rawVal + ")";
                case "boolean" -> "Boolean(" + rawVal + ")";
                default -> rawVal;
            };
            typedArgs.add(formatted);
        }

        // 5. Direkter JS-Instanzaufruf ohne Java-Reflection-Bridge
        String jsCall = target + "." + methodName + "(" + String.join(", ", typedArgs) + ")";

        if (!saveTo.isEmpty()) {
            sb.append(pad).append("ctx['").append(saveTo).append("'] = ").append(jsCall).append(";\n");
        } else {
            sb.append(pad).append(jsCall).append(";\n");
        }
    }

    private static void transpileCommandAction(JsonObject act, StringBuilder sb, int indent) {
        String pad = "    ".repeat(indent);
        String cmd = getString(act, "command", "say Hi");
        Matcher m = VAR_PATTERN.matcher(cmd);
        String interpolated = m.replaceAll("\\${ctx['$1']}");
        sb.append(pad).append("ctx.player.server.runCommandSilent(`").append(interpolated).append("`);\n");
    }

	private static String resolveEntityOrNull(JsonObject act, String key, String defaultExpr) {
		if (!act.has(key)) return resolveVariable(defaultExpr);
		String val = act.get(key).getAsString().trim();
		if (val.isEmpty() || val.equals("null")) return "null";

		// Reicht 'val' (egal ob "player", "$player", "victim", etc.) direkt an die zentrale Logik weiter
		return resolveVariable(val);
	}

	private static String resolveVariable(String var) {
		// Entfernt ein mögliches $ am Anfang
		if (var.startsWith("$")) var = var.substring(1);

		// Finde den ersten Punkt, um das Basis-Objekt vom Rest zu trennen
		int dotIdx = var.indexOf('.');
		String root = dotIdx != -1 ? var.substring(0, dotIdx) : var;
		String path = dotIdx != -1 ? var.substring(dotIdx) : ""; // Enthält den Punkt und alles danach (z.B. ".isGlowing()")

		// Nur das Basis-Objekt (root) wird gemappt
		String resolvedRoot = switch (root) {
			case "player", "target", "attacker", "victim", "event" -> "ctx." + root;
			case "hitPos" -> "(ctx.hitPos || ctx.player.position())";
			case "blockPos" -> "(ctx.blockPos || ctx.player.blockPosition())";
			default -> "ctx['" + root + "']";
		};

		// Der Pfad/Aufruf wird unverfälscht angehängt
		return resolvedRoot + path;
	}

	private static String resolveTarget(JsonObject obj, String key) {
		if (!obj.has(key)) return "ctx.player";
		String val = obj.get(key).getAsString().trim();
		if (val.isEmpty()) return "ctx.player";

		if (val.startsWith("$")) val = val.substring(1);

		int dotIdx = val.indexOf('.');
		if (dotIdx != -1) {
			return "ctx['" + val.substring(0, dotIdx) + "']" + val.substring(dotIdx);
		}

		return "ctx['" + val + "']";
	}

    private static String resolveVal(JsonObject obj, String key, String def) {
        if (!obj.has(key)) return def;
        String val = obj.get(key).getAsString();
        return resolveVal(val, def);
    }


private static String resolveVal(String val, String def) {
    if (val == null || val.isEmpty()) return def;

    // 1. Reine Pfad- & Variablenzugriffe ($player.pos, $custom.pos, $target.eyePosition)
    if (val.startsWith("$") && !val.matches(".*[\\s+\\-*/%].*")) {
        String raw = val.substring(1);

        int dotIdx = raw.indexOf('.');
        String root = dotIdx != -1 ? raw.substring(0, dotIdx) : raw;
        String path = dotIdx != -1 ? raw.substring(dotIdx) : ""; // Enthält den führenden Punkt, z. B. ".pos"

        // Das Root-Objekt im ctx auflösen (System-Kürzel oder generisch ctx['root'])
        String resolvedRoot = switch (root) {
            case "player" -> "ctx.player";
            case "target" -> "ctx.target";
            case "hitPos" -> "(ctx.hitPos || ctx.player.position())";
            case "blockPos" -> "(ctx.blockPos || ctx.player.blockPosition())";
            default -> "ctx['" + root + "']";
        };

        // Pfad wird unverändert angehängt -> ctx['custom'] + .pos = ctx['custom'].pos
        return resolvedRoot + path;
    }

    // 2. Mathematische Ausdrücke ($RuneLevel * 0.5 + $base)
    if (val.contains("$")) {
        Matcher m = VAR_PATTERN.matcher(val);
        StringBuilder exprSb = new StringBuilder();
        while (m.find()) {
            String v = m.group(1);
            String rep = switch (v) {
                case "SockLevel", "SocketLevel" -> "(ctx['SocketLevel'] || 1)";
                case "RuneLevel" -> "(ctx['RuneLevel'] || 1)";
                default -> "(Number(ctx['" + v + "']) || 0)";
            };
            m.appendReplacement(exprSb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(exprSb);
        return exprSb.toString();
    }

    return val;
}

    private static String getString(JsonObject obj, String key, String def) {
        return obj.has(key) ? obj.get(key).getAsString() : def;
    }
}