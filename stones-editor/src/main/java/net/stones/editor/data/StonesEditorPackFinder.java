package net.stones.editor.data;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.stones.editor.StonesEditorMod;
import net.stones.editor.init.StonesEditorConfig;

import java.io.File;
import java.nio.file.Files;

@Mod.EventBusSubscriber(modid = StonesEditorMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class StonesEditorPackFinder {

    @SubscribeEvent
    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.SERVER_DATA) {
            File globalDatapacksDir = FMLPaths.GAMEDIR.get().resolve("datapacks").toFile();
            if (!globalDatapacksDir.exists()) {
                globalDatapacksDir.mkdirs();
            }

            event.addRepositorySource((packConsumer) -> {
                String targetProject = StonesEditorConfig.ACTIVE_WORKSPACE_PACK.get();

                if (targetProject == null || targetProject.isEmpty()) {
                    return;
                }

                File packDir = new File(globalDatapacksDir, targetProject);
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
                            StonesEditorMod.LOGGER.info("[Stones Editor] Aktives Studio-Projekt dynamisch geladen: {}", targetProject);
                        }
                    }
                });
            });
        }
    }

    private static void ensurePackExists(File datapacksDir, String packName) {
        File packDir = new File(datapacksDir, packName);
        if (!packDir.exists()) return;

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
                StonesEditorMod.LOGGER.error("[Stones Editor] Fehler beim Schreiben der pack.mcmeta für '" + packName + "': ", e);
            }
        }

        File enchantmentsDir = new File(packDir, "data/stones_workspace/enchantments");
        if (!enchantmentsDir.exists()) {
            enchantmentsDir.mkdirs();
        }
    }
}