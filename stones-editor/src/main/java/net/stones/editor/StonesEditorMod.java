package net.stones.editor;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.stones.editor.network.StudioNetwork;
import org.slf4j.Logger;

@Mod(StonesEditorMod.MODID)
public class StonesEditorMod {
    public static final String MODID = "stones_editor";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StonesEditorMod() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);

        // Korrekter Forge 1.20.1 Aufruf (ohne fehlerhaftes NetworkConstants)
        ModLoadingContext.get().registerExtensionPoint(
            IExtensionPoint.DisplayTest.class,
            () -> new IExtensionPoint.DisplayTest(
                () -> IExtensionPoint.DisplayTest.IGNORESERVERONLY,
                (remoteVersion, isFromServer) -> true
            )
        );
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(StudioNetwork::registerPackets);
    }
}