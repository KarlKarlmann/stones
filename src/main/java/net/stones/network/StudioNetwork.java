package net.stones.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.stones.StonesMod;
import net.stones.client.gui.editor.StonesStudioScreen;
import net.stones.init.StonesModConfig;
import net.stones.util.ServerDatapackExporter;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Verwaltet die komplette Client-Server-Kommunikation für das Stones Studio.
 * Schützt den Server durch physische Trennung von Client-GUI-Klassen (Multiplayer Safe).
 */
public class StudioNetwork {

    private static final String PROTOCOL_VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StonesMod.MODID, "studio_channel"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void registerPackets() {
        CHANNEL.registerMessage(packetId++, C2SRequestPackList.class, C2SRequestPackList::encode, C2SRequestPackList::decode, C2SRequestPackList::handle);
        CHANNEL.registerMessage(packetId++, S2CSyncPackList.class,    S2CSyncPackList::encode,    S2CSyncPackList::decode,    S2CSyncPackList::handle);
        CHANNEL.registerMessage(packetId++, C2SProjectAction.class,   C2SProjectAction::encode,   C2SProjectAction::decode,   C2SProjectAction::handle);
        CHANNEL.registerMessage(packetId++, C2STriggerReload.class,   C2STriggerReload::encode,   C2STriggerReload::decode,   C2STriggerReload::handle);
        CHANNEL.registerMessage(packetId++, C2SRequestRuneFile.class, C2SRequestRuneFile::encode, C2SRequestRuneFile::decode, C2SRequestRuneFile::handle);
        CHANNEL.registerMessage(packetId++, S2CSyncRuneFile.class,    S2CSyncRuneFile::encode,    S2CSyncRuneFile::decode,    S2CSyncRuneFile::handle);
        CHANNEL.registerMessage(packetId++, C2SSaveRuneFile.class,    C2SSaveRuneFile::encode,    C2SSaveRuneFile::decode,    C2SSaveRuneFile::handle);
    }

    public static String sanitizeProjectName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    // =========================================================================
    // Hilfsmethode: Scannt das aktive Datapack und gibt Packliste + Dateiliste zurück
    // =========================================================================
    private static S2CSyncPackList buildSyncPacket() {
        File datapacksDir = FMLPaths.GAMEDIR.get().resolve("datapacks").toFile();
        List<String> packs = new ArrayList<>();
        List<String> activeFiles = new ArrayList<>();
        String activePack = StonesModConfig.ACTIVE_WORKSPACE_PACK.get();

        if (datapacksDir.exists() && datapacksDir.listFiles() != null) {
            for (File file : datapacksDir.listFiles()) {
                if (!file.getName().startsWith(".") && (file.isDirectory() || file.getName().endsWith(".zip"))) {
                    packs.add(file.getName());

                    if (file.getName().equals(activePack)) {
                        File enchDir = new File(file, "data/stones_workspace/enchantments");
                        if (enchDir.exists() && enchDir.listFiles() != null) {
                            for (File runeFile : enchDir.listFiles()) {
                                if (runeFile.getName().endsWith(".json")) {
                                    activeFiles.add(runeFile.getName().replace(".json", ""));
                                }
                            }
                        }
                    }
                }
            }
        }
        return new S2CSyncPackList(packs, activePack, true, activeFiles);
    }

    // =========================================================================
    // 1. C2S: REQUEST PACK LIST (Client fragt Server nach Datapacks und Dateien)
    // =========================================================================
    public static class C2SRequestPackList {
        public C2SRequestPackList() {}

        public static void encode(C2SRequestPackList msg, FriendlyByteBuf buf) {}
        public static C2SRequestPackList decode(FriendlyByteBuf buf) { return new C2SRequestPackList(); }

