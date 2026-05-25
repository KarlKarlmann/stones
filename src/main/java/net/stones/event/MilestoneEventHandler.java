package net.stones.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent; // NEU: Für zuverlässige Treffer-Erkennung
import net.minecraftforge.event.entity.player.PlayerInteractEvent; // NEU: Für Block-Klicks
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.stones.StonesMod;
import net.stones.cap.PlayerShrineCapProvider;
import net.stones.data.ShrineInstance;
import net.stones.data.ShrineSavedData;
import net.stones.enchantment.RuneEnchantment;
import net.stones.enchantment.behavior.ActionContext;
import net.stones.enchantment.behavior.RuneBehavior;
import net.stones.logic.RuneCalculator;
import net.stones.logic.RuneCalculator.CachedMilestone;
import net.stones.item.StoneItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.stones.enchantment.behavior.TriggerType;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Überarbeiteter Event-Handler.
 * Nutzt jetzt dedizierte Interact-Events für zuverlässiges ON_SWING.
 */
@Mod.EventBusSubscriber(modid = StonesMod.MODID)
public class MilestoneEventHandler {

    // --- DAMAGE EVENTS ---

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim) executeMilestones(victim, TriggerType.ON_HURT, event);
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) executeMilestones(attacker, TriggerType.ON_ATTACK, event);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            executeMilestones(attacker, TriggerType.ON_KILL, event);
        }
    }

    // --- SWING / INTERACTION EVENTS (Der Fix) ---

    /**
     * Feuert wenn ein Block geschlagen wird (Links-Klick).
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof ServerPlayer player) {
            // Verhindert Doppelauslösung wenn man gleichzeitig swingt
            if (event.getHand() == InteractionHand.MAIN_HAND) {
                executeMilestones(player, TriggerType.ON_SWING, event);
            }
        }
    }

    /**
     * Feuert wenn ein Entity geschlagen wird (Links-Klick).
     * Dient hier als Trigger für die "Bewegung" des Schlages, unabhängig vom Schaden.
     */
    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof ServerPlayer player) {
            executeMilestones(player, TriggerType.ON_SWING, event);
        }
    }

    /**
     * Fallback für Schläge in die Luft.
     * Da LeftClickEmpty Client-Only ist, nutzen wir hier eine robustere Tick-Logik.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.side == LogicalSide.SERVER && event.phase == TickEvent.Phase.END) {
            ServerPlayer player = (ServerPlayer) event.player;
            
            // Standard Tick Trigger
            executeMilestones(player, TriggerType.ON_TICK, event);

            // Verbesserte Swing-Erkennung für Luftschläge:
            // Wir prüfen > -1 (aktiv) und < 2 (gerade gestartet), um das Fenster zu vergrößern.
            // Zusätzlich prüfen wir attackAnim, um sicherzustellen, dass es ein Schlag ist.
            if (player.swinging && player.swingTime >= 0 && player.swingTime <= 1 && player.swingingArm == InteractionHand.MAIN_HAND) {
                // Wir feuern dies nur, wenn wir NICHT gerade etwas abbauen oder angreifen
                // (Die InteractEvents oben übernehmen die präzisen Fälle, um Dopplungen zu vermeiden)
                // attackAnim ist 0 am Anfang des Swings
                if (player.attackAnim == 0.0F) { 
                     executeMilestones(player, TriggerType.ON_SWING, event);
                }
            }
        }
    }

    // --- OTHER EVENTS ---

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            executeMilestones(player, TriggerType.ON_BLOCK_BREAK, event);
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().getOwner() instanceof ServerPlayer player) {
            executeMilestones(player, TriggerType.ON_PROJECTILE_HIT, event);
        }
    }

    @SubscribeEvent
    public static void onLivingJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            executeMilestones(player, TriggerType.ON_JUMP, event);
        }
    }

    // --- CORE LOGIC ---

    public static void executeMilestones(ServerPlayer player, TriggerType trigger, Event event) {
        // Holt sich das vorbereitete Array mit vollem Kontext in Millisekunden (O(1) Map Lookup)
        List<CachedMilestone> milestones = RuneCalculator.getActiveMilestones(player);
        
        for (CachedMilestone cached : milestones) {
            RuneEnchantment runeEnch = cached.rune;
            ResourceLocation idLoc = ForgeRegistries.ENCHANTMENTS.getKey(runeEnch);
            String runeId = idLoc != null ? idLoc.getPath() : "unknown";

            for (RuneBehavior behavior : runeEnch.getBehaviors()) {
                if (behavior.trigger == trigger) {
                    ActionContext context = new ActionContext(player, event, new com.google.gson.JsonObject(), runeId);
                    context.setContextLevels(runeEnch, cached.runeLevel, cached.socketLevel, cached.mult);
                    behavior.execute(context);
                }
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