package net.stones.enchantment.behavior;

import java.util.HashMap;
import java.util.Map;

/**
 * Extensible Enum für Stones-Trigger (mit Strict Validation).
 * Verhält sich performant wie ein Enum (O(1) Vergleiche mit ==),
 * erlaubt aber anderen Mods das Registrieren von Triggern, ohne Datapack-Typos durchzuwinken.
 */
public class TriggerType {
    private static final Map<String, TriggerType> REGISTRY = new HashMap<>();

    // --- VANILLA / STONES CORE TRIGGERS ---
    public static final TriggerType ON_ATTACK = register("ON_ATTACK");
    public static final TriggerType ON_HURT = register("ON_HURT");
    public static final TriggerType ON_KILL = register("ON_KILL");
    public static final TriggerType ON_SWING = register("ON_SWING");
    public static final TriggerType ON_TICK = register("ON_TICK");
    public static final TriggerType ON_BLOCK_BREAK = register("ON_BLOCK_BREAK");
    public static final TriggerType ON_PROJECTILE_HIT = register("ON_PROJECTILE_HIT");
    public static final TriggerType ON_JUMP = register("ON_JUMP");
    public static final TriggerType ON_ACTION_BUTTON = register("ON_ACTION_BUTTON");

    public final String id;

    private TriggerType(String id) {
        this.id = id;
    }

    /**
     * FÜR MODS: Registriert einen neuen Trigger beim Spielstart.
     * Bridge-Mods (wie Iron's Spells Bridge) nutzen dies, um ihre Events anzumelden.
     */
    public static TriggerType register(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Trigger-ID darf nicht leer sein!");
        }
        String upperId = id.trim().toUpperCase();
        return REGISTRY.computeIfAbsent(upperId, TriggerType::new);
    }

    /**
     * FÜR DEN JSON-PARSER: Holt einen registrierten Trigger.
     * Wirft absichtlich einen Fehler, wenn der Trigger nicht von einer Mod angemeldet wurde!
     */
    public static TriggerType get(String id) {
        if (id == null || id.trim().isEmpty()) return null;
        String upperId = id.trim().toUpperCase();
        
        TriggerType type = REGISTRY.get(upperId);
        if (type == null) {
            // Hier knallt es absichtlich! Der Datapack-Ersteller hat sich vertippt 
            // oder die zugehörige Mod fehlt.
            throw new IllegalArgumentException(
                "CRITICAL STONES ERROR: Unbekannter TriggerType im JSON: '" + id + "'! " +
                "Entweder hast du dich im JSON vertippt, oder die Mod, die diesen Trigger bereitstellt, ist nicht installiert."
            );
        }
        return type;
    }
}