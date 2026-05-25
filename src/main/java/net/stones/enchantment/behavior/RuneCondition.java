package net.stones.enchantment.behavior;

import com.google.gson.JsonObject;

/**
 * Interface für wiederverwendbare Conditions
 * Erlaubt modulare Bedingungen die in JSON referenziert werden können
 */
public interface RuneCondition {
    
    /**
     * Die ID der Condition (z.B. "stones:health_below", "yourmod:in_nether")
     */
    String getId();
    
    /**
     * Prüft ob die Bedingung erfüllt ist
     * @param context Der ActionContext mit allen relevanten Daten
     * @return true wenn Bedingung erfüllt ist
     */
    boolean test(ActionContext context);
    
    /**
     * Optional: Validierung der Parameter beim Laden
     */
    default boolean validateParams(JsonObject params) {
        return true;
    }
    
    /**
     * Optional: Beschreibung der Condition
     */
    default String getDescription() {
        return "No description provided";
    }
}