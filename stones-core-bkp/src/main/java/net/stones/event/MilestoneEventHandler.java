package net.stones.event;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.stones.StonesMod;
import net.stones.data.ShrineInstance;
import net.stones.enchantment.behavior.TriggerType;
import net.stones.item.StoneItem;
import net.stones.util.RuneCalculator;
import net.stones.util.RuneCalculator.CachedMilestone;
import net.stones.transpiler.StonesScriptBridge;

import java.util.List;

/**
 * Zentraler Event-Handler für Stones-Runen.
 * Fängt alle Forge-Events auf dem Server ab, prüft über RuneCalculator
 * aktiv gesockelte Runen und leitet sie direkt an die KubeJS-Bridge weiter.
 */
@Mod.EventBusSubscriber(modid = StonesMod.MODID)
public class MilestoneEventHandler {

    // =========================================================================
    // DAMAGE & COMBAT EVENTS
    // =========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim) {
            LivingEntity attacker = event.getSource().getEntity() instanceof LivingEntity le ? le : null;
            executeMilestones(victim, TriggerType.ON_HURT, attacker, event);
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            executeMilestones(attacker, TriggerType.ON_ATTACK, event.getEntity(), event);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            executeMilestones(attacker, TriggerType.ON_KILL, event.getEntity(), event);
        }
    }

    // =========================================================================
    // SWING & INTERACTION EVENTS
    // =========================================================================
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ServerPlayer player) {
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                executeMilestones(player, TriggerType.ON_SWING, null, event);
            }
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof ServerPlayer player) {
            LivingEntity target = event.getTarget() instanceof LivingEntity le ? le : null;
            executeMilestones(player, TriggerType.ON_SWING, target, event);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side == LogicalSide.SERVER && event.phase == TickEvent.Phase.END) {
            ServerPlayer player = (ServerPlayer) event.player;

            executeMilestones(player, TriggerType.ON_TICK, player, event);

            // Zuverlässige Erkennung von Schlägen ins Leere (Air-Swing)
            if (player.swinging && player.swingTime >= 0 && player.swingTime <= 1 && player.swingingArm == InteractionHand.MAIN_HAND) {
                if (player.attackAnim == 0.0F) {
                    executeMilestones(player, TriggerType.ON_SWING, null, event);
                }
            }
        }
    }

    // =========================================================================
    // WORLD & PHYSICAL EVENTS
    // =========================================================================
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            executeMilestones(player, TriggerType.ON_BLOCK_BREAK, null, event);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().getOwner() instanceof ServerPlayer player) {
            LivingEntity hitTarget = null;
            if (event.getRayTraceResult() instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) {
                hitTarget = le;
            }
            executeMilestones(player, TriggerType.ON_PROJECTILE_HIT, hitTarget, event);
        }
    }

    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            executeMilestones(player, TriggerType.ON_JUMP, null, event);
        }
    }

    // =========================================================================
    // CORE LOGIC (Übermittlung an KubeJS Bridge)
    // =========================================================================
    public static void executeMilestones(ServerPlayer player, TriggerType trigger, Event event) {
        executeMilestones(player, trigger, null, event);
    }

    public static void executeMilestones(ServerPlayer player, TriggerType trigger, LivingEntity target, Event event) {
        if (player == null || trigger == null) return;

        List<CachedMilestone> milestones = RuneCalculator.getActiveMilestones(player);
        if (milestones.isEmpty()) return;

        for (CachedMilestone cached : milestones) {
            if (cached.runeId != null) {
                String cleanRuneId = cached.runeId.getPath().replace("stones:", "");
                StonesScriptBridge.dispatchTrigger(player, cleanRuneId, trigger.id, target, event);
            }
        }
    }

    public static boolean isValidRune(ItemStack stack, ShrineInstance shrine, int index, int playerLevel) {
        if (stack.isEmpty() || !(stack.getItem() instanceof StoneItem)) return false;
        for (ShrineInstance.SlotConfig cfg : shrine.getLayout()) {
            if (cfg.inventoryIndex == index) {
                return playerLevel >= cfg.requiredLevel;
            }
        }
        return false;
    }
}