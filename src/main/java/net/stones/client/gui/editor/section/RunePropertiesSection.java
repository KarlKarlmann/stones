package net.stones.client.gui.editor.section;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.stones.client.gui.editor.StonesStudioScreen;
import net.stones.client.gui.editor.section.StudioMenuBar;
import net.stones.client.gui.editor.widget.StudioSuggestTextField;
import net.stones.client.gui.editor.widget.StudioMultiLineEditBox;
import net.stones.client.gui.editor.widget.StudioTextField;
import net.stones.client.gui.editor.widget.StudioButton;
import net.stones.client.gui.editor.modal.IconEditModal;

/**
 * Sub-Renderer für das Eigenschaften-Accordion ("Allgemeine Eigenschaften").
 * Angepasst auf das Deferred Render Queue System und die neue Screen-Referenz.
 */
public class RunePropertiesSection {

    private final StonesStudioScreen screen;

    public StudioTextField fldName;
    public StudioMultiLineEditBox fldDescription;
    public Button btnType;
    public String typeState = "MINOR";

    public StudioTextField fldMaxLevel;
    public StudioTextField fldReqLevel;
    public StudioTextField fldFactor;
    public Button btnIsCurse;
    public boolean isCurseState = false;

    // Nutzt jetzt das neue StudioSuggestTextField für Auto-Complete Support
    public StudioTextField fldAttribute;
    public Button btnOperation;
    public String operationState = "ADDITION";

    public String iconState = "";
    public boolean isEditingIcon = false;
    
    private IconEditModal iconModal;

    public RunePropertiesSection(StonesStudioScreen screen) {
        this.screen = screen;
    }

