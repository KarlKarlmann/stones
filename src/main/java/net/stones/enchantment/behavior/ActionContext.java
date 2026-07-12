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
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Hält den Zustand während der Ausführung einer Rune.
 * Exponiert das 'target' und befüllt das Caster-Combo-Inventar.
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
        initializeComboVariables(); // Automatisches Laden aller aktiven Combos
    }

    private static class ComboData {
        float count;
        long remainingTimeout;
        int max;
        String texture;
        float size;
        float radius;
        float speed;
        String color;
    }

    /**
     * Scannt den Spieler (Caster) nach aktiven Combos.
     * Exponiert die Werte unter flachen Namen wie $<id>_count und $<id>_timeout.
     * Die Target-Combos werden jetzt explizit per stones:get_combo geholt!
     */
    private void initializeComboVariables() {
        if (player == null) return;
        CompoundTag playerPersist = player.getPersistentData();
        long now = player.level().getGameTime();
        
        Map<String, ComboData> activeCombos = new HashMap<>();
        
        // Combos des Spielers (Casters) einlesen
        scanCombosForEntity(player, now, activeCombos);
        
        // In den Kontext-Variablen-Pool injizieren
        for (Map.Entry<String, ComboData> entry : activeCombos.entrySet()) {
            String id = entry.getKey();
            ComboData data = entry.getValue();
            
            setVariable(id, data.count); // Einfache Abfrage: $storm_caller
            setVariable(id + "_count", data.count); // Explizite Abfrage: $storm_caller_count
            setVariable(id + "_timeout", (float) data.remainingTimeout); // Ticks verbleibend: $storm_caller_timeout
            setVariable(id + "_max", (float) data.max);
            setVariable(id + "_texture", data.texture);
            setVariable(id + "_size", data.size);
            setVariable(id + "_radius", data.radius);
            setVariable(id + "_speed", data.speed);
            setVariable(id + "_color", data.color);
        }
    }

    private void scanCombosForEntity(LivingEntity entity, long now, Map<String, ComboData> map) {
        CompoundTag persist = entity.getPersistentData();
        for (String key : new java.util.ArrayList<>(persist.getAllKeys())) {
            if (key.startsWith("stones_combo_") && key.endsWith("_count")) {
                String id = key.substring("stones_combo_".length(), key.length() - "_count".length());
                long expire = persist.getLong("stones_combo_" + id + "_expire");
                float count = persist.getFloat(key);
                
                // Falls abgelaufen (und kein unendlicher Timeout -1): Server- und Clientseitig zurücksetzen
                if (expire != -1L && expire > 0L && now >= expire) {
                    persist.putFloat(key, 0.0f);
                    persist.putLong("stones_combo_" + id + "_expire", 0L);
                    persist.remove("stones_combo_" + id + "_max");
                    persist.remove("stones_combo_" + id + "_texture");
                    persist.remove("stones_combo_" + id + "_size");
                    persist.remove("stones_combo_" + id + "_radius");
                    persist.remove("stones_combo_" + id + "_speed");
                    persist.remove("stones_combo_" + id + "_color");
                    count = 0.0f;
                    
                    // Client-HUD-Reset erzwingen
                    if (!entity.level().isClientSide) {
                        net.minecraftforge.network.PacketDistributor.PacketTarget target = 
                            net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity);
                        net.stones.StonesMod.PACKET_HANDLER.send(target, new net.stones.network.PacketSyncCombo(
                            id, entity.getId(), 0, 100, "minecraft:textures/particle/glint.png", 0, 0, 0, 0, 0, 0, 0, 0
                        ));
                    }
                }
                
                long remaining = (expire == -1L) ? -1L : Math.max(0L, expire - now);
                
                ComboData data = new ComboData();
                data.count = count;
                data.remainingTimeout = remaining;
                data.max = persist.getInt("stones_combo_" + id + "_max");
                data.texture = persist.getString("stones_combo_" + id + "_texture");
                data.size = persist.getFloat("stones_combo_" + id + "_size");
                data.radius = persist.getFloat("stones_combo_" + id + "_radius");
                data.speed = persist.getFloat("stones_combo_" + id + "_speed");
                data.color = persist.getString("stones_combo_" + id + "_color");
                map.put(id, data);
            }
        }
    }

    /**
     * Legacy-Overload für Aufrufe ohne expliziten Multiplikator.
     */
    public void setContextLevels(RuneEnchantment companion, int runeLevel, int sockLevel) {
        this.setContextLevels(companion, runeLevel, sockLevel, 1.0);
    }

    /**
     * Setzt die Level-Variablen für mathematische Ausdrücke.
     */
    public void setContextLevels(RuneEnchantment companion, int runeLevel, int sockLevel, double multiplier) {
        int effRune = runeLevel;
        int effSock = sockLevel;
        if(sockLevel == 0) effSock = runeLevel;
        int effPlayer = player.experienceLevel - effSock;

        setVariable("RuneLevel", (float) effRune);
        setVariable("SockLevel", (float) effSock);
        setVariable("PlayerLevel", (float) effPlayer); 
        setVariable("AmplifyMultiplier", (float) multiplier);

        // Berechne alle Statistiken der Rune
        for (RuneStat stat : companion.getStats()) {
            float val = RuneCalculator.calculateStatValue(stat, runeLevel, sockLevel, player.experienceLevel, multiplier);
            setVariable(stat.id(), val);
        }
    }
    
    private void initializeEventVariables() {
        setVariable("player", player);
        setVariable("level", player.level());
        setVariable("playerHealth", player.getHealth());
        setVariable("playerLevel", (float) player.experienceLevel);

        LivingEntity targetEntity = null;
        if (event instanceof LivingHurtEvent hurtEvent) {
            setVariable("damage", hurtEvent.getAmount());
            setVariable("victim", hurtEvent.getEntity());
            targetEntity = hurtEvent.getEntity();
            if (hurtEvent.getSource().getEntity() instanceof LivingEntity attacker) {
                setVariable("attacker", attacker);
                if (attacker != player) {
                    targetEntity = attacker;
                }
            }
        } else if (event instanceof LivingDeathEvent deathEvent) {
            setVariable("victim", deathEvent.getEntity());
            targetEntity = deathEvent.getEntity();
        } else if (event instanceof BlockEvent.BreakEvent breakEvent) {
            setVariable("blockPos", breakEvent.getPos());
            setVariable("blockState", breakEvent.getState());
        } else if (event instanceof ProjectileImpactEvent impactEvent) {
            setVariable("projectile", impactEvent.getProjectile());
            setVariable("hitPos", impactEvent.getRayTraceResult().getLocation());
            if (impactEvent.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le) {
                targetEntity = le;
            }
        } else if (event instanceof net.minecraftforge.event.entity.player.AttackEntityEvent attackEvent) {
            if (attackEvent.getTarget() instanceof LivingEntity le) {
                targetEntity = le;
            }
        }

        // Setzt das standardisierte Event-Ziel als $target
        if (targetEntity != null) {
            setVariable("target", targetEntity);
        }
    }
    
    public ServerPlayer getPlayer() { return player; }
    public @Nullable Event getEvent() { return event; }
    public void setVariable(String key, Object value) { variables.put(key, value); }
    public String getRuneId() { return runeId; }
    public @Nullable Object getVariable(String key) { return variables.get(key); }
	
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