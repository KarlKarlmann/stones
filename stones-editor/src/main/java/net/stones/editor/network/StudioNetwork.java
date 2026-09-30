package net.stones.editor.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.ConnectionData;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.stones.editor.StonesEditorMod;
import net.stones.editor.client.gui.StonesStudioScreen;
import net.stones.editor.init.StonesEditorConfig;
import net.stones.editor.data.ServerDatapackExporter;
import net.stones.editor.data.TemplateHashHelper;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class StudioNetwork {

    // Liest die Mod-Version automatisch aus den Forge-Metadaten (mods.toml / build.gradle)
    public static final String PROTOCOL_VERSION = ModList.get()
        .getModContainerById(StonesEditorMod.MODID)
        .map(container -> container.getModInfo().getVersion().toString())
        .orElse("3");
        
    public static final ResourceLocation CHANNEL_NAME = new ResourceLocation(StonesEditorMod.MODID, "studio_channel");

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_NAME,
            () -> PROTOCOL_VERSION,
            version -> true, // ACCEPT_ALL: Kicked niemanden beim Joinen!
            version -> true  // ACCEPT_ALL: Kicked niemanden beim Joinen!
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
        
        // SCRIPT-PAKETE
        CHANNEL.registerMessage(packetId++, C2SRequestScriptFile.class, C2SRequestScriptFile::encode, C2SRequestScriptFile::decode, C2SRequestScriptFile::handle);
        CHANNEL.registerMessage(packetId++, S2CSyncScriptFile.class,    S2CSyncScriptFile::encode,    S2CSyncScriptFile::decode,    S2CSyncScriptFile::handle);
        CHANNEL.registerMessage(packetId++, C2SSaveScriptFile.class,    C2SSaveScriptFile::encode,    C2SSaveScriptFile::decode,    C2SSaveScriptFile::handle);
    }

    // =========================================================================
    // VERSION CHECK METHODEN (Aufruf beim /stonesstudio Befehl)
    // =========================================================================

    public enum Status { NOT_CONNECTED, MOD_MISSING, VERSION_MISMATCH, MATCH }
    public record VersionCheckResult(Status status, String serverVersion) {}

    public static VersionCheckResult checkServerVersion() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            return new VersionCheckResult(Status.NOT_CONNECTED, null);
        }

        Connection connection = mc.getConnection().getConnection();
        ConnectionData data = NetworkHooks.getConnectionData(connection);

        // Server hat den Kanal gar nicht registriert (Vanilla oder Mod fehlt)
        if (data == null || !data.getChannels().containsKey(CHANNEL_NAME)) {
            return new VersionCheckResult(Status.MOD_MISSING, null);
        }

        String serverVersion = data.getChannels().get(CHANNEL_NAME);
        
        // Prüft auf exakte Versionsgleichheit
        if (PROTOCOL_VERSION.equals(serverVersion)) {
            return new VersionCheckResult(Status.MATCH, serverVersion);
        } else {
            return new VersionCheckResult(Status.VERSION_MISMATCH, serverVersion);
        }
    }


    // =========================================================================
    // BESTEHENDE METHODEN 
    // =========================================================================

    public static String sanitizeProjectName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "");
    }

    private static String resolveFileName(String rawName) {
        if (rawName.endsWith(".json") || rawName.endsWith(".bak")) {
            return rawName;
        }
        return rawName + ".json";
    }

    // === HILFSMETHODEN FÜR DIRECT KUBEJS EXPORT ===

    private static void exportKubeJsScript(String rawFileName, JsonElement parsedJson, String activePack) {
        try {
            if (!parsedJson.isJsonObject()) return;
            JsonObject json = parsedJson.getAsJsonObject();

            String logicalId = rawFileName.replace(".json", "").replace(".bak", "");
            String jsCode = null;

            if (json.has("raw_script")) {
                String scriptLink = json.get("raw_script").getAsString().trim();
                if (!scriptLink.isEmpty()) {
                    String scriptFileName = scriptLink;
                    if (scriptFileName.contains("/")) scriptFileName = scriptFileName.substring(scriptFileName.lastIndexOf('/') + 1);
                    if (scriptFileName.contains("\\")) scriptFileName = scriptFileName.substring(scriptFileName.lastIndexOf('\\') + 1);
                    if (!scriptFileName.endsWith(".js")) scriptFileName += ".js";

                    File scriptFile = new File(FMLPaths.GAMEDIR.get().resolve("datapacks/" + activePack + "/data/stones_workspace/scripts").toFile(), scriptFileName);
                    if (scriptFile.exists()) {
                        jsCode = Files.readString(scriptFile.toPath(), StandardCharsets.UTF_8);
                    }
                }
            } else if (json.has("behaviors")) {
                jsCode = net.stones.transpiler.StonesTranspiler.transpile(logicalId, json);
            }

            if (jsCode != null && !jsCode.isBlank()) {
                File scriptDir = FMLPaths.GAMEDIR.get().resolve("kubejs/server_scripts/stones_generated").toFile();
                File scriptFile = new File(scriptDir, logicalId + ".js");
                if (scriptFile.getParentFile() != null) {
                    scriptFile.getParentFile().mkdirs();
                }
                Files.writeString(scriptFile.toPath(), jsCode, StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            StonesEditorMod.LOGGER.error("Fehler beim sofortigen KubeJS Export in StudioNetwork: ", e);
        }
    }

    private static void exportDirectScriptToKubeJs(String fName, String content) {
        try {
            if (content == null || content.isBlank()) return;
            String logicalId = fName.endsWith(".js") ? fName.substring(0, fName.length() - 3) : fName;
            File scriptDir = FMLPaths.GAMEDIR.get().resolve("kubejs/server_scripts/stones_generated").toFile();
            File scriptFile = new File(scriptDir, logicalId + ".js");
            if (scriptFile.getParentFile() != null) {
                scriptFile.getParentFile().mkdirs();
            }
            Files.writeString(scriptFile.toPath(), content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            StonesEditorMod.LOGGER.error("Fehler beim direkten KubeJS Export in StudioNetwork: ", e);
        }
    }

    private static S2CSyncPackList buildSyncPacket() {
        File datapacksDir = FMLPaths.GAMEDIR.get().resolve("datapacks").toFile();
        List<String> packs = new ArrayList<>();
        List<String> activeFiles = new ArrayList<>();
        List<String> activeScripts = new ArrayList<>();
        String activePack = StonesEditorConfig.ACTIVE_WORKSPACE_PACK.get();

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
                        
                        File scriptDir = new File(file, "data/stones_workspace/scripts");
                        if (scriptDir.exists() && scriptDir.listFiles() != null) {
                            for (File scriptFile : scriptDir.listFiles()) {
                                if (scriptFile.getName().endsWith(".js")) {
                                    activeScripts.add(scriptFile.getName());
                                }
                            }
                        }
                    }
                }
            }
        }
        return new S2CSyncPackList(packs, activePack, true, activeFiles, activeScripts);
    }

    // --- PACKETS ---

    public static class C2SRequestPackList {
        public C2SRequestPackList() {}
        public static void encode(C2SRequestPackList msg, FriendlyByteBuf buf) {}
        public static C2SRequestPackList decode(FriendlyByteBuf buf) { return new C2SRequestPackList(); }

        public static void handle(C2SRequestPackList msg, Supplier<NetworkEvent.Context> ctxGetter) {
            NetworkEvent.Context ctx = ctxGetter.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null) return;
                if (!player.hasPermissions(2)) {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                            new S2CSyncPackList(new ArrayList<>(), "", false, new ArrayList<>(), new ArrayList<>()));
                    return;
                }
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), buildSyncPacket());
            });
            ctx.setPacketHandled(true);
        }
    }

    public static class S2CSyncPackList {
        private final List<String> packNames;
        private final String activePackName;
        private final boolean authorized;
        private final List<String> activePackFiles;
        private final List<String> activePackScripts;

        public S2CSyncPackList(List<String> packNames, String activePackName, boolean authorized, List<String> activePackFiles, List<String> activePackScripts) {
            this.packNames = packNames;
            this.activePackName = activePackName;
            this.authorized = authorized;
            this.activePackFiles = activePackFiles;
            this.activePackScripts = activePackScripts;
        }

        public static void encode(S2CSyncPackList msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.authorized);
            buf.writeInt(msg.packNames.size());
            for (String name : msg.packNames) buf.writeUtf(name);
            buf.writeUtf(msg.activePackName);
            buf.writeInt(msg.activePackFiles.size());
            for (String file : msg.activePackFiles) buf.writeUtf(file);
            buf.writeInt(msg.activePackScripts.size());
            for (String file : msg.activePackScripts) buf.writeUtf(file);
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
            int sSize = buf.readInt();
            List<String> scripts = new ArrayList<>();
            for (int i = 0; i < sSize; i++) scripts.add(buf.readUtf());
            return new S2CSyncPackList(names, active, auth, files, scripts);
        }

        public static void handle(S2CSyncPackList msg, Supplier<NetworkEvent.Context> ctxGetter) {
            NetworkEvent.Context ctx = ctxGetter.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    ClientHandler.handlePackList(msg.packNames, msg.activePackName, msg.authorized, msg.activePackFiles, msg.activePackScripts)
            ));
            ctx.setPacketHandled(true);
        }
    }

    private static class ClientHandler {
        public static void handlePackList(List<String> packs, String active, boolean authorized, List<String> files, List<String> scripts) {
            StonesStudioScreen.receiveServerPackList(packs, active, authorized, files, scripts);
        }

        public static void handleRuneLoad(String fileName, String jsonStr, boolean hasConflict, String jarTemplateStr, String newJarHash) {
            if (Minecraft.getInstance().screen instanceof StonesStudioScreen sss) {
                sss.loadRuneFromJson(fileName, jsonStr, hasConflict, jarTemplateStr, newJarHash);
            }
        }
        
        public static void handleScriptLoad(String fileName, String content) {
            if (Minecraft.getInstance().screen instanceof StonesStudioScreen sss) {
                sss.loadScriptOnly(fileName, content);
            }
        }
    }

    // --- JSON RUNE PACKETS ---

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
                String activePack = StonesEditorConfig.ACTIVE_WORKSPACE_PACK.get();
                if (activePack.isEmpty()) return;

                String fileNameWithExt = resolveFileName(msg.fileName);
                File file = new File(FMLPaths.GAMEDIR.get().resolve("datapacks/" + activePack + "/data/stones_workspace/enchantments").toFile(), fileNameWithExt);

                if (file.exists()) {
                    try {
                        String content = Files.readString(file.toPath());
                        TemplateHashHelper.CheckResult result = TemplateHashHelper.verifyServerFile(msg.fileName, content);
                        if (result.status() == TemplateHashHelper.Status.SILENT_UPDATE) {
                            content = result.processedJson().toString();
                            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
                        }
                        boolean hasConflict = (result.status() == TemplateHashHelper.Status.MODIFIED_CONFLICT);
                        String jarTemplateStr = hasConflict ? result.jarJson().toString() : "";
                        String newJarHash = result.newJarHash() != null ? result.newJarHash() : "";
                        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CSyncRuneFile(msg.fileName, content, hasConflict, jarTemplateStr, newJarHash));
                    } catch (Exception e) {
                        player.sendSystemMessage(Component.translatable("chat.stones.studio.server.file_read_error"));
                    }
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public static class S2CSyncRuneFile {
        private final String fileName;
        private final String jsonContent;
        private final boolean hasConflict;
        private final String jarTemplateStr;
        private final String newJarHash;

        public S2CSyncRuneFile(String fileName, String jsonContent, boolean hasConflict, String jarTemplateStr, String newJarHash) {
            this.fileName = fileName; this.jsonContent = jsonContent; this.hasConflict = hasConflict; this.jarTemplateStr = jarTemplateStr; this.newJarHash = newJarHash;
        }
        public S2CSyncRuneFile(String fileName, String jsonContent) { this(fileName, jsonContent, false, "", ""); }
        public static void encode(S2CSyncRuneFile msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.fileName); buf.writeUtf(msg.jsonContent, 1048576); buf.writeBoolean(msg.hasConflict); buf.writeUtf(msg.jarTemplateStr, 1048576); buf.writeUtf(msg.newJarHash);
        }
        public static S2CSyncRuneFile decode(FriendlyByteBuf buf) {
            return new S2CSyncRuneFile(buf.readUtf(), buf.readUtf(1048576), buf.readBoolean(), buf.readUtf(1048576), buf.readUtf());
        }
        public static void handle(S2CSyncRuneFile msg, Supplier<NetworkEvent.Context> ctxGetter) {
            NetworkEvent.Context ctx = ctxGetter.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handleRuneLoad(msg.fileName, msg.jsonContent, msg.hasConflict, msg.jarTemplateStr, msg.newJarHash)));
            ctx.setPacketHandled(true);
        }
    }

    public static class C2SSaveRuneFile {
        private final String fileName;
        private final String jsonContent;
        public C2SSaveRuneFile(String fileName, String jsonContent) { this.fileName = fileName; this.jsonContent = jsonContent; }
        public static void encode(C2SSaveRuneFile msg, FriendlyByteBuf buf) { buf.writeUtf(msg.fileName); buf.writeUtf(msg.jsonContent, 1048576); }
        public static C2SSaveRuneFile decode(FriendlyByteBuf buf) { return new C2SSaveRuneFile(buf.readUtf(), buf.readUtf(1048576)); }
        public static void handle(C2SSaveRuneFile msg, Supplier<NetworkEvent.Context> ctxGetter) {
            NetworkEvent.Context ctx = ctxGetter.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || !player.hasPermissions(2)) return;
                String activePack = StonesEditorConfig.ACTIVE_WORKSPACE_PACK.get();
                if (activePack.isEmpty()) return;

                try {
                    String fileNameWithExt = resolveFileName(msg.fileName);
                    File file = new File(FMLPaths.GAMEDIR.get().resolve("datapacks/" + activePack + "/data/stones_workspace/enchantments").toFile(), fileNameWithExt);
                    JsonElement parsed = JsonParser.parseString(msg.jsonContent);
                    TemplateHashHelper.ensureHashExists(parsed, msg.fileName);
                    Gson prettyGson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
                    file.getParentFile().mkdirs();
                    Files.writeString(file.toPath(), prettyGson.toJson(parsed), StandardCharsets.UTF_8);

                    // KubeJS Skript sofort auf Server-Ebene aktualisieren
                    exportKubeJsScript(msg.fileName, parsed, activePack);

                    player.sendSystemMessage(Component.translatable("chat.stones.studio.server.file_saved", fileNameWithExt));
                } catch (Exception e) {
                    player.sendSystemMessage(Component.translatable("chat.stones.studio.server.file_save_error", msg.fileName));
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    // --- JS SCRIPT PACKETS ---

    public static class C2SRequestScriptFile {
        private final String fileName;
        public C2SRequestScriptFile(String fileName) { this.fileName = fileName; }
        public static void encode(C2SRequestScriptFile msg, FriendlyByteBuf buf) { buf.writeUtf(msg.fileName); }
        public static C2SRequestScriptFile decode(FriendlyByteBuf buf) { return new C2SRequestScriptFile(buf.readUtf()); }

        public static void handle(C2SRequestScriptFile msg, Supplier<NetworkEvent.Context> ctxGetter) {
            NetworkEvent.Context ctx = ctxGetter.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || !player.hasPermissions(2)) return;
                String activePack = StonesEditorConfig.ACTIVE_WORKSPACE_PACK.get();
                if (activePack.isEmpty()) return;

                String fName = msg.fileName.endsWith(".js") ? msg.fileName : msg.fileName + ".js";
                File file = new File(FMLPaths.GAMEDIR.get().resolve("datapacks/" + activePack + "/data/stones_workspace/scripts").toFile(), fName);

                if (file.exists()) {
                    try {
                        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new S2CSyncScriptFile(fName, content));
                    } catch (Exception e) {}
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public static class S2CSyncScriptFile {
        private final String fileName;
        private final String content;
        public S2CSyncScriptFile(String fileName, String content) { this.fileName = fileName; this.content = content; }
        public static void encode(S2CSyncScriptFile msg, FriendlyByteBuf buf) { buf.writeUtf(msg.fileName); buf.writeUtf(msg.content, 1048576); }
        public static S2CSyncScriptFile decode(FriendlyByteBuf buf) { return new S2CSyncScriptFile(buf.readUtf(), buf.readUtf(1048576)); }
        public static void handle(S2CSyncScriptFile msg, Supplier<NetworkEvent.Context> ctxGetter) {
            NetworkEvent.Context ctx = ctxGetter.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handleScriptLoad(msg.fileName, msg.content)));
            ctx.setPacketHandled(true);
        }
    }

    public static class C2SSaveScriptFile {
        private final String fileName;
        private final String content;
        public C2SSaveScriptFile(String fileName, String content) { this.fileName = fileName; this.content = content; }
        public static void encode(C2SSaveScriptFile msg, FriendlyByteBuf buf) { buf.writeUtf(msg.fileName); buf.writeUtf(msg.content, 1048576); }
        public static C2SSaveScriptFile decode(FriendlyByteBuf buf) { return new C2SSaveScriptFile(buf.readUtf(), buf.readUtf(1048576)); }
        public static void handle(C2SSaveScriptFile msg, Supplier<NetworkEvent.Context> ctxGetter) {
            NetworkEvent.Context ctx = ctxGetter.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || !player.hasPermissions(2)) return;
                String activePack = StonesEditorConfig.ACTIVE_WORKSPACE_PACK.get();
                if (activePack.isEmpty()) return;

                try {
                    String fName = msg.fileName.endsWith(".js") ? msg.fileName : msg.fileName + ".js";
                    File file = new File(FMLPaths.GAMEDIR.get().resolve("datapacks/" + activePack + "/data/stones_workspace/scripts").toFile(), fName);
                    file.getParentFile().mkdirs();
                    Files.writeString(file.toPath(), msg.content, StandardCharsets.UTF_8);

                    // KubeJS Skript sofort auf Server-Ebene aktualisieren
                    exportDirectScriptToKubeJs(fName, msg.content);

                    player.sendSystemMessage(Component.translatable("chat.stones.studio.server.file_saved", fName));
                } catch (Exception e) {}
            });
            ctx.setPacketHandled(true);
        }
    }

    // --- SYSTEM PACKETS ---

    public static class C2SProjectAction {
        private final String actionType;
        private final String projectName;
        public C2SProjectAction(String actionType, String projectName) { this.actionType = actionType; this.projectName = projectName; }
        public static void encode(C2SProjectAction msg, FriendlyByteBuf buf) { buf.writeUtf(msg.actionType); buf.writeUtf(msg.projectName); }
        public static C2SProjectAction decode(FriendlyByteBuf buf) { return new C2SProjectAction(buf.readUtf(), buf.readUtf()); }
        public static void handle(C2SProjectAction msg, Supplier<NetworkEvent.Context> ctxGetter) {
            NetworkEvent.Context ctx = ctxGetter.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || !player.hasPermissions(2)) return;
                String sanitizedName = sanitizeProjectName(msg.projectName);
                if (msg.actionType.equals("CREATE") && !sanitizedName.isEmpty()) {
                    ServerDatapackExporter.createAndExportNewPack(player, sanitizedName);
                    StonesEditorConfig.ACTIVE_WORKSPACE_PACK.set(sanitizedName);
                    StonesEditorConfig.SPEC.save();
                } else if (msg.actionType.equals("ACTIVATE") && !sanitizedName.isEmpty()) {
                    StonesEditorConfig.ACTIVE_WORKSPACE_PACK.set(sanitizedName);
                    StonesEditorConfig.SPEC.save();
                    player.sendSystemMessage(Component.translatable("chat.stones.studio.server.project_activated", sanitizedName));
                } else if (msg.actionType.equals("DEACTIVATE")) {
                    StonesEditorConfig.ACTIVE_WORKSPACE_PACK.set("");
                    StonesEditorConfig.SPEC.save();
                    player.sendSystemMessage(Component.translatable("chat.stones.studio.server.project_deactivated"));
                }
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), buildSyncPacket());
            });
            ctx.setPacketHandled(true);
        }
    }

    public static class C2STriggerReload {
        public C2STriggerReload() {}
        public static void encode(C2STriggerReload msg, FriendlyByteBuf buf) {}
        public static C2STriggerReload decode(FriendlyByteBuf buf) { return new C2STriggerReload(); }
        public static void handle(C2STriggerReload msg, Supplier<NetworkEvent.Context> ctxGetter) {
            NetworkEvent.Context ctx = ctxGetter.get();
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || !player.hasPermissions(2)) return;
                player.getServer().getCommands().performPrefixedCommand(
                        player.getServer().createCommandSourceStack().withSuppressedOutput().withPermission(4), "reload");
                player.sendSystemMessage(Component.translatable("chat.stones.studio.server.reload_success"));
            });
            ctx.setPacketHandled(true);
        }
    }
}