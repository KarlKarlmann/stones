package net.stones.transpiler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.stones.StonesMod;
import net.stones.client.fx.SpriteInstance;
import net.stones.client.fx.BeamInstance;
import net.stones.client.fx.BeamType;
import net.stones.network.S2CSpawnSpritePacket;
import net.stones.network.S2CSpawnBeamPacket;
import net.stones.cap.PlayerShrineCapProvider;
import net.stones.data.ServerTextureRegistry;
import net.stones.data.ShrineSavedData;
import net.stones.entity.StonesProjectileEntity;
import net.stones.entity.ai.StonesFollowOwnerGoal;
import net.stones.entity.ai.StonesProtectOwnerGoal;
import net.stones.event.StonesActionDispatcher;
import net.stones.util.RuneCalculator;
import net.stones.network.PacketSyncCombo;
import net.stones.network.PacketSyncCooldown;
import net.stones.particle.XrayParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageType;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.resources.ResourceKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nullable;
/**
 * Zentrale, absturzsichere API-Brücke im Transpiler-Paket für KubeJS-Skripte.
 */
public class StonesScriptBridge {

    @FunctionalInterface
    public interface TriggerConsumer {
        void accept(ServerPlayer player, String runeId, String triggerName, LivingEntity target, Object event);
    }

    private static TriggerConsumer triggerConsumer = null;

    // --- INVERTED INDEX (Telefonbuch-Register) ---
    // Map: TriggerName (z.B. "ON_TICK") -> Set aller Runen-IDs, die diesen Trigger wirklich nutzen
    private static final Map<String, Set<String>> RUNES_BY_TRIGGER = new ConcurrentHashMap<>();

    // --- PLAYER-SPECIFIC GOVERNOR STATE ---
    private static int lastGlobalTick = -1;
    private static final Map<UUID, Integer> playerTickUsage = new HashMap<>();
    private static final int MAX_TRIGGERS_PER_TICK = 150;

    /**
     * Registriert eine Rune direkt unter ihrem spezifischen Event-Kanal (Register-Eintrag).
     */
    public static void registerRuneTrigger(String runeId, String triggerName) {
        if (runeId == null || triggerName == null) return;
        String cleanId = runeId.replace("stones:", "");
        
        RUNES_BY_TRIGGER
            .computeIfAbsent(triggerName, k -> ConcurrentHashMap.newKeySet())
            .add(cleanId);
    }

    /**
     * Leert das Register und den Governor-Status beim Reload von KubeJS/Datapacks.
     */
    public static void clearRegisteredTriggers() {
        RUNES_BY_TRIGGER.clear();
        playerTickUsage.clear();
    }

    public static void setTriggerConsumer(TriggerConsumer consumer) {
        triggerConsumer = consumer;
        StonesMod.LOGGER.info("[Stones Bridge] KubeJS TriggerConsumer erfolgreich registriert.");
    }

    /**
     * Engine-Governor: Prüft das Limit pro Spieler & Tick.
     */
    private static boolean consumeAllowance(ServerPlayer player, String runeId, String triggerName) {
        if (player == null || player.getServer() == null) return true;

        int currentTick = player.getServer().getTickCount();

        if (currentTick != lastGlobalTick) {
            playerTickUsage.clear();
            lastGlobalTick = currentTick;
        }

        UUID playerId = player.getUUID();
        int usage = playerTickUsage.getOrDefault(playerId, 0);

        if (usage >= MAX_TRIGGERS_PER_TICK) {
            if (usage == MAX_TRIGGERS_PER_TICK) {
                StonesMod.LOGGER.debug("Stones Mod Governor: Engine-Cap ({} Triggers/Tick) durch Kombo von Spieler {} erreicht. Letzte Rune: '{}', Trigger: '{}'.",
                    MAX_TRIGGERS_PER_TICK, player.getName().getString(), runeId, triggerName);
            }
            playerTickUsage.put(playerId, usage + 1);
            return false;
        }

        playerTickUsage.put(playerId, usage + 1);
        return true;
    }

