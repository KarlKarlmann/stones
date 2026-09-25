package net.stones.editor.event;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.stones.editor.StonesEditorMod;
import net.stones.editor.init.StonesEditorConfig;
import net.stones.util.TemplateHashHelper;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@Mod.EventBusSubscriber(modid = StonesEditorMod.MODID)
public class StonesEditorServerEvents {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.hasPermissions(2)) return;

        File datapacksDir = FMLPaths.GAMEDIR.get().resolve("datapacks").toFile();
        if (!datapacksDir.exists() || datapacksDir.listFiles() == null) return;

        String activeConfigPack = StonesEditorConfig.ACTIVE_WORKSPACE_PACK.get();

        boolean foundInactivePack = false;
        for (File file : datapacksDir.listFiles()) {
            String name = file.getName();
            if ((name.contains("stone") || name.contains("rune")) && !name.equals(activeConfigPack)) {
                foundInactivePack = true;
                break;
            }
        }

        if (foundInactivePack) {
            player.sendSystemMessage(
                Component.translatable("gui.stones.studio.toast.new_packs_found")
                    .append(" - ")
                    .append(Component.translatable("gui.stones.studio.toast.activation_hint"))
            );
        }

        if (activeConfigPack == null || activeConfigPack.isEmpty()) return;

        File enchantmentsDir = new File(datapacksDir, activeConfigPack + "/data/stones_workspace/enchantments");
        if (!enchantmentsDir.exists() || enchantmentsDir.listFiles() == null) return;

        boolean hasUpdatedCoreTemplates = false;

        for (File file : enchantmentsDir.listFiles()) {
            if (!file.getName().endsWith(".json")) continue;
            String fileName = file.getName();

            try (InputStream is = StonesEditorServerEvents.class.getResourceAsStream("/data/stones/enchantments/" + fileName)) {
                if (is != null) {
                    try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        JsonObject jarJson = JsonParser.parseReader(reader).getAsJsonObject();
                        String jarHash = TemplateHashHelper.calculateHash(jarJson);

                        String fileContent = Files.readString(file.toPath());
                        JsonObject playerJson = JsonParser.parseString(fileContent).getAsJsonObject();

                        String storedHash = playerJson.has(TemplateHashHelper.HASH_KEY)
                            ? playerJson.get(TemplateHashHelper.HASH_KEY).getAsString() : "";

                        if (!storedHash.equals(jarHash)) {
                            hasUpdatedCoreTemplates = true;
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (hasUpdatedCoreTemplates) {
            MutableComponent buttonComponent = Component.translatable("chat.stones.studio.templates_updated.button")
                .withStyle(style -> style
                    .withColor(ChatFormatting.LIGHT_PURPLE)
                    .withBold(true)
                    .withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/stonesstudio autoupdate"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("chat.stones.studio.templates_updated.hover")))
                );

            MutableComponent msg = Component.translatable("chat.stones.studio.templates_updated.header")
                .append("\n")
                .append(Component.translatable("chat.stones.studio.templates_updated.body"))
                .append("\n")
                .append(buttonComponent);

            player.sendSystemMessage(msg);
        }
    }
}