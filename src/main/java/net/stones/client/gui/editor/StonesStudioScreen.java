package net.stones.client.gui.editor;

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
import net.stones.network.StudioNetwork;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.stones.client.gui.editor.TreeNode;
import net.stones.client.gui.editor.StudioSerializer;
import net.stones.client.gui.editor.section.StudioProjectDialog;
import net.stones.client.gui.editor.section.StudioMenuBar;
import net.stones.client.gui.editor.section.StudioContextMenu;
import net.stones.client.gui.editor.section.SidePanelRenderer;
import net.stones.client.gui.editor.section.RuneStatsSection;
import net.stones.client.gui.editor.section.RunePropertiesSection;
import net.stones.client.gui.editor.section.BehaviorTreeRenderer;
import net.stones.client.gui.editor.widget.StudioSuggestTextField;
import net.stones.client.gui.editor.modal.StatEditModal;
import net.stones.client.gui.editor.modal.ActionEditModal;
import net.stones.client.gui.editor.modal.AbstractStudioModal;
import net.stones.client.gui.editor.modal.TemplateUpdateModal;

/**
 * ARCHITEKTUR: STONES STUDIO ORCHESTRATOR
 * Das primäre Rendering- und Input-Handling-Fenster für das Stones Studio.
 *
 * AKTUALISIERT: Reine Client-GUI. Hash-Logik und Dateiprüfung auf den Server ausgelagert.
 */
public class StonesStudioScreen extends Screen {

    public static final int LEFT_PANEL_WIDTH = 180;
    private boolean leftPanelOpen = true;

    // --- Accordion-Zustände auf dem Bildschirm ---
    public static boolean isPropertiesExpanded = true;
    public static boolean isStatsExpanded = true;

    // --- Globaler Scroll-Offset für den gesamten rechten Workspace-Screen ---
    private double mainScrollY = 0;

    // --- Deferred Tooltip Render Queue ---
    private List<FormattedCharSequence> deferredTooltip = null;
    private int deferredTooltipX = 0;
    private int deferredTooltipY = 0;

    // --- Snapshot-Speicher für ungespeicherte Änderungen ---
    private String lastSavedJsonString = "";

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
        isWaitingForServer = true;
        waitStartTime = System.currentTimeMillis();
        discoveredPacks.clear();
        StudioNetwork.CHANNEL.sendToServer(new StudioNetwork.C2SRequestPackList());
        checkLocalWorldPacks();
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

    public static void receiveServerPackList(List<String> serverPacks, String activePackName, boolean authorized, List<String> files) {
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

    public void requestActionWithUnsavedWarning(Runnable action) {
        if (!currentFileName.isEmpty()) {
            String currentJson = serializeActiveTree().toString();
            if (!currentJson.equals(lastSavedJsonString)) {
                this.activeModal = new UnsavedChangesModal(this, action);
                return;
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

            this.lastSavedJsonString = serializeActiveTree().toString();

            // Nur wenn der Server explizit einen Konflikt meldet, Modal anzeigen
            if (hasConflict && jarTemplateStr != null && !jarTemplateStr.isEmpty()) {
                JsonObject jarTemplate = JsonParser.parseString(jarTemplateStr).getAsJsonObject();
                this.activeModal = new TemplateUpdateModal(this, fileName, loadedJson, jarTemplate, newJarHash, () -> {
                    // Continuation callback
                });
            }

        } catch (Exception e) {
            Minecraft.getInstance().player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_01" + fileName));
        }

        updateHeaderVisibility();
    }

    public JsonObject serializeActiveTree() {
        StudioContextMenu.prepareTreeForSaving(activeTree);

        // Zieht sich eine Kopie des aktuellen JSONs (inklusive dem existierenden Hash vom Server!)
        JsonObject root = currentRuneJson.deepCopy();
        propertiesSection.saveTo(root);

        JsonArray statsArray = new JsonArray();
        for (JsonObject s : activeStats) {
            statsArray.add(s.deepCopy());
        }
        root.add("stats", statsArray);

        JsonArray behaviorsArray = StudioSerializer.serializeBehaviors(activeTree);
        root.add("behaviors", behaviorsArray);

        currentRuneJson = root;
        return root;
    }

    @Override
    protected void init() {
        super.init();
        currentInstance = this;

        int currentLeftWidth = leftPanelOpen ? LEFT_PANEL_WIDTH : 0;
        int headerX = currentLeftWidth + 20;
        int yStart = StudioMenuBar.HEIGHT + 45;

        this.propertiesSection.init(headerX, yStart);

        addRenderableWidget(Button.builder(net.minecraft.network.chat.Component.translatable("gui.stones.studio.stonesstudio.text_02"), btn -> toggleLeftPanel())
                .bounds(5, StudioMenuBar.HEIGHT + 5, 20, 20).build());

        this.projectDialog.initDialog();
        updateHeaderVisibility();
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
            logicTree.render(graphics, editorX, treeStartY, bgMouseX, bgScrolledMouseY);

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
            if (mouseY > (treeStartY - mainScrollY) && mouseX >= currentLeftWidth) {
                if (logicTree.handleMouseClick(mouseX, scrolledMouseY, mouseY, button)) return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double scrolledMouseY = mouseY + mainScrollY;
        if (logicTree.handleMouseRelease(mouseX, scrolledMouseY, button)) return true;
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
            double totalContentHeight = getTreeStartY() + getLogicTreeHeight();
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
        if (keyCode == 256) { // ESC Key
            this.onClose();
            return true;
        }

        if (isWaitingForServer) return false;
        if (!isAuthorized) return super.keyPressed(keyCode, scanCode, modifiers);
        if (projectDialog.isOpen()) return projectDialog.keyPressed(keyCode, scanCode, modifiers);
        if (activeModal != null) return activeModal.keyPressed(keyCode, scanCode, modifiers);
        if (activeStatModal != null) return activeStatModal.keyPressed(keyCode, scanCode, modifiers);
        if (propertiesSection.isEditingIcon && propertiesSection.getIconModal() != null) {
            return propertiesSection.getIconModal().keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return true; }

    public void openEditModal(TreeNode node) {
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

/**
 * Warn-Dialog, falls man die Datei verlässt oder neulädt, während man noch
 * ungespeicherte Änderungen offen hat.
 */
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