    /**
     * Führt ein Event aus. Nutzt den Inverted Index für blitzschnellen Abbruch,
     * wenn die Rune den Trigger gar nicht abonniert hat.
     */
    public static void dispatchTrigger(ServerPlayer player, String runeId, String triggerName, LivingEntity target, Object event) {
        if (player == null || runeId == null || triggerName == null) return;

        String cleanId = runeId.replace("stones:", "");

        // 1. REGISTER-LOOKUP: Hört dieser Trigger überhaupt auf diese Rune?
        Set<String> activeRunesForTrigger = RUNES_BY_TRIGGER.get(triggerName);
        if (activeRunesForTrigger == null || !activeRunesForTrigger.contains(cleanId)) {
            return; // Zoran Zappelmann nicht im Buch -> Sofortiger Abbruch ohne String-Allocation!
        }

        // 2. GOVERNOR-CHECK: Schutz vor Endlosschleifen
        if (!consumeAllowance(player, cleanId, triggerName)) {
            return;
        }

        if (triggerConsumer == null) return;

        try {
            if (!"ON_TICK".equalsIgnoreCase(triggerName)) {
                StonesMod.LOGGER.info("[Stones Bridge] Leite Trigger weiter an KubeJS: {} ({})", cleanId, triggerName);
            }
            triggerConsumer.accept(player, cleanId, triggerName, target, event);
        } catch (Exception e) {
            StonesMod.LOGGER.error("[Stones Bridge] Fehler bei KubeJS Trigger-Ausführung für " + cleanId + " (" + triggerName + "):", e);
        }
    }

    static {
        StonesActionDispatcher.setConsumer((player, runeId, slot) -> {
            StonesMod.LOGGER.info("[Stones Net] Action-Taste empfangen: {} (Slot: {})", runeId, slot);
            dispatchTrigger(player, runeId, "ON_ACTION_BUTTON", player, slot);
        });
    }
	
