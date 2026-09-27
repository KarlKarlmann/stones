package net.stones.visuals;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(StonesVisuals.MODID)
public class StonesVisuals {
    public static final String MODID = "stones_visuals";
    private static final Logger LOGGER = LogManager.getLogger();

    public StonesVisuals() {
        // Stop auf Dedicated Servern vor jeglichem Klassen-Laden
        if (FMLEnvironment.dist != Dist.CLIENT) {
            LOGGER.info("[Stones Visuals] Dedicated Server erkannt. Client-Mod pausiert.");
            return;
        }

        boolean hasStones = ModList.get().isLoaded("stones");
        boolean hasKube = ModList.get().isLoaded("stoneskube");

        if (hasStones && hasKube) {
            throw new IllegalStateException("[Stones Visuals] 'stones' und 'stonesKube' duerfen nicht gleichzeitig installiert sein!");
        }

        if (hasStones || hasKube) {
            LOGGER.info("[Stones Visuals] Core-Mod gefunden! Lade Client-Features...");
            VisualsInitializer.start();
        } else {
            LOGGER.warn("[Stones Visuals] Keine Core-Mod gefunden. Standby.");
        }
    }
}