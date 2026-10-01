package net.stones.editor;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.NetworkConstants;
import net.stones.editor.network.StudioNetwork;
import org.slf4j.Logger;

@Mod(StonesEditorMod.MODID)
public class StonesEditorMod {
    public static final String MODID = "stones_editor";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StonesEditorMod() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);

        // 1. Handshake-Toleranz: Erlaubt Verbindungen auf Server ohne Editor oder mit anderer Version
        ModLoadingContext.get().registerExtensionPoint(
            IExtensionPoint.DisplayTest.class,
            () -> new IExtensionPoint.DisplayTest(
                () -> NetworkConstants.IGNORESERVERONLY,
                (remoteVersion, isFromServer) -> true
            )
        );

        // HINWEIS: StonesEditorConfig wurde entfernt, da das aktive Projekt
        // jetzt weltgebunden direkt in <world>/datapacks/stones_runtime/pack.mcmeta verwaltet wird.
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(StudioNetwork::registerPackets);
    }
}