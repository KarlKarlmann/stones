package net.stones.editor.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.storage.LevelResource;
import net.stones.editor.network.StudioNetwork;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.stones.editor.client.gui.TreeNode;
import net.stones.editor.client.gui.StudioSerializer;
import net.stones.editor.client.gui.section.StudioProjectDialog;
import net.stones.editor.client.gui.section.StudioMenuBar;
import net.stones.editor.client.gui.section.StudioContextMenu;
import net.stones.editor.client.gui.section.SidePanelRenderer;
import net.stones.editor.client.gui.section.RuneStatsSection;
import net.stones.editor.client.gui.section.RunePropertiesSection;
import net.stones.editor.client.gui.section.BehaviorTreeRenderer;
import net.stones.editor.client.gui.widget.StudioSuggestTextField;
import net.stones.editor.client.gui.widget.StudioMultiLineEditBox;
import net.stones.editor.client.gui.modal.StatEditModal;
import net.stones.editor.client.gui.modal.ActionEditModal;
import net.stones.editor.client.gui.modal.AbstractStudioModal;
import net.stones.editor.client.gui.modal.TemplateUpdateModal;
import net.stones.editor.client.gui.modal.FxEditModal;
import net.stones.editor.client.gui.modal.BeamEditModal;

/**
 * ARCHITEKTUR: STONES STUDIO ORCHESTRATOR
 * Das primäre Rendering- und Input-Handling-Fenster für das Stones Studio.
 */
public class StonesStudioScreen extends Screen {

    public static final int LEFT_PANEL_WIDTH = 180;
	public static final List<String> activePackScripts = new ArrayList<>();
    private boolean leftPanelOpen = true;

    // --- Accordion-Zustände auf dem Bildschirm ---
    public static boolean isPropertiesExpanded = true;
    public static boolean isStatsExpanded = true;

    // --- Raw JS Editor Komponenten ---
	private Button btnToggleScriptMode;
	public net.stones.editor.client.gui.widget.StudioTextField fldRawScriptLink;
	public net.stones.editor.client.gui.widget.StudioMultiLineEditBox fldRawScriptContent; 
	
    // --- Globaler Scroll-Offset für den gesamten rechten Workspace-Screen ---
    private double mainScrollY = 0;

    // --- Deferred Tooltip Render Queue ---
    private List<FormattedCharSequence> deferredTooltip = null;
    private int deferredTooltipX = 0;
    private int deferredTooltipY = 0;

    // --- Snapshot-Speicher für ungespeicherte Änderungen ---
    private String lastSavedJsonString = "";
    private String lastSavedScriptContent = "";

    // --- Zentraler Handshake-Status für diese Screen-Instanz ---
    public static StudioNetwork.VersionCheckResult connectionStatus = new StudioNetwork.VersionCheckResult(StudioNetwork.Status.MATCH, "");

    // --- Globale Statics (vom Model / Serializer genutzt) ---
    public static final List<PackInfo> discoveredPacks = new ArrayList<>();
    public static final List<String> activePackFiles = new ArrayList<>();
    public static int activePackIndex = 0;
    public static String serverActivePackName = "";
    public static boolean isWaitingForServer = true;
    public static boolean isAuthorized = true;
    public static String currentFileName = "";
    public static JsonObject currentRuneJson = new JsonObject();

    public static final List<TreeNode> activeTree = new ArrayList<>();
    public static final List<JsonObject> activeStats = new ArrayList<>();

    private boolean hasLocalWorldPacks = false;

    // --- Sub-Komponenten & Sektionen ---
    private final StudioMenuBar menuBar = new StudioMenuBar(this);
    private final StudioContextMenu contextMenu = new StudioContextMenu(this);
    private final StudioProjectDialog projectDialog = new StudioProjectDialog(this);
    private final SidePanelRenderer sidePanel = new SidePanelRenderer(this);
    private final BehaviorTreeRenderer logicTree = new BehaviorTreeRenderer(this);

    public final RunePropertiesSection propertiesSection = new RunePropertiesSection(this);
    private final RuneStatsSection statsSection = new RuneStatsSection(this);

    private AbstractStudioModal activeModal = null;
    private StatEditModal activeStatModal = null;

    // Statischer Verweis auf die aktive Instanz des Screens
    public static StonesStudioScreen currentInstance = null;

    // Zeitstempel zur Messung eines Verbindungs-Timeouts auf den Server
    public static long waitStartTime = 0;

    public record PackInfo(String name, boolean isZip) {}