    public void init(int headerX, int yStart) {
        Font font = screen.getFont();

        // Text: "Name"
        this.fldName = screen.addWidget(new StudioTextField(screen, font, headerX + 85, yStart + 5, 100, 14, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_01"), 
        // Text: "Der Anzeigename der Rune. Kann auch als Lokalisierungsschlüssel angegeben werden mit DICT:..."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_02")));
        
        this.fldDescription = screen.addWidget(new StudioMultiLineEditBox(screen, font, headerX + 260, yStart + 5, 130, 31, 
        // Text: "Beschreibung..."
        // Text: "Description"
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_03"), net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_04"),
        // Text: "Die Beschreibung des Zaubereffekts, die im Spielmenü angezeigt wird. Unterstützt %level% und %roman%."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_05")));
        
        // NEU: Nutzt jetzt StudioButton für perfektes Tooltip-Wrapping!
        // Text: "MINOR"
        this.btnType = screen.addWidget(new StudioButton(screen, headerX + 80, yStart + 22, 55, 14, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_06"), btn -> cycleRuneType(), 
        // Text: "Klicke, um den Runentyp (MINOR, MAJOR, MILESTONE) zu wechseln."
                net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_07")));

        // NEU: Nutzt jetzt StudioButton
        // Text: "Nein"
        this.btnIsCurse = screen.addWidget(new StudioButton(screen, headerX + 180, yStart + 22, 35, 14, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_08"), btn -> {
            isCurseState = !isCurseState;
            updateCurseButtonText();
        // Text: "Legt fest, ob die Rune als Fluch gilt."
        }, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_09")));

        // Text: "MaxLvl"
        this.fldMaxLevel = screen.addWidget(new StudioTextField(screen, font, headerX + 55, yStart + 46, 35, 14, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_10"), 
        // Text: "Das maximale Enchantment Level dieses Enchantments."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_11")));
        
        // Text: "ReqLvl"
        this.fldReqLevel = screen.addWidget(new StudioTextField(screen, font, headerX + 145, yStart + 46, 35, 14, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_12"), 
        // Text: "Die Steigerung des Runelevels pro Enchantment Level."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_13")));

        // KORREKTUR: Instanziierung der konkreten statischen Subklasse "AttributeSuggestField" anstatt der abstrakten Elternklasse
        // Text: "Attribute"
        this.fldAttribute = screen.addWidget(new StudioSuggestTextField.AttributeSuggestField(screen, font, headerX + 40, yStart + 71, 110, 14, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_14"), 
        // Text: "Das optionale Minecraft-Attribut, welches modifiziert werden soll."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_15")));

        // NEU: Nutzt jetzt StudioButton
        // Text: "ADDITION"
        this.btnOperation = screen.addWidget(new StudioButton(screen, headerX + 195, yStart + 71, 75, 14, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_16"), btn -> cycleOperation(), 
        // Text: "Bestimmt, ob das Attribut addiert oder multipliziert werden soll."
                net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_17")));

        // Text: "Factor"
        this.fldFactor = screen.addWidget(new StudioTextField(screen, font, headerX + 335, yStart + 71, 35, 14, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_18"), 
        // Text: "Der Faktor um das das Atribut pro Enchantment Level geändert werden soll."
            net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_19")));

        if (isEditingIcon) {
            iconModal = new IconEditModal(screen, this);
        } else {
            iconModal = null;
        }

        updateRuneTypeButtonText();
        updateCurseButtonText();
        updateOperationButtonText();
    }

    private void cycleRuneType() {
        typeState = switch (typeState) {
            case "MINOR" -> "MAJOR";
            case "MAJOR" -> "MILESTONE";
            default -> "MINOR";
        };
        updateRuneTypeButtonText();
    }

    private void updateRuneTypeButtonText() {
        if (btnType != null) {
            btnType.setMessage(Component.literal(typeState));
        }
    }

    private void cycleOperation() {
        operationState = switch (operationState) {
            case "ADDITION" -> "MULTIPLY";
            default -> "ADDITION";
        };
        updateOperationButtonText();
    }

    private void updateOperationButtonText() {
        if (btnOperation != null) {
            btnOperation.setMessage(Component.literal(operationState));
        }
    }

    private void updateCurseButtonText() {
        if (btnIsCurse != null) {
            btnIsCurse.setMessage(Component.literal(isCurseState ? "Ja" : "Nein"));
        }
    }

    public void updateVisibility(int headerX, int propContentY, int screenHeight, boolean isFileLoaded) {
        boolean showFields = isFileLoaded && StonesStudioScreen.isPropertiesExpanded && screen.isBackgroundActive()
            && (propContentY >= StudioMenuBar.HEIGHT + 5) && (propContentY <= screenHeight - 25);

        if (isEditingIcon) {
            if (iconModal == null) {
                iconModal = new IconEditModal(screen, this);
            }
            toggleMainFields(false);
        } else {
            iconModal = null;
            toggleMainFields(showFields, headerX, propContentY);
        }
    }

    private void toggleMainFields(boolean visible) {
        toggleMainFields(visible, 0, 0);
    }

    private void toggleMainFields(boolean visible, int headerX, int propContentY) {
        if (fldName != null) { fldName.visible = visible; if (visible) { fldName.setX(headerX + 85); fldName.setY(propContentY + 5); } }
        if (fldDescription != null) { fldDescription.visible = visible; if (visible) { fldDescription.setX(headerX + 260); fldDescription.setY(propContentY + 5); } }
        
        if (btnType != null) { btnType.visible = visible; if (visible) { btnType.setX(headerX + 80); btnType.setY(propContentY + 22); } }
        if (btnIsCurse != null) { btnIsCurse.visible = visible; if (visible) { btnIsCurse.setX(headerX + 180); btnIsCurse.setY(propContentY + 22); } }
        
        if (fldMaxLevel != null) { fldMaxLevel.visible = visible; if (visible) { fldMaxLevel.setX(headerX + 55); fldMaxLevel.setY(propContentY + 46); } }
        if (fldReqLevel != null) { fldReqLevel.visible = visible; if (visible) { fldReqLevel.setX(headerX + 145); fldReqLevel.setY(propContentY + 46); } }
        
        if (fldAttribute != null) { fldAttribute.visible = visible; if (visible) { fldAttribute.setX(headerX + 40); fldAttribute.setY(propContentY + 71); } }
        if (btnOperation != null) { btnOperation.visible = visible; if (visible) { btnOperation.setX(headerX + 195); btnOperation.setY(propContentY + 71); } }
        if (fldFactor != null) { fldFactor.visible = visible; if (visible) { fldFactor.setX(headerX + 335); fldFactor.setY(propContentY + 71); } }
    }

    public void loadFrom(JsonObject json) {
        if (fldName != null) fldName.setValue(json.has("name") ? json.get("name").getAsString() : "");
        if (fldDescription != null) fldDescription.setValue(json.has("description") ? json.get("description").getAsString() : "");

        typeState = json.has("type") ? json.get("type").getAsString() : "MINOR";
        updateRuneTypeButtonText();

        if (fldMaxLevel != null) fldMaxLevel.setValue(json.has("max_level") ? json.get("max_level").getAsString() : "20");
        if (fldReqLevel != null) fldReqLevel.setValue(json.has("required_level") ? json.get("required_level").getAsString() : "1.0");
        if (fldFactor != null) fldFactor.setValue(json.has("factor") ? json.get("factor").getAsString() : "0.0");
        
        this.iconState = json.has("icon") ? json.get("icon").getAsString() : "";
        
        isCurseState = json.has("is_curse") && json.get("is_curse").getAsBoolean();
        updateCurseButtonText();

        if (fldAttribute != null) {
            fldAttribute.setValue(json.has("attribute") ? json.get("attribute").getAsString() : "");
        } else {
            fldAttribute.setValue("");
        }

        operationState = json.has("operation") ? json.get("operation").getAsString() : "ADDITION";
        updateOperationButtonText();
    }

    public void saveTo(JsonObject json) {
        if (fldName != null) json.addProperty("name", fldName.getValue());
        if (fldDescription != null) json.addProperty("description", fldDescription.getValue());
        json.addProperty("type", typeState);
        try { if (fldMaxLevel != null) json.addProperty("max_level", Integer.parseInt(fldMaxLevel.getValue())); } catch (Exception ignored) {}
        try { if (fldReqLevel != null) json.addProperty("required_level", Float.parseFloat(fldReqLevel.getValue())); } catch (Exception ignored) {}
        try { if (fldFactor != null) json.addProperty("factor", Float.parseFloat(fldFactor.getValue())); } catch (Exception ignored) {}
        
        if (!this.iconState.trim().isEmpty()) {
            json.addProperty("icon", this.iconState.trim());
        } else {
            json.remove("icon");
        }

        if (isCurseState) json.addProperty("is_curse", true);
        else json.remove("is_curse");

        if (fldAttribute != null) {
            String attr = fldAttribute.getValue().trim();
            if (!attr.isEmpty()) {
                json.addProperty("attribute", attr);
                json.addProperty("operation", operationState);
            } else {
                json.remove("attribute");
                json.remove("operation");
            }
        }
    }

    public IconEditModal getIconModal() {
        return this.iconModal;
    }

    public void render(GuiGraphics graphics, int editorX, int mouseX, int mouseY) {
        Font font = screen.getFont();
        if (font == null) return;

        int areaWidth = screen.width - editorX - 20;

        int propHeaderY = screen.getPropHeaderY();
        boolean hoverProp = mouseX >= editorX && mouseX < editorX + areaWidth && mouseY >= propHeaderY && mouseY < propHeaderY + 12;
        
        graphics.fill(editorX, propHeaderY, editorX + areaWidth, propHeaderY + 12, hoverProp ? 0x22FFFFFF : 0x11FFFFFF);
        graphics.renderOutline(editorX, propHeaderY, areaWidth, 12, 0xFF444449);
        
        String propArrow = StonesStudioScreen.isPropertiesExpanded ? "▼ " : "▶ ";
        graphics.drawString(font, propArrow + "Allgemeine Eigenschaften", editorX + 6, propHeaderY + 2, 0xFFFFAA00);

        if (StonesStudioScreen.isPropertiesExpanded && !isEditingIcon) {
            int propContentY = screen.getPropContentY();
            
            graphics.fill(editorX, propContentY, editorX + areaWidth, propContentY + 115, 0xFF141416);
            graphics.renderOutline(editorX, propContentY, areaWidth, 115, 0xFF2D2D31);

            int iconX = editorX + 10;
            int iconY = propContentY + 5;
            graphics.fill(iconX, iconY, iconX + 32, iconY + 32, 0xFF0D0D0E);
            graphics.renderOutline(iconX, iconY, 32, 32, 0xFF444449);

            ResourceLocation iconRl = new ResourceLocation("stones", "textures/block/runestone.png");
            boolean pathEmpty = this.iconState == null || this.iconState.trim().isEmpty();
            if (!pathEmpty) {
                try {
                    iconRl = new ResourceLocation(this.iconState.trim());
                } catch (Exception ignored) {}
            }

            boolean exists = false;
            if (!pathEmpty) {
                exists = Minecraft.getInstance().getResourceManager().getResource(iconRl).isPresent();
            }

            if (!pathEmpty && exists) {
                graphics.blit(iconRl, iconX + 2, iconY + 2, 0, 0, 28, 28, 28, 28);
            } else {
                ResourceLocation fallbackRl = new ResourceLocation("stones", "textures/block/runestone.png");
                graphics.blit(fallbackRl, iconX + 2, iconY + 2, 0, 0, 28, 28, 28, 28);
                if (!pathEmpty) {
                    graphics.renderOutline(iconX, iconY, 32, 32, 0xFFFF5555);
        // Text: "?"
                    graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_20").getString(), iconX + 13, iconY + 12, 0xFFFF5555);
                }
            }

            // Hover-Effekt: Deferred Tooltip Queue! Kein Scroll-Hack mehr nötig!
            boolean hoverIcon = mouseX >= iconX && mouseX < iconX + 32 && mouseY >= iconY && mouseY < iconY + 32;
            if (hoverIcon && screen.isBackgroundActive()) {
        // Text: "§dIcon bearbeiten\n§8Klicke zum Ändern."
                screen.queueTooltip(net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_21"), mouseX, mouseY);
                graphics.fill(iconX + 1, iconY + 1, iconX + 31, iconY + 31, 0x33FFFFFF);
            }

        // Text: "Name:"
            graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_22").getString(), editorX + 50, propContentY + 8, 0xFFAAAAAA);
        // Text: "Typ:"
            graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_23").getString(), editorX + 50, propContentY + 25, 0xFFAAAAAA);
        // Text: "Fluch:"
            graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_24").getString(), editorX + 145, propContentY + 25, 0xFFAAAAAA);
        // Text: "MaxLvl:"
            graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_25").getString(), editorX + 10, propContentY + 49, 0xFFAAAAAA);
        // Text: "ReqLvl:"
            graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_26").getString(), editorX + 100, propContentY + 49, 0xFFAAAAAA);
        // Text: "Desc:"
            graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_27").getString(), editorX + 225, propContentY + 8, 0xFFAAAAAA);
            
        // Text: "Attr:"
            graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_28").getString(), editorX + 10, propContentY + 74, 0xFFAAAAAA);
        // Text: "➔  Op:"
            graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_29").getString(), editorX + 158, propContentY + 74, 0xFFAAAAAA);
        // Text: "➔  Factor:"
            graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runepropertiessection.text_30").getString(), editorX + 278, propContentY + 74, 0xFFAAAAAA);
        }

        if (isEditingIcon && iconModal != null) {
            iconModal.render(graphics, mouseX, mouseY, 0);
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int currentLeftWidth = screen.isLeftPanelOpen() ? StonesStudioScreen.LEFT_PANEL_WIDTH : 0;
        int editorX = currentLeftWidth + 20;
        int areaWidth = screen.width - editorX - 20;
        int propContentY = screen.getPropContentY();

        if (isEditingIcon && iconModal != null) {
            return true;
        }

        int propHeaderY = screen.getPropHeaderY();
        if (mouseX >= editorX && mouseX < editorX + areaWidth && mouseY >= propHeaderY && mouseY < propHeaderY + 12) {
            StonesStudioScreen.isPropertiesExpanded = !StonesStudioScreen.isPropertiesExpanded;
            screen.updateHeaderVisibility();
            return true;
        }

        if (StonesStudioScreen.isPropertiesExpanded) {
            int iconX = editorX + 10;
            int iconY = propContentY + 5;
            if (mouseX >= iconX && mouseX < iconX + 32 && mouseY >= iconY && mouseY < iconY + 32) {
                this.isEditingIcon = true;
                this.updateVisibility(editorX, propContentY, screen.height, true); 
                return true;
            }
        }
        return false;
    }
}