	public static Object readNbt(Entity entity, String path) {
        if (entity == null || path == null || path.isBlank()) return 0;

        CompoundTag current = new CompoundTag();
        entity.saveWithoutId(current);

        // Trennt den Pfad an Punkten, ignoriert Punkte innerhalb von Anführungszeichen
        String[] parts = path.split("\\.(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i].replace("\"", "").trim();
            if (part.isEmpty()) continue;

            if (i == parts.length - 1) {
                if (!current.contains(part)) return 0;
                byte type = current.getTagType(part);
                return switch (type) {
                    case 1, 2, 3 -> current.getInt(part);
                    case 4 -> current.getLong(part);
                    case 5 -> current.getFloat(part);
                    case 6 -> current.getDouble(part);
                    case 8 -> current.getString(part);
                    case 9 -> current.getList(part, 10);
                    case 10 -> current.getCompound(part);
                    default -> current.get(part);
                };
            } else {
                if (current.contains(part, 10)) { // 10 = CompoundTag
                    current = current.getCompound(part);
                } else {
                    return 0; // Pfad existiert nicht
                }
            }
        }
        return 0;
    }
	
	public static void makeMinion(Mob mob, Player player) {
		if (mob == null || player == null) return;

		// 1. Vanilla Target-KI sicher leeren
		mob.setTarget(null);
		mob.targetSelector.getAvailableGoals().clear();

		// 2. Tag setzen & Owner-NBT verknüpfen
		mob.addTag("stones_minion");
		mob.getPersistentData().putUUID("StonesMinionOwner", player.getUUID());

		// 3. Eigene Begleiter-KI injizieren
		mob.goalSelector.addGoal(2, new StonesFollowOwnerGoal(mob, player, 1.25D));
		mob.targetSelector.addGoal(1, new StonesProtectOwnerGoal(mob, player));
	}
	
    public static Map<String, Object> getRuneData(LivingEntity entity, String runeId) {
        Map<String, Object> data = new HashMap<>();
        data.put("runeLevel", 1);
        data.put("socketLevel", 1);
        data.put("mult", 1.0);

        if (!(entity instanceof ServerPlayer player)) return data;
        String cleanId = runeId.replace("stones:", "");

        for (RuneCalculator.CachedMilestone m : RuneCalculator.getActiveMilestones(player)) {
            if (m.runeId != null && m.runeId.getPath().replace("stones:", "").equalsIgnoreCase(cleanId)) {
                data.put("runeLevel", m.runeLevel);
                data.put("socketLevel", m.socketLevel);
                data.put("mult", (double) m.mult);
                return data;
            }
        }

        var capOpt = player.getCapability(PlayerShrineCapProvider.SHRINE_LINK);
        if (capOpt.isPresent() && capOpt.resolve().isPresent() && capOpt.resolve().get().isLinked()) {
            var shrine = ShrineSavedData.get(player.serverLevel()).getShrine(capOpt.resolve().get().getLinkedShrine());
            if (shrine != null) {
                RuneCalculator.collectActiveRunes(shrine.getInventory(), shrine.getLayout(), player.experienceLevel,
                    (rune, runeLevel, socketLevel, mult, mainSlot, subSlot) -> {
                        ResourceLocation loc = ForgeRegistries.ENCHANTMENTS.getKey(rune);
                        if (loc != null && loc.getPath().replace("stones:", "").equalsIgnoreCase(cleanId)) {
                            data.put("runeLevel", runeLevel);
                            data.put("socketLevel", socketLevel);
                            data.put("mult", (double) mult);
                        }
                    }
                );
            }
        }
        return data;
    }

    public static boolean hasRune(LivingEntity entity, String runeId) {
        if (!(entity instanceof ServerPlayer player)) return false;
        String cleanId = runeId.replace("stones:", "");

        for (RuneCalculator.CachedMilestone m : RuneCalculator.getActiveMilestones(player)) {
            if (m.runeId != null && m.runeId.getPath().replace("stones:", "").equalsIgnoreCase(cleanId)) {
                return true;
            }
        }

        var capOpt = player.getCapability(PlayerShrineCapProvider.SHRINE_LINK);
        if (capOpt.isPresent() && capOpt.resolve().isPresent() && capOpt.resolve().get().isLinked()) {
            var shrine = ShrineSavedData.get(player.serverLevel()).getShrine(capOpt.resolve().get().getLinkedShrine());
            if (shrine != null) {
                final boolean[] found = { false };
                RuneCalculator.collectActiveRunes(shrine.getInventory(), shrine.getLayout(), player.experienceLevel,
                    (rune, runeLevel, socketLevel, mult, mainSlot, subSlot) -> {
                        ResourceLocation loc = ForgeRegistries.ENCHANTMENTS.getKey(rune);
                        if (loc != null && loc.getPath().replace("stones:", "").equalsIgnoreCase(cleanId)) {
                            found[0] = true;
                        }
                    }
                );
                return found[0];
            }
        }
        return false;
    }

	public static void spawnSprite(
			LivingEntity actor,
			Vec3 pos,
			Vec3 velocity,
			Map<String, Object> config
	) {
		if (actor == null || !(actor.level() instanceof ServerLevel level) || pos == null || config == null) return;
		if (velocity == null) velocity = Vec3.ZERO;

		// 1. Numerische Parameter auflösen
		float drag = getFloat(config.get("drag"), 0.92f);
		float gravity = getFloat(config.get("gravity"), 0.0f);
		float scale = getFloat(config.get("initial_scale"), 1.0f);
		float growth = getFloat(config.get("growth_rate"), 0.0f);
		float spin = getFloat(config.get("spin_speed"), 0.0f);
		int lifetime = getInt(config.get("lifetime"), 30);

		// FIX: rawTexture konsistent genutzt
		String rawTexture = getString(config.get("texture"), "minecraft:textures/particle/glint.png");
		String safeTexture = rawTexture.startsWith("data:") ? ServerTextureRegistry.processTextureForNetwork(rawTexture) : rawTexture;

		// 2. Enums auflösen
		SpriteInstance.Facing facing = SpriteInstance.Facing.BILLBOARD;
		if (config.get("facing") != null) {
			try {
				facing = SpriteInstance.Facing.valueOf(config.get("facing").toString().toUpperCase());
			} catch (IllegalArgumentException ignored) {}
		}

		SpriteInstance.BlendMode blend = SpriteInstance.BlendMode.ADDITIVE;
		if (config.get("blend_mode") != null) {
			try {
				blend = SpriteInstance.BlendMode.valueOf(config.get("blend_mode").toString().toUpperCase());
			} catch (IllegalArgumentException ignored) {}
		}

		// 3. Keyframes auflösen
		List<SpriteInstance.Keyframe> keyframes = new ArrayList<>();
		Object kfObj = config.get("keyframes");
		if (kfObj instanceof List<?> list) {
			for (Object item : list) {
				if (item instanceof Map<?, ?> k) {
					float time = getFloat(k.get("time"), 0f);
					String colorHex = getString(k.get("color"), "#FFFFFF");
					float alpha = getFloat(k.get("alpha"), 1f);
					keyframes.add(SpriteInstance.Keyframe.fromHexAndAlpha(time, colorHex, alpha));
				}
			}
		}

		// 4. Netzwerk-Paket senden
		S2CSpawnSpritePacket packet = new S2CSpawnSpritePacket(
			pos, velocity, drag, gravity, scale, growth, spin,
			facing, blend, lifetime, safeTexture, keyframes
		);

		PacketDistributor.TargetPoint target = new PacketDistributor.TargetPoint(
			pos.x, pos.y, pos.z, 64.0, level.dimension()
		);
		StonesMod.PACKET_HANDLER.send(PacketDistributor.NEAR.with(() -> target), packet);
	}
	
    public static void spawnProjectile(
            LivingEntity shooter,
            Map<String, Object> config,
            Consumer<StonesProjectileEntity> onTick,
            BiConsumer<StonesProjectileEntity, EntityHitResult> onHitEntity,
            BiConsumer<StonesProjectileEntity, BlockHitResult> onHitBlock
    ) {
        if (shooter == null || !(shooter.level() instanceof ServerLevel sl)) return;

        try {
            Vec3 origin = resolveVec3(config.get("origin"), shooter.getEyePosition());
            Vec3 direction = resolveVec3(config.get("direction"), shooter.getLookAngle());

            float speed = getFloat(config.get("speed"), 1.8f);
            float gravity = getFloat(config.get("gravity"), 0.0f);
            int lifetime = getInt(config.get("lifetime"), 80);
            float hitboxW = getFloat(config.get("hitboxW"), 0.25f);
            float hitboxH = getFloat(config.get("hitboxH"), 0.25f);
            String renderMode = getString(config.get("renderMode"), "BILLBOARD");

            String rawTexture = getString(config.get("texture"), "minecraft:textures/particle/flame.png");
            String safeTextureId;
            if (rawTexture.startsWith("data:")) {
                safeTextureId = ServerTextureRegistry.processTextureForNetwork(rawTexture);
            } else {
                safeTextureId = rawTexture;
            }

            StonesProjectileEntity projectile = new StonesProjectileEntity(sl, origin.x, origin.y, origin.z);
            projectile.setOwner(shooter);
            projectile.onTickConsumer = onTick;
            projectile.onHitEntityConsumer = onHitEntity;
            projectile.onHitBlockConsumer = onHitBlock;

            projectile.setup(direction, speed, gravity, lifetime, hitboxW, hitboxH, renderMode, safeTextureId, null, null, null);
            sl.addFreshEntity(projectile);

        } catch (Exception e) {
            StonesMod.LOGGER.error("[Stones Bridge] Fehler beim Spawnen des Projektils für " + shooter.getName().getString(), e);
        }
    }

    public static List<BlockPos> findBlocks(
            LivingEntity actor,
            int rx, int ry, int rz,
            boolean lineOfSight,
            List<String> blockFilter,
            List<String> tagFilter,
            Object posOverride
    ) {
        List<BlockPos> results = new ArrayList<>();
        if (actor == null || !(actor.level() instanceof ServerLevel sl)) return results;

        BlockPos center;
        if (posOverride != null) {
            center = resolveBlockPos(posOverride, actor.blockPosition());
        } else {
            center = actor.blockPosition();
        }

        Set<String> blockSet = new HashSet<>(blockFilter != null ? blockFilter : List.of());
        List<TagKey<Block>> tags = new ArrayList<>();
        if (tagFilter != null) {
            for (String tagStr : tagFilter) {
                tags.add(BlockTags.create(new ResourceLocation(tagStr)));
            }
        }

        Vec3 eyePos = actor.getEyePosition();

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-rx, -ry, -rz), center.offset(rx, ry, rz))) {
            BlockState state = sl.getBlockState(pos);
            if (matchesFilter(state, blockSet, tags)) {
                if (lineOfSight) {
                    BlockHitResult hit = sl.clip(new ClipContext(eyePos, Vec3.atCenterOf(pos), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, actor));
                    if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(pos)) continue;
                }
                results.add(pos.immutable());
            }
        }
        return results;
    }

    private static boolean matchesFilter(BlockState state, Set<String> blockSet, List<TagKey<Block>> tags) {
        if (blockSet.isEmpty() && tags.isEmpty()) return true;

        if (!blockSet.isEmpty()) {
            ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            if (key != null && blockSet.contains(key.toString())) return true;
        }

        for (TagKey<Block> tag : tags) {
            if (state.is(tag)) return true;
        }
        return false;
    }

    public static void setBlock(Level level, Object posObj, String blockId) {
        if (level == null || level.isClientSide()) return;
        BlockPos pos = resolveBlockPos(posObj, null);
        if (pos == null) return;

        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blockId));
        if (block != null) {
            level.setBlock(pos, block.defaultBlockState(), 3);
        }
    }

	public static void dealDamage(
			@Nullable Entity directAttacker, 
			@Nullable Entity indirectAttacker, 
			Entity target, 
			float amount, 
			String damageTypeId
	) {
		if (!(target instanceof LivingEntity livingTarget) || amount <= 0.0f) return;
		Level level = livingTarget.level();
		if (level.isClientSide()) return;

		// 1. Check if ID is provided
		if (damageTypeId == null || damageTypeId.isBlank()) {
			throw new IllegalArgumentException(
				"[Stones Mod] Parameter 'damage_type' cannot be empty! Please provide a full DamageType ID (e.g. 'minecraft:indirect_magic')."
			);
		}

		String cleanId = damageTypeId.trim();

		// 2. Strict Namespace Check
		if (!cleanId.contains(":")) {
			throw new IllegalArgumentException(
				"[Stones Mod] Invalid DamageType ID '" + damageTypeId + "'! " +
				"Namespace prefix is missing. Did you mean 'minecraft:" + cleanId + "'?"
			);
		}

		// 3. Syntax validation for ResourceLocation
		ResourceLocation loc = ResourceLocation.tryParse(cleanId);
		if (loc == null) {
			throw new IllegalArgumentException(
				"[Stones Mod] Invalid ResourceLocation syntax for DamageType ID: '" + damageTypeId + "'!"
			);
		}

		// 4. Server Registry Lookup
		var registryOpt = level.registryAccess().registry(Registries.DAMAGE_TYPE);
		if (registryOpt.isEmpty()) {
			throw new IllegalStateException("[Stones Mod] Failed to access DamageType registry from world level.");
		}

		ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, loc);
		var holderOpt = registryOpt.get().getHolder(key);

		// 5. Strict Existence Check
		if (holderOpt.isEmpty()) {
			throw new IllegalArgumentException(
				"[Stones Mod] Unknown DamageType '" + cleanId + "'! " +
				"This type does not exist in the active server registry."
			);
		}

		// 6. Apply damage
		DamageSource source = new DamageSource(holderOpt.get(), directAttacker, indirectAttacker);
		livingTarget.hurt(source, amount);
	}

