package net.stones.client.gui.editor.modal;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.stones.client.gui.editor.StonesStudioScreen;
import net.stones.network.StudioNetwork;
import net.stones.util.TemplateHashHelper;

public class TemplateUpdateModal extends AbstractStudioModal {

    private final String fileName;
    private final JsonObject playerJson;
    private final JsonObject jarJson;
    private final String newJarHash;
    private final Runnable onComplete;

    public TemplateUpdateModal(StonesStudioScreen screen, String fileName, JsonObject playerJson, JsonObject jarJson, String newJarHash, Runnable onComplete) {
        super(screen, Component.translatable("gui.stones.studio.templateupdate.title"), 340, 130);
        this.fileName = fileName;
        this.playerJson = playerJson;
        this.jarJson = jarJson;
        this.newJarHash = newJarHash;
        this.onComplete = onComplete;
        this.init();
    }

    @Override
    protected void initFields(int startX, int startY) {
        // JA-BUTTON: Backup erstellen & auf neue Vorlage zurücksetzen
        addModalWidget(Button.builder(Component.translatable("gui.stones.studio.templateupdate.button.yes_backup"), b -> {
            createBackupAndApplyJarTemplate();
            screen.closeModal();
            onComplete.run();
        }).bounds(startX + 15, startY + 85, 150, 20).build());

        // NEIN-BUTTON: Eigene Änderungen behalten, aber Hash erneuern (kein erneutes Nachfragen)
        addModalWidget(Button.builder(Component.translatable("gui.stones.studio.templateupdate.button.no_keep"), b -> {
            keepPlayerEditsAndUpdateHash();
            screen.closeModal();
            onComplete.run();
        }).bounds(startX + 175, startY + 85, 150, 20).build());
    }

	private void createBackupAndApplyJarTemplate() {
        // 1. Sichere die alte Spielerdatei serverseitig als .bak (hier ignoriert der Server fehlende Hashes)
        String backupFileName = fileName + ".bak";
        StudioNetwork.CHANNEL.sendToServer(new StudioNetwork.C2SSaveRuneFile(backupFileName, playerJson.toString()));

        // 2. Ersetze Inhalt mit JAR-Vorlage und LÖSCHE den eventuell vorhandenen alten Hash
        JsonObject newContent = jarJson.deepCopy();
        newContent.remove(TemplateHashHelper.HASH_KEY);
        
        // 3. Speichern (Der Server stempelt jetzt automatisch den NEUEN Hash!)
        StudioNetwork.CHANNEL.sendToServer(new StudioNetwork.C2SSaveRuneFile(fileName, newContent.toString()));
        screen.loadRuneFromJson(fileName, newContent.toString(), false, null, null);
    }

    private void keepPlayerEditsAndUpdateHash() {
        // Spielerinhalt behalten, aber den alten Hash LÖSCHEN
        playerJson.remove(TemplateHashHelper.HASH_KEY);
        
        // Speichern (Der Server sieht, dass der Hash fehlt, und stempelt seinen aktuellen JAR-Hash auf deine editierten Daten!)
        StudioNetwork.CHANNEL.sendToServer(new StudioNetwork.C2SSaveRuneFile(fileName, playerJson.toString()));
        screen.loadRuneFromJson(fileName, playerJson.toString(), false, null, null);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int startX, int startY) {
        graphics.drawString(font, Component.translatable("gui.stones.studio.templateupdate.line1", fileName).getString(), startX + 15, startY + 30, 0xFFFFAA00);
        graphics.drawString(font, Component.translatable("gui.stones.studio.templateupdate.line2").getString(), startX + 15, startY + 44, 0xFFBBBBBB);
        graphics.drawString(font, Component.translatable("gui.stones.studio.templateupdate.line3").getString(), startX + 15, startY + 56, 0xFFBBBBBB);
    }

    @Override
    public void onCancel() {
        screen.closeModal();
    }
}