        public static void handle(C2SRequestPackList msg, Supplier<NetworkEvent.Context> ctxGetter) {
            NetworkEvent.Context ctx = ctxGetter.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;

                // SICHERHEITS-TÜRSTEHER: Spieler ohne Admin-Rechte erhalten eine leere Liste.
                // Wichtig: 4. Argument muss übergeben werden, sonst Kompilierfehler!
                if (!player.hasPermissions(2)) {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new S2CSyncPackList(new ArrayList<>(), "", false, new ArrayList<>()));
                    return;
                }

                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), buildSyncPacket());
            });
            ctx.setPacketHandled(true);
        }
    }

    // =========================================================================
    // 2. S2C: SYNC PACK LIST (Server sendet Packs UND Dateien an Client-GUI)
    // =========================================================================
    public static class S2CSyncPackList {
        private final List<String> packNames;
        private final String activePackName;
        private final boolean authorized;
        private final List<String> activePackFiles;

        public S2CSyncPackList(List<String> packNames, String activePackName, boolean authorized, List<String> activePackFiles) {
            this.packNames = packNames;
            this.activePackName = activePackName;
            this.authorized = authorized;
            this.activePackFiles = activePackFiles;
        }

        public static void encode(S2CSyncPackList msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.authorized);
            buf.writeInt(msg.packNames.size());
            for (String name : msg.packNames) buf.writeUtf(name);
            buf.writeUtf(msg.activePackName);
            buf.writeInt(msg.activePackFiles.size());
            for (String file : msg.activePackFiles) buf.writeUtf(file);
        }

        public static S2CSyncPackList decode(FriendlyByteBuf buf) {
            boolean auth = buf.readBoolean();
            int size = buf.readInt();
            List<String> names = new ArrayList<>();
            for (int i = 0; i < size; i++) names.add(buf.readUtf());
            String active = buf.readUtf();
            int fSize = buf.readInt();
            List<String> files = new ArrayList<>();
            for (int i = 0; i < fSize; i++) files.add(buf.readUtf());
            return new S2CSyncPackList(names, active, auth, files);
        }

        public static void handle(S2CSyncPackList msg, Supplier<NetworkEvent.Context> ctxGetter) {
            NetworkEvent.Context ctx = ctxGetter.get();
            ctx.enqueueWork(() -> {
                // ARCHITEKTUR-RETTUNG: Verhindert NoClassDefFoundError auf dem Server durch DistExecutor.
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                        net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                                ClientHandler.handlePackList(msg.packNames, msg.activePackName, msg.authorized, msg.activePackFiles)
                );
            });
            ctx.setPacketHandled(true);
        }
    }

    // =========================================================================
    // Innerer Client-Helper — wird nur auf der Client-JVM geladen.
    // =========================================================================
    private static class ClientHandler {
        public static void handlePackList(List<String> packs, String active, boolean authorized, List<String> files) {
            StonesStudioScreen.receiveServerPackList(packs, active, authorized, files);
        }
        public static void handleRuneLoad(String fileName, String jsonStr) {
            if (Minecraft.getInstance().screen instanceof StonesStudioScreen sss) {
                sss.loadRuneFromJson(fileName, jsonStr);
            }
        }
    }

    // =========================================================================
    // 3. C2S: REQUEST RUNE FILE (Client fordert Inhalt einer JSON-Datei an)
    // =========================================================================
    public static class C2SRequestRuneFile {
        private final String fileName;

        public C2SRequestRuneFile(String fileName) { this.fileName = fileName; }

        public static void encode(C2SRequestRuneFile msg, FriendlyByteBuf buf) { buf.writeUtf(msg.fileName); }
        public static C2SRequestRuneFile decode(FriendlyByteBuf buf) { return new C2SRequestRuneFile(buf.readUtf()); }

        public static void handle(C2SRequestRuneFile msg, Supplier<NetworkEvent.Context> ctxGetter) {
            NetworkEvent.Context ctx = ctxGetter.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || !player.hasPermissions(2)) return;

                String activePack = StonesModConfig.ACTIVE_WORKSPACE_PACK.get();
                if (activePack.isEmpty()) return;

                String fileNameWithExt = msg.fileName.endsWith(".json") ? msg.fileName : msg.fileName + ".json";
                File file = new File(
                        FMLPaths.GAMEDIR.get().resolve("datapacks/" + activePack + "/data/stones_workspace/enchantments").toFile(),
                        fileNameWithExt);

                if (file.exists()) {
                    try {
                        String content = Files.readString(file.toPath());
                        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CSyncRuneFile(msg.fileName, content));
                    } catch (Exception e) {
                        player.sendSystemMessage(Component.literal("§c[Server] Fehler beim Lesen der Datei."));
                    }
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    // =========================================================================
    // 4. S2C: SYNC RUNE FILE (Server sendet Dateiinhalt an GUI)
    // =========================================================================
    public static class S2CSyncRuneFile {
        private final String fileName;
        private final String jsonContent;

        public S2CSyncRuneFile(String fileName, String jsonContent) {
            this.fileName = fileName;
            this.jsonContent = jsonContent;
        }

        public static void encode(S2CSyncRuneFile msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.fileName);
            buf.writeUtf(msg.jsonContent, 1048576);
        }

        public static S2CSyncRuneFile decode(FriendlyByteBuf buf) {
            return new S2CSyncRuneFile(buf.readUtf(), buf.readUtf(1048576));
        }

        public static void handle(S2CSyncRuneFile msg, Supplier<NetworkEvent.Context> ctxGetter) {
            NetworkEvent.Context ctx = ctxGetter.get();
            ctx.enqueueWork(() -> {
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                        net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                                ClientHandler.handleRuneLoad(msg.fileName, msg.jsonContent)
                );
            });
            ctx.setPacketHandled(true);
        }
    }

    // =========================================================================
    // 5. C2S: SAVE RUNE FILE (Client speichert JSON-Datei auf dem Server)
    // =========================================================================
    public static class C2SSaveRuneFile {
        private final String fileName;
        private final String jsonContent;

        public C2SSaveRuneFile(String fileName, String jsonContent) {
            this.fileName = fileName;
            this.jsonContent = jsonContent;
        }

        public static void encode(C2SSaveRuneFile msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.fileName);
            buf.writeUtf(msg.jsonContent, 1048576);
        }

        public static C2SSaveRuneFile decode(FriendlyByteBuf buf) {
            return new C2SSaveRuneFile(buf.readUtf(), buf.readUtf(1048576));
        }

        public static void handle(C2SSaveRuneFile msg, Supplier<NetworkEvent.Context> ctxGetter) {
            NetworkEvent.Context ctx = ctxGetter.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || !player.hasPermissions(2)) return;

                String activePack = StonesModConfig.ACTIVE_WORKSPACE_PACK.get();
                if (activePack.isEmpty()) return;

                try {
                    String fileNameWithExt = msg.fileName.endsWith(".json") ? msg.fileName : msg.fileName + ".json";
                    File file = new File(
                            FMLPaths.GAMEDIR.get().resolve("datapacks/" + activePack + "/data/stones_workspace/enchantments").toFile(),
                            fileNameWithExt);

                    // Pretty-Print für Lesbarkeit (keine Einzeiler!)
                    JsonElement parsed = JsonParser.parseString(msg.jsonContent);
                    Gson prettyGson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
                    Files.writeString(file.toPath(), prettyGson.toJson(parsed), java.nio.charset.StandardCharsets.UTF_8);

                    player.sendSystemMessage(Component.literal("§a[Server] '" + fileNameWithExt + "' gespeichert."));
                } catch (Exception e) {
                    player.sendSystemMessage(Component.literal("§c[Server] Fehler beim Speichern von '" + msg.fileName + "'."));
                    StonesMod.LOGGER.error("Speichern fehlgeschlagen: ", e);
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    // =========================================================================
    // 6. C2S: PROJECT ACTION (Projekt erstellen, aktivieren, deaktivieren)
    // =========================================================================
    public static class C2SProjectAction {
        private final String actionType;
        private final String projectName;

        public C2SProjectAction(String actionType, String projectName) {
            this.actionType = actionType;
            this.projectName = projectName;
        }

        public static void encode(C2SProjectAction msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.actionType);
            buf.writeUtf(msg.projectName);
        }

        public static C2SProjectAction decode(FriendlyByteBuf buf) {
            return new C2SProjectAction(buf.readUtf(), buf.readUtf());
        }

        public static void handle(C2SProjectAction msg, Supplier<NetworkEvent.Context> ctxGetter) {
            NetworkEvent.Context ctx = ctxGetter.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || !player.hasPermissions(2)) return;

                String sanitizedName = sanitizeProjectName(msg.projectName);

                if (msg.actionType.equals("CREATE") && !sanitizedName.isEmpty()) {
                    ServerDatapackExporter.createAndExportNewPack(player, sanitizedName);
                    StonesModConfig.ACTIVE_WORKSPACE_PACK.set(sanitizedName);
                    StonesModConfig.SPEC.save();
                } else if (msg.actionType.equals("ACTIVATE") && !sanitizedName.isEmpty()) {
                    StonesModConfig.ACTIVE_WORKSPACE_PACK.set(sanitizedName);
                    StonesModConfig.SPEC.save();
                    player.sendSystemMessage(Component.literal("§e[Server] " + sanitizedName + " als aktives Projekt gesetzt. Drücke 'Anwenden' zum Laden!"));
                } else if (msg.actionType.equals("DEACTIVATE")) {
                    StonesModConfig.ACTIVE_WORKSPACE_PACK.set("");
                    StonesModConfig.SPEC.save();
                    player.sendSystemMessage(Component.literal("§e[Server] Projekt deaktiviert. Drücke 'Anwenden' zum Entladen!"));
                }

                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), buildSyncPacket());
            });
            ctx.setPacketHandled(true);
        }
    }

    // =========================================================================
    // 7. C2S: TRIGGER RELOAD (Führt ein sauberes /reload auf dem Server aus)
    // =========================================================================
    public static class C2STriggerReload {
        public C2STriggerReload() {}

        public static void encode(C2STriggerReload msg, FriendlyByteBuf buf) {}
        public static C2STriggerReload decode(FriendlyByteBuf buf) { return new C2STriggerReload(); }

        public static void handle(C2STriggerReload msg, Supplier<NetworkEvent.Context> ctxGetter) {
            NetworkEvent.Context ctx = ctxGetter.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || !player.hasPermissions(2)) return;

                // Komplettes Datapack neu laden — der EnchantmentReloadListener übernimmt
                // den Sync der Runen-Daten an alle Clients automatisch via PacketSyncEnchantments.
                player.getServer().getCommands().performPrefixedCommand(
                        player.createCommandSourceStack(), "reload");

                player.sendSystemMessage(Component.literal("§a[Server] Reload erfolgreich durchgeführt!"));
            });
            ctx.setPacketHandled(true);
        }
    }
}