public static void spawnBeam(
        LivingEntity actor, 
        Vec3 start, 
        Vec3 end, 
        BeamType beamType, 
        String texture,
        float coreWidth, 
        float coronaWidth, 
        float r, float g, float b, float a,
        float scrollSpeed, 
        float repeat, 
        float helixRadius, 
        float helixFreq, 
        float helixSpeed, 
        int lifetime
) {
    if (actor == null || !(actor.level() instanceof ServerLevel level) || start == null || end == null) return;

    String rawTexture = texture != null ? texture : "minecraft:textures/entity/beacon_beam.png";
    String safeTextureId = rawTexture.startsWith("data:") 
            ? ServerTextureRegistry.processTextureForNetwork(rawTexture) 
            : rawTexture;

    BeamInstance beamFx = new BeamInstance(
        start, end, beamType, safeTextureId,
        coreWidth, coronaWidth,
        r, g, b, a,
        scrollSpeed, repeat,
        helixRadius, helixFreq, helixSpeed,
        lifetime
    );

    S2CSpawnBeamPacket packet = new S2CSpawnBeamPacket(beamFx);

    // Sendet das Paket an alle Spieler im Umkreis von 64 Blöcken um den Startpunkt
    PacketDistributor.TargetPoint target = new PacketDistributor.TargetPoint(
        start.x, start.y, start.z, 64.0, level.dimension()
    );
    
    StonesMod.PACKET_HANDLER.send(PacketDistributor.NEAR.with(() -> target), packet);
}

	
	public static boolean checkBlock(Level level, BlockPos pos, Object blocksInput, Object tagsInput) {
		if (level == null || pos == null) return false;
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) return false;

		ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
		String idStr = blockId != null ? blockId.toString() : "";

		List<String> blocks = parseListOrString(blocksInput);
		for (String b : blocks) {
			if (!b.isEmpty() && (b.equals(idStr) || b.equals(blockId != null ? blockId.getPath() : ""))) {
				return true;
			}
		}

		List<String> tags = parseListOrString(tagsInput);
		for (String t : tags) {
			if (t.isEmpty()) continue;
			ResourceLocation tagLoc = ResourceLocation.tryParse(t);
			if (tagLoc != null) {
				var tagKey = TagKey.create(ForgeRegistries.BLOCKS.getRegistryKey(), tagLoc);
				if (state.is(tagKey)) return true;
			}
		}

		return blocks.isEmpty() && tags.isEmpty();
	}
	
	public static boolean checkBlock(LivingEntity actor, double distance, Object posObj, Object blocksInput, Object tagsInput) {
        if (actor == null) return false;

        BlockPos pos = null;
        if (distance > 0.0) {
            HitResult hit = actor.pick(distance, 0.0f, false);
            if (hit instanceof BlockHitResult bhr) {
                pos = bhr.getBlockPos();
            }
        } else if (posObj != null) {
            pos = resolveBlockPos(posObj, null);
        } else {
            pos = actor.blockPosition();
        }

        if (pos == null) return false;
        // Delegiert an deine bestehende Methode unten
        return checkBlock(actor.level(), pos, blocksInput, tagsInput);
    }
	
	private static List<String> parseListOrString(Object input) {
		List<String> list = new ArrayList<>();
		if (input instanceof String s) {
			if (!s.isBlank()) list.add(s.trim());
		} else if (input instanceof Iterable<?> iterable) {
			for (Object o : iterable) {
				if (o != null) list.add(o.toString().trim());
			}
		} else if (input instanceof Object[] arr) {
			for (Object o : arr) {
				if (o != null) list.add(o.toString().trim());
			}
		}
		return list;
	}

    public static void explode(LivingEntity actor, Object posObj, float radius, boolean fire, boolean destroyBlocks) {
        if (actor == null || actor.level().isClientSide()) return;
        Vec3 pos = resolveVec3(posObj, actor.position());
        Level.ExplosionInteraction mode = destroyBlocks ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE;
        actor.level().explode(actor, pos.x, pos.y, pos.z, radius, fire, mode);
    }

    public static void explode(LivingEntity actor, Object posObj, float radius, boolean fire) {
        explode(actor, posObj, radius, fire, false);
    }

    public static List<Entity> findEntities(
            LivingEntity actor,
            String mode,
            float radius,
            float rx, float ry, float rz,
            boolean livingOnly,
            boolean excludeSelf,
            boolean lineOfSight,
            Object posOverride
    ) {
        List<Entity> results = new ArrayList<>();
        if (actor == null || !(actor.level() instanceof ServerLevel sl)) return results;

        if ("raycast".equalsIgnoreCase(mode)) {
            Vec3 eyePos = actor.getEyePosition();
            Vec3 look = actor.getLookAngle();
            Vec3 reach = eyePos.add(look.scale(radius));
            AABB searchBox = actor.getBoundingBox().expandTowards(look.scale(radius)).inflate(1.0);

            EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                    sl, actor, eyePos, reach, searchBox,
                    e -> (!excludeSelf || !e.equals(actor)) && (!livingOnly || e instanceof LivingEntity)
            );
            if (hit != null && hit.getEntity() != null) results.add(hit.getEntity());
        } else {
            Vec3 center = resolveVec3(posOverride, actor.position().add(0, 1.0, 0));
            AABB area = new AABB(center.x - rx, center.y - ry, center.z - rz, center.x + rx, center.y + ry, center.z + rz);

            Class<? extends Entity> targetClass = livingOnly ? LivingEntity.class : Entity.class;
            for (Entity entity : sl.getEntitiesOfClass(targetClass, area)) {
                if (excludeSelf && entity.equals(actor)) continue;
                if (lineOfSight) {
                    HitResult hit = sl.clip(new ClipContext(center, entity.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor));
                    if (hit.getType() != HitResult.Type.MISS) continue;
                }
                results.add(entity);
            }
        }
        return results;
    }

    public static boolean isReady(LivingEntity entity, String cooldownName) {
        if (entity == null) return true;
        long now = entity.level().getGameTime();
        long endTick = entity.getPersistentData().getLong("cd_" + cooldownName);

        if (endTick > now + 72000L) {
            endTick = 0L;
            entity.getPersistentData().putLong("cd_" + cooldownName, 0L);
        }

        return now >= endTick;
    }

    public static void setCooldown(LivingEntity entity, String cooldownName, int ticks) {
        if (entity == null || ticks <= 0) return;
        long endTick = entity.level().getGameTime() + ticks;
        entity.getPersistentData().putLong("cd_" + cooldownName, endTick);

        if (entity instanceof ServerPlayer player) {
            StonesMod.PACKET_HANDLER.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncCooldown(cooldownName, endTick)
            );
        }
    }

    public static void updateCombo(
            LivingEntity entity, String id, float value, int max,
            String texture, float size, float radius, float speed, String colorHex, int timeout
    ) {
        if (entity == null) return;
        CompoundTag persist = entity.getPersistentData();
        long now = entity.level().getGameTime();

        if (value <= 0.0f) {
            persist.putFloat("stones_combo_" + id + "_count", 0.0f);
            persist.putLong("stones_combo_" + id + "_expire", 0L);
            persist.remove("stones_combo_" + id + "_max");
            persist.remove("stones_combo_" + id + "_texture");
            persist.remove("stones_combo_" + id + "_size");
            persist.remove("stones_combo_" + id + "_radius");
            persist.remove("stones_combo_" + id + "_speed");
            persist.remove("stones_combo_" + id + "_color");

            StonesMod.PACKET_HANDLER.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                    new PacketSyncCombo(id, entity.getId(), 0, 1, "minecraft:textures/particle/glint.png", 0, 0, 0, 0, 0, 0, 0, 0)
            );
            return;
        }

        String safeTexture = ServerTextureRegistry.processTextureForNetwork(texture);
        float r = 1f, g = 1f, b = 1f, a = 1f;
        if (colorHex != null && colorHex.length() >= 7) {
            try {
                String clean = colorHex.replace("#", "");
                r = Integer.valueOf(clean.substring(0, 2), 16) / 255f;
                g = Integer.valueOf(clean.substring(2, 4), 16) / 255f;
                b = Integer.valueOf(clean.substring(4, 6), 16) / 255f;
                if (clean.length() == 8) a = Integer.valueOf(clean.substring(6, 8), 16) / 255f;
            } catch (Exception ignored) {}
        }

        persist.putFloat("stones_combo_" + id + "_count", value);
        long expire = (timeout == -1) ? -1L : (now + timeout);
        persist.putLong("stones_combo_" + id + "_expire", expire);
        persist.putInt("stones_combo_" + id + "_max", max);
        persist.putString("stones_combo_" + id + "_texture", safeTexture);
        persist.putFloat("stones_combo_" + id + "_size", size);
        persist.putFloat("stones_combo_" + id + "_radius", radius);
        persist.putFloat("stones_combo_" + id + "_speed", speed);
        persist.putString("stones_combo_" + id + "_color", colorHex != null ? colorHex : "#FFFFFF");

        StonesMod.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                new PacketSyncCombo(id, entity.getId(), (int) value, max, safeTexture, size, radius, speed, r, g, b, a, timeout == -1 ? 999999999 : timeout)
        );
    }

    public static float getComboCount(LivingEntity entity, String id) {
        if (entity == null) return 0.0f;
        long expire = entity.getPersistentData().getLong("stones_combo_" + id + "_expire");
        if (expire == -1L || (expire > 0L && entity.level().getGameTime() < expire)) {
            return entity.getPersistentData().getFloat("stones_combo_" + id + "_count");
        }
        return 0.0f;
    }

    public static void spawnParticles(
            LivingEntity actor,
            String particleId,
            Object posObj,
            int count,
            double sx, double sy, double sz,
            double speed
    ) {
        if (actor == null || !(actor.level() instanceof ServerLevel sl)) return;

        try {
            Vec3 pos = resolveVec3(posObj, actor.position());
            ResourceLocation pLoc = new ResourceLocation(particleId);
            ParticleType<?> pType = ForgeRegistries.PARTICLE_TYPES.getValue(pLoc);

            if (pType instanceof ParticleOptions pOptions) {
                sl.sendParticles(pOptions, pos.x, pos.y, pos.z, Math.max(1, count), sx, sy, sz, speed);
            }
        } catch (Exception e) {
            StonesMod.LOGGER.error("[Stones Bridge] Fehler beim Senden von Partikeln: " + particleId, e);
        }
    }

    public static void drawMarker(LivingEntity actor, Object posObj, String mode, float size, int duration) {
        if (actor == null || !(actor.level() instanceof ServerLevel sl)) return;
        Vec3 pos = resolveVec3(posObj, actor.position());
        XrayParticleOptions options = new XrayParticleOptions(duration, size);

        if ("box".equalsIgnoreCase(mode)) {
            for (double i = 0; i <= size; i += 0.25) {
                sl.sendParticles(options, pos.x + i, pos.y, pos.z, 1, 0, 0, 0, 0);
                sl.sendParticles(options, pos.x + i, pos.y + size, pos.z, 1, 0, 0, 0, 0);
                sl.sendParticles(options, pos.x + i, pos.y, pos.z + size, 1, 0, 0, 0, 0);
                sl.sendParticles(options, pos.x + size, pos.y + i, pos.z, 1, 0, 0, 0, 0);
                sl.sendParticles(options, pos.x + size, pos.y + i, pos.z + size, 1, 0, 0, 0, 0);
                sl.sendParticles(options, pos.x + size, pos.y + i, pos.z + size, 1, 0, 0, 0, 0);
            }
        } else {
            sl.sendParticles(options, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, 1, 0, 0, 0, 0);
        }
    }
	
    public static void playSound(LivingEntity actor, String soundId, String sourceStr, float volume, float pitch) {
        if (actor == null) return;
        try {
            ResourceLocation sLoc = new ResourceLocation(soundId);
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(sLoc);
            if (sound == null) return;

            SoundSource source = SoundSource.PLAYERS;
            if (sourceStr != null && !sourceStr.isEmpty()) {
                try {
                    source = SoundSource.valueOf(sourceStr.toUpperCase());
                } catch (IllegalArgumentException ignored) {}
            }

            actor.level().playSound(null, actor.getX(), actor.getY(), actor.getZ(), sound, source, volume, pitch);
        } catch (Exception e) {
            StonesMod.LOGGER.error("[Stones Bridge] Fehler beim Abspielen von Sound: " + soundId, e);
        }
    }
	
    public static float removeRandomEnchantment(LivingEntity target) {
        if (target == null) return 0.0f;
        ItemStack stack = target.getMainHandItem();
        if (stack.isEmpty()) return 0.0f;
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
        if (enchants.isEmpty()) return 0.0f;
        List<Enchantment> list = new ArrayList<>(enchants.keySet());
        Enchantment chosen = list.get(target.level().random.nextInt(list.size()));
        int lvl = enchants.remove(chosen);
        EnchantmentHelper.setEnchantments(enchants, stack);
        return (float) lvl;
    }

    public static Vec3 resolveVec3(Object obj, Vec3 fallback) {
        if (obj instanceof Vec3 v) return v;
        if (obj instanceof BlockPos bp) return Vec3.atCenterOf(bp);
        return fallback;
    }

    public static BlockPos resolveBlockPos(Object obj, BlockPos fallback) {
        if (obj instanceof BlockPos bp) return bp;
        if (obj instanceof Vec3 v) return BlockPos.containing(v.x, v.y, v.z);
        return fallback;
    }

    private static float getFloat(Object obj, float fallback) {
        if (obj instanceof Number n) return n.floatValue();
        if (obj instanceof String s) {
            try { return Float.parseFloat(s); } catch (Exception ignored) {}
        }
        return fallback;
    }

    private static int getInt(Object obj, int fallback) {
        if (obj instanceof Number n) return n.intValue();
        if (obj instanceof String s) {
            try { return Integer.parseInt(s); } catch (Exception ignored) {}
        }
        return fallback;
    }
	public static boolean hasDamageTag(Object eventObj, String tagId) {
		if (eventObj == null || tagId == null || tagId.isEmpty()) return false;

		if (eventObj instanceof LivingDamageEvent event) {
			if (event.getSource() == null) return false;
			ResourceLocation loc = ResourceLocation.tryParse(tagId.contains(":") ? tagId : "minecraft:" + tagId);
			if (loc == null) return false;

			TagKey<DamageType> tagKey = TagKey.create(Registries.DAMAGE_TYPE, loc);
			return event.getSource().is(tagKey);
		}
		return false;
	}
	
	public static boolean isDamageType(DamageSource source, String typeId) {
		if (source == null || typeId == null) return false;
		
		ResourceLocation loc = new ResourceLocation(typeId);
		ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE, loc);

		return source.is(key);
	}	
    private static String getString(Object obj, String fallback) {
        return obj != null ? obj.toString() : fallback;
    }
}