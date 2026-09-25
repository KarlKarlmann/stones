package net.stones.client.integration;

import com.mojang.datafixers.util.Either;
import net.actionbar.features.ActionSystem.ActionDataProvider;
import net.actionbar.features.ActionSystem.ActionGatherer;
import net.actionbar.features.ActionSystem.ActionTriggerHandler;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.stones.StonesMod;
import net.stones.features.ActionSystem;
import net.stones.network.PacketPerformAction;

import java.util.List;
import java.util.Map;

/**
 * Isolierte Brücke zur Actionbar-Mod.
 */
public class ActionbarCompat {

    public static void register() {
        net.actionbar.features.ActionSystem.registerGatherer(new ActionGatherer() {
            @Override
            public void gather(List<ResourceLocation> activeActions, Map<ResourceLocation, Integer> actionLevels) {
                if (!ActionSystem.isSyncingWithActionbar) {
                    ActionSystem.isSyncingWithActionbar = true;
                    ActionSystem.refreshCalculatedActions(); 
                    ActionSystem.isSyncingWithActionbar = false;
                }
                activeActions.addAll(ActionSystem.getCalculatedActionsCache());
                actionLevels.putAll(ActionSystem.getActionLevelCache());
            }
        });

        net.actionbar.features.ActionSystem.registerDataProvider(new ActionDataProvider() {
            @Override 
            public ResourceLocation getIcon(String id) { return ActionSystem.getActionIcon(id); }
            
            @Override 
            public List<Either<FormattedText, TooltipComponent>> getTooltip(String id, int level) { return ActionSystem.getActionTooltip(id); }
            
            @Override 
            public int getCooldown(String id) { return ActionSystem.getActionCooldown(id); }
        });

        net.actionbar.features.ActionSystem.registerTriggerHandler(new ActionTriggerHandler() {
            @Override
            public boolean onTrigger(String id, int slot) {
                ResourceLocation loc = ResourceLocation.tryParse(id);
                // FIX: Wir prüfen gegen den CALCULATED_ACTIONS_CACHE!
                // Hier stecken Runen UND Zauber aus der Bridge drin.
                if (loc != null && ActionSystem.getCalculatedActionsCache().contains(loc)) {
                    StonesMod.PACKET_HANDLER.sendToServer(new PacketPerformAction(id, slot));
                    return true;
                }
                return false; 
            }
        });
    }

    public static void refresh() {
        net.actionbar.features.ActionSystem.refreshCalculatedActions();
    }
}