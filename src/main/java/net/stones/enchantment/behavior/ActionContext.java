package net.stones.enchantment.behavior;

import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.stones.enchantment.RuneEnchantment;
import net.stones.enchantment.RuneStat;
import net.stones.logic.RuneCalculator;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Hält den Zustand während der Ausführung einer Rune.
 * Aktualisiert für Amplify: Berechnet nun verstärkte Level-Variablen
 * und stellt den Multiplikator als eigene Variable zur Verfügung.
 */
public class ActionContext {
    
    private final ServerPlayer player;
    private final Event event;
    private final JsonObject params;
    private final Map<String, Object> variables = new HashMap<>();
    private final String runeId;

    public ActionContext(ServerPlayer player, @Nullable Event event, JsonObject params, String runeId) {
        this.player = player;
        this.event = event;
        this.params = params;
        this.runeId = runeId;
        initializeEventVariables();
    }

    /**
     * Legacy-Overload für Aufrufe ohne expliziten Multiplikator.
     * Verhindert Build-Fehler in Modulen wie dem ActionSystem.
     */
    public void setContextLevels(RuneEnchantment ench, int runeLevel, int sockLevel) {
        this.setContextLevels(ench, runeLevel, sockLevel, 1.0);
    }

    /**
     * Setzt die Level-Variablen. Jetzt mit Amplify-Support.
     * @param multiplier Der Resonanz-Multiplikator (1.0 = keine Verstärkung).
     */
    public void setContextLevels(RuneEnchantment ench, int runeLevel, int sockLevel, double multiplier) {
        // Berechne effektive Level für die Variablen-Exposition
        int effRune = (int)(runeLevel);
        int effSock = (int)(sockLevel);
		if(sockLevel == 0) effSock = (int)(runeLevel);
        int effPlayer = (int)(player.experienceLevel - effSock);

        setVariable("RuneLevel", (float) effRune);
        setVariable("SockLevel", (float) effSock);
        setVariable("PlayerLevel", (float) effPlayer); 
        
        // Multiplikator als Variable für Custom Actions/Logiken bereitstellen
        setVariable("AmplifyMultiplier", (float) multiplier);

        // Berechne alle Stats der Rune mit eingerechneter Verstärkung via Single-Source-of-Truth
        for (RuneStat stat : ench.getStats()) {
            float val = RuneCalculator.calculateStatValue(stat, runeLevel, sockLevel, player.experienceLevel, multiplier);
            setVariable(stat.id(), val);
        }
    }
    
    private void initializeEventVariables() {
        setVariable("player", player);
        setVariable("level", player.level());
        setVariable("playerHealth", player.getHealth());
        setVariable("playerLevel", (float) player.experienceLevel);

        if (event instanceof LivingHurtEvent hurtEvent) {
            setVariable("damage", hurtEvent.getAmount());
            setVariable("victim", hurtEvent.getEntity());
            if (hurtEvent.getSource().getEntity() instanceof LivingEntity attacker) {
                setVariable("attacker", attacker);
            }
        } else if (event instanceof LivingDeathEvent deathEvent) {
            setVariable("victim", deathEvent.getEntity());
        } else if (event instanceof BlockEvent.BreakEvent breakEvent) {
            setVariable("blockPos", breakEvent.getPos());
            setVariable("blockState", breakEvent.getState());
        } else if (event instanceof ProjectileImpactEvent impactEvent) {
            setVariable("projectile", impactEvent.getProjectile());
            setVariable("hitPos", impactEvent.getRayTraceResult().getLocation());
        }
    }
    
    public ServerPlayer getPlayer() { return player; }
    public @Nullable Event getEvent() { return event; }
    public void setVariable(String key, Object value) { variables.put(key, value); }
    public String getRuneId() { return runeId; }
    public @Nullable Object getVariable(String key) {
        return variables.get(key);
    }
	
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key, Class<T> type) {
        Object val = variables.get(key);
        return type.isInstance(val) ? (T) val : null;
    }
    
    public Map<String, Object> getVariables() { return this.variables; }
    
    public float getFloat(String key, float defaultValue) {
        Object val = variables.get(key);
        return (val instanceof Number num) ? num.floatValue() : defaultValue;
    }
    
    public void modifyDamage(float multiplier, float addition) {
        if (event instanceof LivingHurtEvent hurtEvent) {
            float newDamage = (hurtEvent.getAmount() * multiplier) + addition;
            hurtEvent.setAmount(newDamage);
            setVariable("damage", newDamage);
        }
    }
}