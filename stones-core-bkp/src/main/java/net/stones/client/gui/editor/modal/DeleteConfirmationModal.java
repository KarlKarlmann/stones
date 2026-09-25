package net.stones.client.gui.editor.modal;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.stones.client.gui.editor.StonesStudioScreen;
import net.stones.client.gui.editor.TreeNode;
import net.stones.client.gui.editor.section.StudioContextMenu;

/**
 * Ein kleines, spezialisiertes Bestätigungs-Modal ("Bist du dir sicher?").
 */
public class DeleteConfirmationModal extends ActionEditModal {

    public DeleteConfirmationModal(StonesStudioScreen screen, TreeNode node) {
        super(screen, node);
        this.title = Component.translatable("gui.stones.studio.deleteconfirmation.title");
        this.width = 280;
        this.height = 110;
        this.recenter(); // Setzt X und Y neu
        
        // Da wir init() in super() schon gerufen haben, müssen wir hier die Felder überschreiben/löschen
        this.children.clear();

        this.btnSave = addModalWidget(Button.builder(Component.translatable("gui.stones.studio.deleteconfirmation.button.delete"), b -> {
            StudioContextMenu.saveState();
            if (targetNode.parent != null) targetNode.parent.children.remove(targetNode);
            else StonesStudioScreen.activeTree.remove(targetNode);
            screen.closeModal();
        }).bounds(x + 35, y + 70, 100, 20).build());

        this.btnCancel = addModalWidget(Button.builder(Component.translatable("gui.stones.studio.deleteconfirmation.button.cancel"), b -> {
            screen.closeModal();
        }).bounds(x + 145, y + 70, 100, 20).build());
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int startX, int startY) {
        String line1 = Component.translatable("gui.stones.studio.deleteconfirmation.line1").getString();
        String element = "\"" + targetNode.readableText + "\"";
        if (element.length() > 30) element = element.substring(0, 27) + "...\"";
        String line2 = Component.translatable("gui.stones.studio.deleteconfirmation.line2").getString();

        graphics.drawString(font, line1, startX + 15, startY + 32, 0xFFBBBBBB);
        graphics.drawString(font, element, startX + 15, startY + 44, 0xFFFFAA00);
        graphics.drawString(font, line2, startX + 15, startY + 56, 0xFFBBBBBB);
    }
}