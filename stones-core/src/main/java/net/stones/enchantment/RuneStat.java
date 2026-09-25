package net.stones.enchantment;

import net.minecraft.util.Mth;
import javax.annotation.Nullable;

/**
 * Repräsentiert eine dynamische Statistik einer Rune.
 * Unterstützt optionale Min/Max-Werte zur flexiblen Begrenzung der berechneten Werte.
 */
public record RuneStat(
    String id,
    String label,
    String type,         // z.B. "magic_damage", "cooldown"
    float base,
    float perLevel,
    String scaling,      // "RUNE_LEVEL", "SOCK_LEVEL", "PLAYER_LEVEL"
    float displayFactor, // Zum Umrechnen (Ticks in Sekunden)
    String suffix,       // "s", "%", etc.
    @Nullable Float min, // Optionale Untergrenze (null, wenn nicht definiert)
    @Nullable Float max  // Optionale Obergrenze (null, wenn nicht definiert)
) {
    /**
     * Berechnet den finalen Wert basierend auf der gewählten Skalierung.
     * Die Min/Max-Grenzen werden nur angewendet, wenn sie explizit gesetzt wurden.
     */
    public float calculate(float runeLvl, float sockLvl, float playerLvl, double mult) {
        float level = switch (scaling) {
            case "RUNE_LEVEL" -> runeLvl;
            case "SOCK_LEVEL" -> sockLvl;
            default -> playerLvl;
        };
        
        // --- DIE EXTRA MEILE: KORREKTE WACHSTUMS-SKALIERUNG ---
        // Anstatt das fertige Ergebnis zu multiplizieren (was debuffs wie CDs ruinieren würde),
        // multiplizieren wir das 'Level-Potential'. 
        // Eine Stufe 1 Rune mit 1.5x Amplify verhält sich wie eine Stufe 1.5 Rune.
        // Formel: Base + (Wachstum * ( (Level * Multiplikator) - Startpunkt ))
        float effectiveBonusLevel = (float)((level * mult) - 1.0);
        float result = base + (perLevel * effectiveBonusLevel);
        
        if (min != null) result = Math.max(result, min);
        if (max != null) result = Math.min(result, max);
        
        return result;
    }
}