    public StonesStudioScreen() {
        super(Component.translatable("gui.stones.studio.title"));
        
        // EIN SCHALTER: Handshake prüfen BEVOR irgendwas initialisiert wird
        connectionStatus = StudioNetwork.checkServerVersion();
        if (connectionStatus.status() != StudioNetwork.Status.MATCH) {
            isWaitingForServer = false;
            return; // Totalsperre: Kein C2SRequestPackList, kein Netzwerkverkehr!
        }

        isWaitingForServer = true;
        waitStartTime = System.currentTimeMillis();
        discoveredPacks.clear();
        StudioNetwork.sendToServer(new StudioNetwork.C2SRequestPackList());
        checkLocalWorldPacks();
    }

	public boolean isRawJsMode() {
		return currentRuneJson != null && currentRuneJson.has("raw_script") 
			&& !currentRuneJson.get("raw_script").getAsString().isEmpty();
	}

    public Font getFont() { 
        return this.font; 
    }

    public boolean isLeftPanelOpen() {
        return this.leftPanelOpen;
    }

    public <T extends Renderable & GuiEventListener & NarratableEntry> T addWidget(T widget) {
        return this.addRenderableWidget(widget);
    }

    public void queueTooltip(Component text, int x, int y) {
        this.deferredTooltip = this.font.split(text, 200);
        this.deferredTooltipX = x;
        this.deferredTooltipY = y;
    }

	public static void receiveServerPackList(List<String> serverPacks, String activePackName, boolean authorized, List<String> files, List<String> scripts) {
		isAuthorized = authorized;
		discoveredPacks.clear();
		serverActivePackName = activePackName;

		for (String name : serverPacks) {
			discoveredPacks.add(new PackInfo(name, name.endsWith(".zip")));
		}

		activePackIndex = 0;
		for (int i = 0; i < discoveredPacks.size(); i++) {
			if (discoveredPacks.get(i).name().equals(activePackName)) {
				activePackIndex = i;
				break;
			}
		}

		activePackFiles.clear();
		activePackFiles.addAll(files);
		
		activePackScripts.clear();
		activePackScripts.addAll(scripts);
		
		isWaitingForServer = false;
		waitStartTime = 0;

		currentFileName = "";
		currentRuneJson = new JsonObject();
		activeTree.clear();
		activeStats.clear();

		if (authorized && discoveredPacks.isEmpty()) {
			if (currentInstance != null) {
				currentInstance.openNewProjectDialog();
			}
		}
	}

	public void loadScriptOnly(String fileName, String content) {
		currentFileName = fileName;
		isWaitingForServer = false;
		waitStartTime = 0;
		logicTree.resetScroll();
		statsSection.resetScroll();
		mainScrollY = 0;

		// Simuliere einen Raw JS "Node" Zustand für isolierte Skripte
		currentRuneJson = new JsonObject();
		currentRuneJson.addProperty("raw_script", "data/stones_workspace/scripts/" + fileName);

		activeTree.clear();
		activeStats.clear();

		if (fldRawScriptLink != null) fldRawScriptLink.setValue("data/stones_workspace/scripts/" + fileName);
		if (fldRawScriptContent != null) fldRawScriptContent.setValue(content);
		
		this.lastSavedJsonString = serializeActiveTree().toString();
		this.lastSavedScriptContent = content != null ? content : "";
		updateHeaderVisibility();
	}

	/**
	 * Empfängt den Inhalt eines Skripts vom Server.
	 * Prüft, ob aktuell eine Rune geöffnet ist, die dieses Skript referenziert,
	 * oder ob es sich um eine isolierte Skript-Datei handelt.
	 */
	public void receiveScriptContent(String fileName, String content) {
		isWaitingForServer = false;
		waitStartTime = 0;

		// Fall A: Wir haben eine Rune geladen, die dieses Skript referenziert -> Nur Textfeld befüllen!
		if (!currentFileName.isEmpty() && !currentFileName.endsWith(".js") && isRawJsMode()) {
			if (fldRawScriptContent != null) {
				fldRawScriptContent.setValue(content != null ? content : "");
			}
			this.lastSavedJsonString = serializeActiveTree().toString();
			this.lastSavedScriptContent = content != null ? content : "";
			return;
		}

		// Fall B: Es wurde tatsächlich eine reine .js Datei aus der Seitenleiste geöffnet
		loadScriptOnly(fileName, content != null ? content : "");
	}

