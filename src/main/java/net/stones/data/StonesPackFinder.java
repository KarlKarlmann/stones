package net.stones.data;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.stones.StonesMod;
import net.stones.init.StonesModConfig;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.nio.file.Files;

/**
 * Der globale Loader für das Stones Studio Datapack.
 * Registriert eine dynamische RepositorySource, die bei JEDEM Reload (und Weltbeitritt) 
 * die Config neu ausliest, den Ordner scannt und das aktuell ausgewählte Pack 
 * mit höchster Priorität in Minecraft injiziert.
 * * BEHOBEN: Verhindert die automatische, unvollständige Generierung leerer/kaputter Workspaces beim Spielstart.
 */
@Mod.EventBusSubscriber(modid = StonesMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class StonesPackFinder {

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA) {
            
            File globalDatapacksDir = FMLPaths.GAMEDIR.get().resolve("datapacks").toFile();
            if (!globalDatapacksDir.exists()) {
                globalDatapacksDir.mkdirs();
            }

            event.addRepositorySource((packConsumer) -> {
                String targetProject = StonesModConfig.ACTIVE_WORKSPACE_PACK.get();

                if (targetProject == null || targetProject.isEmpty()) {
                    return; 
                }

                File packDir = new File(globalDatapacksDir, targetProject);
                // FIX: Wenn der Ordner nicht existiert, erstellen wir ihn NICHT stillschweigend im Hintergrund.
                // Das verhindert unvollständige Daten-Exporte vor der Registrierungs-Initialisierung.
                if (!packDir.exists()) {
                    return;
                }

                ensurePackExists(globalDatapacksDir, targetProject);

                FolderRepositorySource localDiscoverySource = new FolderRepositorySource(
                    globalDatapacksDir.toPath(), 
                    PackType.SERVER_DATA,
                    PackSource.DEFAULT 
                );

                localDiscoverySource.loadPacks((discoveredPackInfo) -> {
                    String discoveredName = discoveredPackInfo.getId().replace("file/", "");

                    if (discoveredName.equals(targetProject)) {
                        Pack customizedPack = Pack.create(
                            "stones_workspace/" + targetProject, 
                            Component.literal("Stones Mod: " + targetProject),
                            true, 
                            (name) -> discoveredPackInfo.open(),
                            new Pack.Info(
                                Component.literal("Aktives Stones Projekt (aus Config)"), 
                                15, 
                                FeatureFlags.DEFAULT_FLAGS 
                            ),
                            PackType.SERVER_DATA,
                            Pack.Position.TOP, 
                            true, 
                            PackSource.BUILT_IN
                        );
                        
                        if (customizedPack != null) {
                            packConsumer.accept(customizedPack);
                            StonesMod.LOGGER.info("[Stones] Aktives Studio-Projekt dynamisch als Datapack geladen: {}", targetProject);
                        }
                    }
                });
            });
        }
    }

    /**
     * Stellt sicher, dass ein existierendes Projekt über die korrekte pack.mcmeta verfügt.
     * Erstellt KEINE Runen-Dateien mehr beim Spielstart, da dies erst im voll-geladenen Spielzustand geschehen darf.
     */
    private static void ensurePackExists(File datapacksDir, String packName) {
        File packDir = new File(datapacksDir, packName);
        if (!packDir.exists()) {
            return; // Sicherheits-Guard
        }

        File metaFile = new File(packDir, "pack.mcmeta");
        if (!metaFile.exists()) {
            try {
                String defaultMeta = "{\n" +
                        "  \"pack\": {\n" +
                        "    \"pack_format\": 15,\n" +
                        "    \"description\": \"Stones Studio Workspace: " + packName + "\"\n" +
                        "  }\n" +
                        "}";
                Files.writeString(metaFile.toPath(), defaultMeta);
            } catch (Exception e) {
                StonesMod.LOGGER.error("[Stones] Fehler beim Schreiben der pack.mcmeta für '" + packName + "': ", e);
            }
        }

        File enchantmentsDir = new File(packDir, "data/stones_workspace/enchantments");
        if (!enchantmentsDir.exists()) {
            enchantmentsDir.mkdirs();
        }
    }
}