package net.stones.enchantment.behavior;

import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RuneBehavior {

    private static final Logger LOGGER = LogManager.getLogger();
    
    // --- PLAYER-SPECIFIC GOVERNOR STATE ---
    private static int lastGlobalTick = -1;
    private static final Map<UUID, Integer> playerTickUsage = new HashMap<>();
    
    // Engine-Hard-Cap: Lässt absurde Kombos zu, schützt aber den Server vor StackOverflows.
    // 150 Aktionen in 1/20 Sekunde sind genug für jeden Boss-Kill.
    // Dieses Limit gilt nun PRO SPIELER.
    private static final int MAX_TRIGGERS_PER_TICK = 150; 

    public final TriggerType trigger;
    public final List<RuneCondition> conditions;
    public final List<ConfiguredRuneAction> actions;

    public RuneBehavior(TriggerType trigger, List<RuneCondition> conditions, List<ConfiguredRuneAction> actions) {
        this.trigger = trigger;
        this.conditions = conditions != null ? conditions : new ArrayList<>();
        this.actions = actions;
    }
    
    public boolean testConditions(ActionContext context) {
        for (RuneCondition condition : conditions) {
            if (!condition.test(context)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Prüft, ob in diesem Tick noch Aktionen ausgeführt werden dürfen.
     * Fungiert als unsichtbares Engine-Cap für Spieler-Kombos.
     */
    private boolean consumeAllowance(ActionContext context) {
        int currentTick = context.getPlayer().getServer().getTickCount();
        
        // Neuer Tick? Map komplett leeren (verhindert Memory Leaks und ist hochperformant)
        if (currentTick != lastGlobalTick) {
            playerTickUsage.clear();
            lastGlobalTick = currentTick;
        }

        UUID playerId = context.getPlayer().getUUID();
        int usage = playerTickUsage.getOrDefault(playerId, 0);

        if (usage >= MAX_TRIGGERS_PER_TICK) {
            // Wir loggen es nur noch leise im Debug-Modus für Server-Admins.
            if (usage == MAX_TRIGGERS_PER_TICK) {
                LOGGER.debug("Stones Mod: Engine-Cap ({} Triggers/Tick) erreicht durch Kombo für Spieler {}. Letzte Rune: '{}', Trigger: '{}'.",
                    MAX_TRIGGERS_PER_TICK, context.getPlayer().getName().getString(), context.getRuneId(), this.trigger.id);
            }
            
            playerTickUsage.put(playerId, usage + 1); // Zählt weiter hoch, um das Cap zu halten
            return false; // Limit erreicht -> Leise abwürgen (Cap)
        }

        playerTickUsage.put(playerId, usage + 1);
        return true; // Ausführung erlaubt
    }
    
    public void execute(ActionContext context) {
        // 1. Teste zuerst die Bedingungen (KOSTET NOCH KEIN LIMIT!)
        if (!testConditions(context)) {
            return;
        }
        
        // 2. Bedingungen erfüllt? Hole Erlaubnis vom Governor für die ECHTE Ausführung!
        if (!consumeAllowance(context)) {
            return; // Engine-Cap greift sanft ein -> Die Kombo hat ihr Maximum für diesen Tick erreicht.
        }
        
        // 3. Aktionen ausführen
        for (ConfiguredRuneAction action : actions) {
            action.execute(context);
        }
    }
    
    public static class ConfiguredRuneAction {
        public final RuneAction action;
        public final JsonObject params;
        
        public ConfiguredRuneAction(RuneAction action, JsonObject params) {
            this.action = action;
            this.params = params;
        }
        
        public void execute(ActionContext context) {
            if (action != null) {
                action.execute(context, this.params);
            }
        }
    }
}