package net.stones.enchantment.behavior;

import com.google.gson.JsonObject;

/**
 * Interface für alle Rune Actions
 * 
 * Actions haben Zugriff auf:
 * - ActionContext (Player, Event, Variables)
 * - JsonObject params (Konfiguration aus JSON)
 */
public interface RuneAction {
    
    /**
     * Die ID der Aktion (z.B. "stones:explode", "yourmod:custom_effect")
     * Muss einzigartig sein!
     */
    String getId();

    /**
     * Führt die Aktion aus
     * 
     * @param ctx Der ActionContext mit allen relevanten Daten
     * @param params Die JSON-Parameter aus der Enchantment-Definition
     */
    void execute(ActionContext ctx, JsonObject params);
    
    /**
     * Optional: Validierung der Parameter beim Laden
     * Wird beim Registrieren des Enchantments aufgerufen
     * 
     * @param params Die JSON-Parameter
     * @return true wenn Parameter valide sind
     */
    default boolean validateParams(JsonObject params) {
        return true;
    }
    
    /**
     * Optional: Beschreibung der Aktion für Debugging/Logs
     */
    default String getDescription() {
        return "No description provided";
    }
}