    private void checkLocalWorldPacks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSingleplayerServer() != null) {
            File localDatapacksDir = mc.getSingleplayerServer().getWorldPath(LevelResource.DATAPACK_DIR).toFile();
            if (localDatapacksDir.exists() && localDatapacksDir.listFiles() != null) {
                for (File file : localDatapacksDir.listFiles()) {
                    String name = file.getName();
                    if (name.contains("stone") || name.contains("rune")) {
                        this.hasLocalWorldPacks = true;
                        break;
                    }
                }
            }
        }
    }

    public int getPropHeaderY() { return StudioMenuBar.HEIGHT + 30; }
    public int getPropContentY() { return getPropHeaderY() + 15; }
    public int getStatsHeaderY() { return getPropContentY() + (isPropertiesExpanded ? 115 : 0) + 5; }
    public int getStatsContentY() { return getStatsHeaderY() + 15; }
    public int getTreeStartY() {
        if (currentFileName.isEmpty()) return StudioMenuBar.HEIGHT + 30;
        int statsHeight = isStatsExpanded ? (StonesStudioScreen.activeStats.size() * RuneStatsSection.ROW_HEIGHT + 15) : 0;
        return getStatsContentY() + statsHeight + 10;
    }

    public int getLogicTreeHeight() {
        return getTreeHeight(activeTree);
    }

    private int getTreeHeight(List<TreeNode> nodes) {
        int h = 0;
        for (TreeNode node : nodes) {
            h += 16;
            if (node.isExpanded) {
                h += getTreeHeight(node.children);
            }
        }
        return h;
    }

    public void setLastSavedJson(String json) {
        this.lastSavedJsonString = json;
    }

    public void setLastSavedScript(String content) {
        this.lastSavedScriptContent = content;
    }

    public void requestActionWithUnsavedWarning(Runnable action) {
        if (!currentFileName.isEmpty()) {
            if (currentFileName.endsWith(".js")) {
                String currentScript = fldRawScriptContent != null ? fldRawScriptContent.getValue() : "";
                if (!currentScript.equals(lastSavedScriptContent)) {
                    this.activeModal = new UnsavedChangesModal(this, action);
                    return;
                }
            } else {
                String currentJson = serializeActiveTree().toString();
                String currentScript = (isRawJsMode() && fldRawScriptContent != null) ? fldRawScriptContent.getValue() : "";
                if (!currentJson.equals(lastSavedJsonString) || (isRawJsMode() && !currentScript.equals(lastSavedScriptContent))) {
                    this.activeModal = new UnsavedChangesModal(this, action);
                    return;
                }
            }
        }
        action.run();
    }

	public void loadRuneFromJson(String fileName, String jsonStr, boolean hasConflict, String jarTemplateStr, String newJarHash) {
		currentFileName = fileName;
		isWaitingForServer = false;
		waitStartTime = 0;
		logicTree.resetScroll();
		statsSection.resetScroll();
		mainScrollY = 0;

		try {
			JsonObject loadedJson = JsonParser.parseString(jsonStr).getAsJsonObject();
			currentRuneJson = loadedJson;
			this.propertiesSection.loadFrom(currentRuneJson);

			activeStats.clear();
			if (currentRuneJson.has("stats")) {
				for (JsonElement sEl : currentRuneJson.getAsJsonArray("stats")) {
					activeStats.add(sEl.getAsJsonObject());
				}
			}

			activeTree.clear();
			if (currentRuneJson.has("behaviors")) {
				StudioSerializer.loadBehaviors(currentRuneJson.getAsJsonArray("behaviors"), activeTree);
			}

			// Link im Textfeld anzeigen
			if (fldRawScriptLink != null && currentRuneJson.has("raw_script")) {
				String scriptPath = currentRuneJson.get("raw_script").getAsString();
				fldRawScriptLink.setValue(scriptPath);
				
				// Fordere den Inhalt des Skripts an / lade es aus der Workspace-Struktur
				requestScriptContent(scriptPath);
			} else {
				if (fldRawScriptLink != null) fldRawScriptLink.setValue("");
				if (fldRawScriptContent != null) fldRawScriptContent.setValue("");
			}

			this.lastSavedJsonString = serializeActiveTree().toString();
			this.lastSavedScriptContent = "";

			if (hasConflict && jarTemplateStr != null && !jarTemplateStr.isEmpty()) {
				JsonObject jarTemplate = JsonParser.parseString(jarTemplateStr).getAsJsonObject();
				this.activeModal = new TemplateUpdateModal(this, fileName, loadedJson, jarTemplate, newJarHash, () -> {});
			}

		} catch (Exception e) {
			Minecraft.getInstance().player.sendSystemMessage(Component.translatable("gui.stones.studio.stonesstudio.text_01" + fileName));
		}

		updateHeaderVisibility();
	}

	public JsonObject serializeActiveTree() {
		StudioContextMenu.prepareTreeForSaving(activeTree);

		JsonObject root = currentRuneJson.deepCopy();
		propertiesSection.saveTo(root);

		JsonArray statsArray = new JsonArray();
		for (JsonObject s : activeStats) {
			statsArray.add(s.deepCopy());
		}
		root.add("stats", statsArray);

		if (isRawJsMode()) {
			String scriptLink = fldRawScriptLink != null ? fldRawScriptLink.getValue().trim() : "";
			root.addProperty("raw_script", scriptLink);
			root.remove("behaviors");
			// WICHTIG: Keine Netzwerk-Pakete oder I/O im Serializer ausführen!
		} else {
			root.remove("raw_script");
			JsonArray behaviorsArray = StudioSerializer.serializeBehaviors(activeTree);
			root.add("behaviors", behaviorsArray);
		}

		currentRuneJson = root;
		return root;
	}
	
	public void requestScriptContent(String scriptPath) {
		if (scriptPath == null || scriptPath.trim().isEmpty()) return;

		String fileName = scriptPath;
		if (fileName.contains("/")) {
			fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
		} else if (fileName.contains("\\")) {
			fileName = fileName.substring(fileName.lastIndexOf('\\') + 1);
		}

		if (!fileName.isEmpty()) {
			StudioNetwork.sendToServer(new StudioNetwork.C2SRequestScriptFile(fileName));
		}
	}

	public void saveScriptContent(String scriptPath, String content) {
		if (scriptPath == null || scriptPath.trim().isEmpty()) return;

		String fileName = scriptPath;
		if (fileName.contains("/")) {
			fileName = fileName.substring(fileName.lastIndexOf('/') + 1);
		} else if (fileName.contains("\\")) {
			fileName = fileName.substring(fileName.lastIndexOf('\\') + 1);
		}

		if (!fileName.isEmpty()) {
			StudioNetwork.sendToServer(new StudioNetwork.C2SSaveScriptFile(fileName, content));
		}
	}
	
	@Override
	protected void init() {
		// FALLS DER HANDSHAKE FEHLSCHLUG: Nur den Schließen-Button anbieten
		if (connectionStatus.status() != StudioNetwork.Status.MATCH) {
			addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> this.closeScreenDirectly())
					.bounds(width / 2 - 50, height / 2 + 55, 100, 20).build());
			return;
		}

		super.init();

		int currentLeftWidth = leftPanelOpen ? LEFT_PANEL_WIDTH : 0;
		int headerX = currentLeftWidth + 20;
		int yStart = StudioMenuBar.HEIGHT + 45;

		this.propertiesSection.init(headerX, yStart);

		if (currentRuneJson != null && !currentFileName.isEmpty()) {
			this.propertiesSection.loadFrom(currentRuneJson);
		}

		// RAW JS SCHALTER INITIALISIERUNG
		String initialLabel = isRawJsMode() ? "📜 Raw JS" : "🌲 Tree";
		btnToggleScriptMode = addRenderableWidget(new Button(
				headerX + 180, getTreeStartY(), 75, 12, 
				Component.literal(initialLabel), 
				btn -> toggleScriptMode(), 
				supplier -> supplier.get()
		) {
			@Override
			public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
				int bgColor = isHovered() ? 0xFF4A4A4A : 0xFF2A2A2A;
				int borderColor = isHovered() ? 0xFF888888 : 0xFF555555;

				graphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor);
				graphics.renderOutline(getX(), getY(), width, height, borderColor);

				int textX = getX() + (width - font.width(getMessage())) / 2;
				int textY = getY() + (height - 8) / 2;
				graphics.drawString(font, getMessage(), textX, textY, 0xFFFFFFFF, false);
			}
		});

		// 1. Link / Pfad-Eingabefeld
		fldRawScriptLink = addRenderableWidget(new net.stones.editor.client.gui.widget.StudioTextField(
			this, font, headerX + 80, getTreeStartY() + 20, width - headerX - 100, 14,
			Component.literal("Skript Link"), Component.literal("z. B. data/stones_workspace/scripts/meine_rune.js")
		));

		// 2. Editor für den Inhalt des Skripts
		fldRawScriptContent = addRenderableWidget(new net.stones.editor.client.gui.widget.StudioMultiLineEditBox(
			this, font, headerX, getTreeStartY() + 40, width - headerX - 20, 180,
			Component.literal("Raw JS Code"), Component.literal("// Schreibe hier deinen rohen JavaScript-Code...")
		));

		// Werte laden falls vorhanden
		if (currentRuneJson != null && currentRuneJson.has("raw_script")) {
			fldRawScriptLink.setValue(currentRuneJson.get("raw_script").getAsString());
		}

		addRenderableWidget(Button.builder(Component.translatable("gui.stones.studio.stonesstudio.text_02"), btn -> toggleLeftPanel())
				.bounds(5, StudioMenuBar.HEIGHT + 5, 20, 20).build());

		this.projectDialog.initDialog();
		updateHeaderVisibility();
	}

	private void toggleScriptMode() {
		if (isRawJsMode()) {
			// Wechsel zurück zum visuellen Baum
			this.activeModal = new ScriptModeWarningModal(this, false, () -> {
				currentRuneJson.remove("raw_script");
				if (fldRawScriptLink != null) fldRawScriptLink.setValue("");
				if (fldRawScriptContent != null) fldRawScriptContent.setValue("");
				btnToggleScriptMode.setMessage(Component.literal("🌲 Tree Visual"));
				updateHeaderVisibility();
			});
		} else {
			// Wechsel zu Raw JS
			this.activeModal = new ScriptModeWarningModal(this, true, () -> {
				this.serializeActiveTree();

				// 1. Transpiliere den bestehenden Baum als JS-Code
				String generatedJs = net.stones.transpiler.StonesTranspiler.transpile(currentFileName, currentRuneJson);

				// 2. Erzeuge den relativen Standard-Link im Workspace
				String baseName = currentFileName.contains(".") 
					? currentFileName.substring(0, currentFileName.lastIndexOf('.')) 
					: currentFileName;
				String defaultScriptLink = "data/stones_workspace/scripts/" + baseName + ".js";

				currentRuneJson.addProperty("raw_script", defaultScriptLink);
				
				if (fldRawScriptLink != null) fldRawScriptLink.setValue(defaultScriptLink);
				if (fldRawScriptContent != null) fldRawScriptContent.setValue(generatedJs);

				btnToggleScriptMode.setMessage(Component.literal("📜 Raw JS"));
				updateHeaderVisibility();
			});
		}
	}

    @Override
    public void removed() {
        super.removed();
        currentInstance = null;
    }

    private void toggleLeftPanel() {
        this.leftPanelOpen = !this.leftPanelOpen;
        updateHeaderVisibility();
    }

	public void updateHeaderVisibility() {
		int currentLeftWidth = leftPanelOpen ? LEFT_PANEL_WIDTH : 0;
		int headerX = currentLeftWidth + 20;
		int propContentY = getPropContentY() - (int)mainScrollY;

		this.propertiesSection.updateVisibility(headerX, propContentY, height, !currentFileName.isEmpty());

		boolean isFileLoaded = !currentFileName.isEmpty();
		int treeY = getTreeStartY() - (int)mainScrollY;

		if (btnToggleScriptMode != null) {
			btnToggleScriptMode.visible = isFileLoaded;
			btnToggleScriptMode.setX(headerX + 180);
			btnToggleScriptMode.setY(treeY);
		} 

		boolean rawActive = isFileLoaded && isRawJsMode();

		if (fldRawScriptLink != null) {
			fldRawScriptLink.visible = rawActive;
			if (rawActive) {
				fldRawScriptLink.setX(headerX + 80);
				fldRawScriptLink.setY(treeY + 18);
				fldRawScriptLink.setWidth(width - headerX - 100);
			}
		}

		if (fldRawScriptContent != null) {
			fldRawScriptContent.visible = rawActive;
			if (rawActive) {
				fldRawScriptContent.setX(headerX);
				fldRawScriptContent.setY(treeY + 38);
				fldRawScriptContent.setWidth(width - headerX - 20);
			}
		}
	}

    public boolean isBackgroundActive() {
        return activeModal == null && activeStatModal == null && !projectDialog.isOpen() && !contextMenu.isOpen && (!propertiesSection.isEditingIcon || propertiesSection.getIconModal() == null);
    }

    public void openContextMenu(TreeNode node, int mouseX, int mouseY) {
        this.contextMenu.open(node, mouseX, mouseY);
    }

    public void openStatModal(JsonObject stat, boolean isNew) {
        this.activeStatModal = new StatEditModal(this, stat, isNew);
    }

    public void closeModal() {
        this.activeModal = null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        // FEHLER-FEEDBACK: Wenn der Server den Editor nicht hat oder die Version nicht stimmt
        if (connectionStatus.status() != StudioNetwork.Status.MATCH) {
            int centerX = width / 2;
            int centerY = height / 2;

            if (connectionStatus.status() == StudioNetwork.Status.MOD_MISSING) {
                graphics.drawCenteredString(this.font, "§cDer Stones Editor ist auf diesem Server nicht installiert.", centerX, centerY - 25, 0xFFFF5555);
                graphics.drawCenteredString(this.font, "§7Netzwerkverkehr & Bearbeitung wurden vollständig deaktiviert.", centerX, centerY - 5, 0xFFAAAAAA);
                graphics.drawCenteredString(this.font, "§8Bitte wenden Sie sich an Ihren Serveradministrator.", centerX, centerY + 15, 0xFF888888);
            } else if (connectionStatus.status() == StudioNetwork.Status.VERSION_MISMATCH) {
                graphics.drawCenteredString(this.font, "§cFalsche Editorversion auf dem Server!", centerX, centerY - 30, 0xFFFF5555);
                String srvVer = connectionStatus.serverVersion() != null ? connectionStatus.serverVersion() : "Unbekannt";
                graphics.drawCenteredString(this.font, "§7Server-Version: §e" + srvVer + " §7| Deine Version: §a" + StudioNetwork.PROTOCOL_VERSION, centerX, centerY - 10, 0xFFFFAA00);
                graphics.drawCenteredString(this.font, "§7Netzwerkverkehr wurde zum Schutz vor Datenfehlern blockiert.", centerX, centerY + 10, 0xFFAAAAAA);
                graphics.drawCenteredString(this.font, "§8Bitte wenden Sie sich an Ihren Serveradministrator.", centerX, centerY + 28, 0xFF888888);
            }

            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        if (isWaitingForServer) {
            if (waitStartTime == 0) {
                waitStartTime = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - waitStartTime > 5000) {
                isWaitingForServer = false;
                waitStartTime = 0;
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_03"), false
                    );
                }
            }

            graphics.drawCenteredString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_04").getString(), width / 2, height / 2 - 10, 0xFFFFAA00);
            graphics.drawCenteredString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_05").getString(), width / 2, height / 2 + 10, 0xFF888888);
            return;
        } else {
            waitStartTime = 0;
        }

        if (!isAuthorized) {
            graphics.drawCenteredString(this.font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_06").getString(), this.width / 2, this.height / 2 - 15, 0xFFFF5555);
            graphics.drawCenteredString(this.font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_07").getString(), this.width / 2, this.height / 2 + 5, 0xFFAAAAAA);
            graphics.drawCenteredString(this.font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_08").getString(), this.width / 2, this.height / 2 + 25, 0xFF888888);
            return;
        }

        boolean bgActive = isBackgroundActive();
        int bgMouseX = bgActive ? mouseX : -999;
        int bgMouseY = bgActive ? mouseY : -999;

        int currentLeftWidth = leftPanelOpen ? LEFT_PANEL_WIDTH : 0;

        if (leftPanelOpen) {
            sidePanel.render(graphics, bgMouseX, bgMouseY);
        }

        graphics.fill(currentLeftWidth + 1, StudioMenuBar.HEIGHT, width, height, 0xFF09090B); 

        int editorX = currentLeftWidth + 20;

        if (currentFileName.isEmpty()) {
            graphics.drawCenteredString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_09").getString(), editorX + (width - editorX)/2, height / 2, 0xFF888888);
        } else {
            graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_10").getString() + currentFileName, editorX, StudioMenuBar.HEIGHT + 10, 0xFFE4E595);
            graphics.fill(editorX, StudioMenuBar.HEIGHT + 22, width - 20, StudioMenuBar.HEIGHT + 23, 0xFF333333); 

            graphics.enableScissor(editorX, StudioMenuBar.HEIGHT + 25, width - 5, height - 5);
            graphics.pose().pushPose();
            graphics.pose().translate(0, -mainScrollY, 0);

            int bgScrolledMouseY = bgMouseY == -999 ? -999 : bgMouseY + (int)mainScrollY;

            propertiesSection.render(graphics, editorX, bgMouseX, bgScrolledMouseY);
            statsSection.render(graphics, editorX, bgMouseX, bgScrolledMouseY);

            int treeStartY = getTreeStartY();
            
            // RENDERING WEICHE (RAW JS vs. VISUELLER BAUM)
			if (isRawJsMode()) {
				graphics.drawString(font, "📜 Script Link:", editorX, treeStartY + 22, 0xFFFFAA00);
			} else {
				logicTree.render(graphics, editorX, treeStartY, bgMouseX, bgScrolledMouseY);
			}

            graphics.pose().popPose();
            graphics.disableScissor();
        }

        if (this.hasLocalWorldPacks) {
            int warnWidth = 400;
            int warnX = (this.width / 2) - (warnWidth / 2);
            int warnY = this.height - 25;
            graphics.fill(warnX, warnY, warnX + warnWidth, warnY + 20, 0xBB220000); 
            graphics.renderOutline(warnX, warnY, warnWidth, 20, 0xFFFFAA00); 
            graphics.drawCenteredString(this.font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_11").getString(), this.width / 2, warnY + 6, 0xFFFFAA00);
        }

        super.render(graphics, bgMouseX, bgMouseY, partialTick);

        contextMenu.render(graphics, mouseX, mouseY);

        menuBar.render(graphics, bgMouseX, bgMouseY);
        menuBar.renderDropdowns(graphics, mouseX, mouseY);

        if (activeModal != null) activeModal.render(graphics, mouseX, mouseY, partialTick);
        if (activeStatModal != null) activeStatModal.render(graphics, mouseX, mouseY, partialTick);
        if (projectDialog.isOpen()) projectDialog.render(graphics, mouseX, mouseY, partialTick);

        if (propertiesSection.isEditingIcon && propertiesSection.getIconModal() != null) {
            propertiesSection.getIconModal().render(graphics, mouseX, mouseY, partialTick);
        }

        if (deferredTooltip != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 600);
            graphics.renderTooltip(font, deferredTooltip, deferredTooltipX, deferredTooltipY);
            graphics.pose().popPose();
            deferredTooltip = null; 
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isAuthorized) return super.mouseClicked(mouseX, mouseY, button);

        if (projectDialog.isOpen()) return projectDialog.mouseClicked(mouseX, mouseY, button);
        if (activeModal != null) return activeModal.mouseClicked(mouseX, mouseY, button);
        if (activeStatModal != null) return activeStatModal.mouseClicked(mouseX, mouseY, button);

        if (propertiesSection.isEditingIcon && propertiesSection.getIconModal() != null) {
            if (propertiesSection.getIconModal().mouseClicked(mouseX, mouseY, button)) return true;
            return true; 
        }

        if (!currentFileName.isEmpty() && propertiesSection.fldAttribute instanceof StudioSuggestTextField suggestField) {
            if (suggestField.showSuggestions()) {
                if (suggestField.isMouseOver(mouseX, mouseY)) {
                    if (suggestField.mouseClicked(mouseX, mouseY, button)) {
                        return true;
                    }
                }
            }
        }

        if (contextMenu.isOpen) {
            if (contextMenu.mouseClicked(mouseX, mouseY, button)) return true;
            contextMenu.isOpen = false; 
            return true; 
        }

        if (menuBar.mouseClicked(mouseX, mouseY, button)) return true;

        if (leftPanelOpen && mouseX < LEFT_PANEL_WIDTH) {
            if (sidePanel.mouseClicked(mouseX, mouseY, button)) return true;
        }

        int currentLeftWidth = leftPanelOpen ? LEFT_PANEL_WIDTH : 0;
        if (!currentFileName.isEmpty()) {
            double scrolledMouseY = mouseY + mainScrollY;

            if (propertiesSection.mouseClicked(mouseX, scrolledMouseY, button)) return true;
            if (statsSection.mouseClicked(mouseX, scrolledMouseY, button)) return true;

            int treeStartY = getTreeStartY();
            if (!isRawJsMode() && mouseY > (treeStartY - mainScrollY) && mouseX >= currentLeftWidth) {
                if (logicTree.handleMouseClick(mouseX, scrolledMouseY, mouseY, button)) return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double scrolledMouseY = mouseY + mainScrollY;
        if (!isRawJsMode() && logicTree.handleMouseRelease(mouseX, scrolledMouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!currentFileName.isEmpty() && propertiesSection.fldAttribute instanceof StudioSuggestTextField suggestField) {
            if (suggestField.showSuggestions() && suggestField.isMouseOver(mouseX, mouseY)) {
                if (suggestField.mouseScrolled(mouseX, mouseY, delta)) {
                    return true;
                }
            }
        }

        if (activeModal != null) {
            if (activeModal.mouseScrolled(mouseX, mouseY, delta)) return true;
        }
        if (activeStatModal != null) {
            if (activeStatModal.mouseScrolled(mouseX, mouseY, delta)) return true;
        }
        if (propertiesSection.isEditingIcon && propertiesSection.getIconModal() != null) {
            if (propertiesSection.getIconModal().mouseScrolled(mouseX, mouseY, delta)) return true;
        }

        if (!isBackgroundActive()) return false;

        int currentLeftWidth = leftPanelOpen ? LEFT_PANEL_WIDTH : 0;
        if (leftPanelOpen && mouseX < currentLeftWidth) {
            sidePanel.handleScroll(delta);
            return true;
        }

        if (!currentFileName.isEmpty()) {
            double totalContentHeight = getTreeStartY() + (isRawJsMode() ? 220 : getLogicTreeHeight());
            double visibleHeight = height - (StudioMenuBar.HEIGHT + 35);
            double maxScroll = Math.max(0, totalContentHeight - visibleHeight);

            mainScrollY = Math.max(0, Math.min(maxScroll, mainScrollY - (delta * 18)));
            updateHeaderVisibility();
        }
        return true;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (projectDialog.isOpen()) return projectDialog.charTyped(codePoint, modifiers);
        if (activeModal != null) return activeModal.charTyped(codePoint, modifiers);
        if (activeStatModal != null) return activeStatModal.charTyped(codePoint, modifiers);
        if (propertiesSection.isEditingIcon && propertiesSection.getIconModal() != null) {
            return propertiesSection.getIconModal().charTyped(codePoint, modifiers);
        }
        return super.charTyped(codePoint, modifiers);
    }

	@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isWaitingForServer) return false;
        if (!isAuthorized) return super.keyPressed(keyCode, scanCode, modifiers);

        // 1. Offene Modale & Dialoge verarbeiten ESC ZUERST (schließen nur sich selbst)
        if (projectDialog.isOpen()) return projectDialog.keyPressed(keyCode, scanCode, modifiers);
        if (activeModal != null) return activeModal.keyPressed(keyCode, scanCode, modifiers);
        if (activeStatModal != null) return activeStatModal.keyPressed(keyCode, scanCode, modifiers);
        if (propertiesSection.isEditingIcon && propertiesSection.getIconModal() != null) {
            return propertiesSection.getIconModal().keyPressed(keyCode, scanCode, modifiers);
        }

        // 2. Kontextmenü bei ESC schließen
        if (contextMenu.isOpen && keyCode == 256) {
            contextMenu.isOpen = false;
            return true;
        }

        // 3. ESC auf der Hauptoberfläche -> Löst geordnetes Schließen aus
        if (keyCode == 256) { // ESC Key
            this.onClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        // Sicheres Beenden: Prüft auf ungespeicherte Änderungen vor dem echten Schließen!
        requestActionWithUnsavedWarning(this::closeScreenDirectly);
    }

    public void closeScreenDirectly() {
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() { return true; }

	public void openEditModal(TreeNode node) {
		if (node.type == TreeNode.Type.ACTION && node.jsonData != null && node.jsonData.has("type")) {
			String type = node.jsonData.get("type").getAsString();
			if ("stones:spawn_sprite".equals(type)) {
				activeModal = new FxEditModal(this, node);
				return;
			}
			if ("stones:spawn_beam".equals(type)) {
				activeModal = new BeamEditModal(this, node);
				return;
			}
		}
		if (node.type == TreeNode.Type.ACTION || node.type == TreeNode.Type.CONDITION) {
			activeModal = new ActionEditModal(this, node);
		}
	}

    public void openEditModal(AbstractStudioModal modal) {
        this.activeModal = modal;
    }

    public void openNewProjectDialog() { this.projectDialog.open(); }
    public void closeStatModal() { this.activeStatModal = null; }
}

class UnsavedChangesModal extends AbstractStudioModal {
    private final Runnable confirmAction;

    public UnsavedChangesModal(StonesStudioScreen screen, Runnable confirmAction) {
        super(screen, net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_12"), 320, 110);
        this.confirmAction = confirmAction;
        this.init();
    }

    @Override
    protected void initFields(int startX, int startY) {
        addModalWidget(Button.builder(net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_13"), b -> {
            screen.closeModal();
            confirmAction.run();
        }).bounds(startX + 25, startY + 70, 150, 20).build());

        addModalWidget(Button.builder(net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_14"), b -> {
            screen.closeModal();
        }).bounds(startX + 185, startY + 70, 110, 20).build());
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int startX, int startY) {
        graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_15").getString(), startX + 15, startY + 32, 0xFFFF5555);
        graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_16").getString(), startX + 15, startY + 44, 0xFFBBBBBB);
        graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_17").getString(), startX + 15, startY + 56, 0xFFBBBBBB);
    }

    @Override
    public void onCancel() {
        screen.closeModal();
    }
}

class ScriptModeWarningModal extends AbstractStudioModal {
    private final Runnable confirmAction;
    private final boolean switchingToRawJs;

    public ScriptModeWarningModal(StonesStudioScreen screen, boolean switchingToRawJs, Runnable confirmAction) {
        super(screen, net.minecraft.network.chat.Component.literal(switchingToRawJs ? "⚠️ Switch to Raw JS" : "⚠️ Switch to Visual Tree"), 340, 125);
        this.switchingToRawJs = switchingToRawJs;
        this.confirmAction = confirmAction;
        this.init();
    }

    @Override
    protected void initFields(int startX, int startY) {
        addModalWidget(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("Continue"), b -> {
            screen.closeModal();
            confirmAction.run();
        }).bounds(startX + 25, startY + 85, 130, 20).build());

        addModalWidget(net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("Cancel"), b -> {
            screen.closeModal();
        }).bounds(startX + 185, startY + 85, 130, 20).build());
    }

    @Override
    protected void renderContent(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int startX, int startY) {
        if (switchingToRawJs) {
            graphics.drawString(font, "Warning: Raw JS mode disables the visual", startX + 15, startY + 30, 0xFFFFAA00);
            graphics.drawString(font, "tree editor. Subsequent JS changes cannot", startX + 15, startY + 44, 0xFFBBBBBB);
            graphics.drawString(font, "be converted back to visual nodes!", startX + 15, startY + 58, 0xFFBBBBBB);
        } else {
            graphics.drawString(font, "Warning: Switching to the visual tree will", startX + 15, startY + 30, 0xFFFF5555);
            graphics.drawString(font, "permanently delete all handwritten Raw JS", startX + 15, startY + 44, 0xFFBBBBBB);
            graphics.drawString(font, "code!", startX + 15, startY + 58, 0xFFBBBBBB);
        }
    }

    @Override
    public void onCancel() {
        screen.closeModal();
    }
}