package net.stones.editor;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.stones.editor.init.StonesEditorConfig;
import net.stones.editor.network.StudioNetwork;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(StonesEditorMod.MODID)
public class StonesEditorMod {
    public static final String MODID = "stones_editor";
    public static final Logger LOGGER = LogManager.getLogger(StonesEditorMod.class);

    public StonesEditorMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Editor-Config registrieren
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, StonesEditorConfig.SPEC);

        modEventBus.addListener(this::setup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("Registriere Netzwerk-Pakete für Stones Editor...");
        StudioNetwork.registerPackets();
    }
}