package net.stones.client.gui.editor.modal;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.stones.client.gui.editor.StonesStudioScreen;
import net.stones.client.gui.editor.widget.StudioUIHelper;
import net.stones.client.gui.editor.widget.StudioTextField;
import net.stones.client.gui.editor.modal.AbstractStudioModal;

/**
 * Formular-Editor für einzelne Rune-Stats.
 * Übergibt Screen-Referenz für sauberes Tooltip-Handling.
 * Aktualisiert: Unterstützt nun 'min' und 'max' Hardcaps!
 */
public class StatEditModal extends AbstractStudioModal {

    private final JsonObject targetStat;
    private final boolean isNew;

    private StudioTextField fldStatId;
    private StudioTextField fldStatLabel;
    private StudioTextField fldStatBase;
    private StudioTextField fldStatPerLevel;
    private StudioTextField fldStatSuffix;
    
    // NEU: Min & Max Felder
    private StudioTextField fldStatMin;
    private StudioTextField fldStatMax;
    
    private Button btnScale;
    private String scalingState = "RUNE_LEVEL";

    private Button btnConfirm;
    private Button btnCancel;

    public StatEditModal(StonesStudioScreen screen, JsonObject stat, boolean isNew) {
        // Höhe von 180 auf 220 erhöht, um Platz für Min & Max zu schaffen
        super(screen, Component.literal(isNew ? "✦ Stat Hinzufügen" : "✦ Stat Eigenschaften ändern"), 320, 220);
        this.targetStat = stat;
        this.isNew = isNew;
        this.init();
    }

