package net.stones.visuals.integration;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.stones.init.StonesModBlockEntities;
import net.stones.visuals.client.renderer.VisualRunestoneRenderer;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.stones.block.entity.RunestoneBlockEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class VisualsEventHandler {

    // Die Liste wohnt jetzt hier – direkt wo der VisualRunestoneRenderer sie braucht!
    public static final List<ResourceLocation> SHRINE_ARTIFACTS = new ArrayList<>();

	@SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Cast löst den Wildcard-Capture Inferenz-Fehler
        event.registerBlockEntityRenderer(
            (BlockEntityType<RunestoneBlockEntity>) (BlockEntityType<?>) StonesModBlockEntities.RUNESTONE.get(), 
            VisualRunestoneRenderer::new
        );
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public void onRegisterModels(ModelEvent.RegisterAdditional event) {
        SHRINE_ARTIFACTS.clear();
        try {
            // Wir scannen den Ordner assets/stones/models/shrine_decor im Skinpack
            Path decorPath = ModList.get().getModFileById("stones_visuals").getFile()
                .findResource("assets", "stones", "models", "shrine_decor");

            if (Files.exists(decorPath)) {
                try (Stream<Path> walk = Files.walk(decorPath, 1)) {
                    walk.filter(p -> p.toString().endsWith(".json"))
                        .forEach(p -> {
                            String filename = p.getFileName().toString().replace(".json", "");
                            ResourceLocation loc = new ResourceLocation("stones", "shrine_decor/" + filename);
                            
                            // 1. Bei Forge registrieren
                            event.register(loc);
                            
                            // 2. In der Liste für den VisualRunestoneRenderer speichern
                            SHRINE_ARTIFACTS.add(loc);
                        });
                }
            }
        } catch (Exception e) {
            // Fehlerbehandlung
        }
    }
}