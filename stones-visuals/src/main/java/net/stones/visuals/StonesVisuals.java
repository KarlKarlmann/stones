package net.stones.visuals;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(StonesVisuals.MODID)
public class StonesVisuals {
    public static final String MODID = "stones_visuals";
    private static final Logger LOGGER = LogManager.getLogger();

    public StonesVisuals() {
        boolean hasStones = ModList.get().isLoaded("stones");
        boolean hasKube = ModList.get().isLoaded("stoneskube");

        // Split-Package-Schutz bleibt bestehen!
        if (hasStones && hasKube) {
            throw new IllegalStateException("[Stones Visuals] Kritischer Fehler: 'stones' und 'stonesKube' duerfen niemals gleichzeitig installiert sein!");
        }

        // DEINE LOGIK: Ist eine der beiden da?
        if (hasStones || hasKube) {
            LOGGER.info("[Stones Visuals] Core-Mod gefunden! Lade visuelle Overhauls...");
            
            // WICHTIG: Wir rufen eine separate Klasse auf!
            // Hier oben bei den Imports darf NICHTS von net.stones... stehen.
            VisualsInitializer.start();
        } else {
            // Keine Core-Mod da? Dann endet der Spaß hier.
            // Die Mod stürzt nicht ab, sie macht einfach gar nichts.
            LOGGER.warn("[Stones Visuals] Keine Stones Core-Mod (stones/stonesKube) gefunden. Visuals gehen in den Standby-Modus.");
        }
    }
}