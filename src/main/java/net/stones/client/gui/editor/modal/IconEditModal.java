package net.stones.client.gui.editor.modal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.stones.client.gui.editor.StonesStudioScreen;
import net.stones.client.gui.editor.section.RunePropertiesSection;
import net.stones.client.gui.editor.widget.StudioSuggestTextField;
import net.stones.client.gui.editor.modal.AbstractStudioModal;

/**
 * Das Einstellungsfenster für das Runen-Icon.
 * Nutzt jetzt das neue, abstrakte Vorschlagsfeld (IconSuggestField), 
 * um Texturen aus allen Namespaces dynamisch per Autocomplete aufzulisten.
 */
public class IconEditModal extends AbstractStudioModal {

    private final RunePropertiesSection parentSection;
    private StudioSuggestTextField.IconSuggestField fldModalIconPath; // Nutzt die spezialisierte suggest-Subklasse
    private Button btnModalConfirm;
    private Button btnModalCancel;

    public IconEditModal(StonesStudioScreen screen, RunePropertiesSection parentSection) {
        // Text: "✦ Icon-Ressourcenpfad bearbeiten"
        super(screen, net.minecraft.network.chat.Component.translatable("gui.stones.studio.iconedit.text_01"), 280, 100);
        this.parentSection = parentSection;
        this.init();
    }

    @Override
    protected void initFields(int x, int y) {
        // Instanziierung als IconSuggestField mit Auto-Complete
        // Text: "Icon-Pfad"
        this.fldModalIconPath = addModalWidget(new StudioSuggestTextField.IconSuggestField(screen, font, x + 10, y + 32, 260, 16, net.minecraft.network.chat.Component.translatable("gui.stones.studio.iconedit.text_02"), 
        // Text: "Ressourcenpfad (z.B. stones:textures/items/rune_minor.png)"
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.iconedit.text_03")));
        
        this.fldModalIconPath.setValue(parentSection.iconState);
        this.fldModalIconPath.setFocused(true);
        setFocused(fldModalIconPath); 

        // Text: "Speichern"
        this.btnModalConfirm = addModalWidget(Button.builder(net.minecraft.network.chat.Component.translatable("gui.stones.studio.iconedit.text_04"), btn -> {
            parentSection.iconState = this.fldModalIconPath.getValue().trim();
            onCancel();
        }).bounds(x + 110, y + 63, 75, 16).build());

        // Text: "Abbrechen"
        this.btnModalCancel = addModalWidget(Button.builder(net.minecraft.network.chat.Component.translatable("gui.stones.studio.iconedit.text_05"), btn -> {
            onCancel();
        }).bounds(x + 195, y + 63, 75, 16).build());
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int x, int y) {
        int pX = x + 10;
        int pY = y + 55;
        graphics.fill(pX, pY, pX + 32, pY + 32, 0xFF0D0D0E);
        graphics.renderOutline(pX, pY, 32, 32, 0xFF444449);

        String currentInput = fldModalIconPath.getValue().trim();
        ResourceLocation previewRl = new ResourceLocation("stones", "textures/block/runestone.png");
        boolean pExists = false;
        if (!currentInput.isEmpty()) {
            try {
                previewRl = new ResourceLocation(currentInput);
                pExists = Minecraft.getInstance().getResourceManager().getResource(previewRl).isPresent();
            } catch (Exception ignored) {}
        }

        if (!currentInput.isEmpty() && pExists) {
            graphics.blit(previewRl, pX + 2, pY + 2, 0, 0, 28, 28, 28, 28);
        } else {
            ResourceLocation fallbackRl = new ResourceLocation("stones", "textures/block/runestone.png");
            graphics.blit(fallbackRl, pX + 2, pY + 2, 0, 0, 28, 28, 28, 28);
            if (!currentInput.isEmpty()) {
                graphics.renderOutline(pX, pY, 32, 32, 0xFFFF5555);
        // Text: "?"
                graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.iconedit.text_06").getString(), pX + 13, pY + 12, 0xFFFF5555);
            }
        }
    }

    @Override
    public void onCancel() {
        parentSection.isEditingIcon = false;
        screen.setFocused(null); 
        screen.updateHeaderVisibility();
    }
}