    @Override
    protected void initFields(int x, int y) {
        // Text: "ID"
        this.fldStatId = addModalWidget(new StudioTextField(screen, font, x + 110, y + 25, 190, 14, net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_01"),
        // Text: "Der eindeutige interne Name dieses Parameters (z. B. proc_chance)."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_02")));
        
        // Text: "Label"
        this.fldStatLabel = addModalWidget(new StudioTextField(screen, font, x + 110, y + 43, 190, 14, net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_03"),
        // Text: "Anzeigenamen im Menü. Kann auch als Lokalisierungsschlüssel übergeben werden mit DICT:..."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_04")));
        
        // Text: "Base"
        this.fldStatBase = addModalWidget(new StudioTextField(screen, font, x + 110, y + 61, 190, 14, net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_05"),
        // Text: "Der Basiswert dieses Parameters auf Enchantment Stufe 1."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_06")));
        
        // Text: "PerLevel"
        this.fldStatPerLevel = addModalWidget(new StudioTextField(screen, font, x + 110, y + 79, 190, 14, net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_07"),
        // Text: "Die Wertsteigerung pro zusätzlicher Enchantment Stufe."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_08")));
        
        // Text: "Suffix"
        this.fldStatSuffix = addModalWidget(new StudioTextField(screen, font, x + 110, y + 97, 190, 14, net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_09"),
        // Text: "Die Maßeinheit oder das Suffix des Werts (z. B. %, Sek, Ticks)."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_10")));

        // NEU: Min Input
        // Text: "Min Limit:"
        this.fldStatMin = addModalWidget(new StudioTextField(screen, font, x + 110, y + 115, 190, 14, net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_26"),
        // Text: "Das absolute Minimum, auf das dieser Stat fallen kann."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_27")));

        // NEU: Max Input
        // Text: "Max Limit:"
        this.fldStatMax = addModalWidget(new StudioTextField(screen, font, x + 110, y + 133, 190, 14, net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_28"),
        // Text: "Das absolute Maximum, auf das dieser Stat steigen kann."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_29")));

        this.fldStatId.setValue(targetStat.has("id") ? targetStat.get("id").getAsString() : "");
        this.fldStatLabel.setValue(targetStat.has("label") ? targetStat.get("label").getAsString() : "DICT:stat.stones.");
        this.fldStatBase.setValue(targetStat.has("base") ? targetStat.get("base").getAsString() : "0.0");
        this.fldStatPerLevel.setValue(targetStat.has("per_level") ? targetStat.get("per_level").getAsString() : "0.0");
        this.fldStatSuffix.setValue(targetStat.has("suffix") ? targetStat.get("suffix").getAsString() : "");
        
        // NEU: Min & Max Werte laden
        this.fldStatMin.setValue(targetStat.has("min") ? targetStat.get("min").getAsString() : "");
        this.fldStatMax.setValue(targetStat.has("max") ? targetStat.get("max").getAsString() : "");
        
        if (targetStat.has("scaling")) {
            this.scalingState = targetStat.get("scaling").getAsString();
        }

        // Skalierungs-Button nach unten verschoben (Y: 151)
        this.btnScale = addModalWidget(Button.builder(Component.literal(scalingState), btn -> {
            scalingState = switch (scalingState) {
                case "RUNE_LEVEL" -> "SOCKET_LEVEL";
                case "SOCKET_LEVEL" -> "PLAYER_LEVEL";
                default -> "RUNE_LEVEL";
            };
            btn.setMessage(Component.literal(scalingState));
        }).bounds(x + 110, y + 151, 190, 14)
        // Text: "Bestimmt, nach welchem Level der Stat skaliert."
          .tooltip(Tooltip.create(net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_11")))
          .build());

        // Buttons nach unten verschoben (Y: 185)
        // Text: "Übernehmen"
        this.btnConfirm = addModalWidget(Button.builder(net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_12"), btn -> {
            targetStat.addProperty("id", fldStatId.getValue().trim());
            targetStat.addProperty("label", fldStatLabel.getValue().trim());
            targetStat.addProperty("type", "generic");
            try { targetStat.addProperty("base", Double.parseDouble(fldStatBase.getValue().trim())); } catch (Exception ignored) {}
            try { targetStat.addProperty("per_level", Double.parseDouble(fldStatPerLevel.getValue().trim())); } catch (Exception ignored) {}
            targetStat.addProperty("scaling", scalingState);
            targetStat.addProperty("suffix", fldStatSuffix.getValue().trim());

            // NEU: Min & Max speichern oder sicher entfernen
            String minVal = fldStatMin.getValue().trim();
            if (!minVal.isEmpty()) {
                try { targetStat.addProperty("min", Double.parseDouble(minVal)); } catch (Exception ignored) {}
            } else {
                targetStat.remove("min");
            }

            String maxVal = fldStatMax.getValue().trim();
            if (!maxVal.isEmpty()) {
                try { targetStat.addProperty("max", Double.parseDouble(maxVal)); } catch (Exception ignored) {}
            } else {
                targetStat.remove("max");
            }

            if (isNew) {
                StonesStudioScreen.activeStats.add(targetStat);
            }
            screen.closeStatModal();
        }).bounds(x + 50, y + 185, 100, 16).build());

        // Text: "Abbrechen"
        this.btnCancel = addModalWidget(Button.builder(net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_13"), btn -> {
            onCancel();
        }).bounds(x + 170, y + 185, 100, 16).build());

        this.fldStatId.setFocused(true);
        setFocused(fldStatId);
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int x, int y) {
        // Text: "Stat ID:"
        StudioUIHelper.drawLabelWithTooltip(screen, graphics, font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_14"), x + 15, y + 28, mouseX, mouseY, 
        // Text: "Der systemweite, eindeutige Parametername."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_15"));
        
        // Text: "Label Key:"
        StudioUIHelper.drawLabelWithTooltip(screen, graphics, font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_16"), x + 15, y + 46, mouseX, mouseY, 
        // Text: "Der Name im Spielmenü. Kann auch als Lokalisierungsschlüssel übergeben werden mit DICT:..."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_17"));
        
        // Text: "Base Wert:"
        StudioUIHelper.drawLabelWithTooltip(screen, graphics, font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_18"), x + 15, y + 64, mouseX, mouseY, 
        // Text: "Der Startwert des Parameters auf Stufe 1."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_19"));
        
        // Text: "Per Level:"
        StudioUIHelper.drawLabelWithTooltip(screen, graphics, font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_20"), x + 15, y + 82, mouseX, mouseY, 
        // Text: "Der Wertzuwachs pro Runenstufe."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_21"));
        
        // Text: "Suffix:"
        StudioUIHelper.drawLabelWithTooltip(screen, graphics, font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_22"), x + 15, y + 100, mouseX, mouseY, 
        // Text: "Die Maßeinheit oder das Suffix des Parameters (z. B. %, Sek, Ticks)."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_23"));
            
        // NEU: Min Label
        // Text: "Min Limit:"
        StudioUIHelper.drawLabelWithTooltip(screen, graphics, font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_26"), x + 15, y + 118, mouseX, mouseY, 
        // Text: "Das absolute Minimum, auf das dieser Stat fallen kann."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_27"));
            
        // NEU: Max Label
        // Text: "Max Limit:"
        StudioUIHelper.drawLabelWithTooltip(screen, graphics, font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_28"), x + 15, y + 136, mouseX, mouseY, 
        // Text: "Das absolute Maximum, auf das dieser Stat steigen kann."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_29"));
        
        // Text: "Skalierung:"
        StudioUIHelper.drawLabelWithTooltip(screen, graphics, font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_24"), x + 15, y + 154, mouseX, mouseY, 
        // Text: "Bestimmt, ob der Wert mit dem Runenlevel, Sockel-Level oder Spieler-Level berechnet wird."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.statedit.text_25"));
    }

    @Override
    public void onCancel() {
        screen.closeStatModal();
    }
}