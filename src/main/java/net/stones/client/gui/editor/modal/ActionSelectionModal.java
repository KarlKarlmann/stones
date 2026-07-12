package net.stones.client.gui.editor.modal;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import net.stones.client.gui.editor.TreeNode;
import net.stones.client.gui.editor.StudioSerializer;
import net.stones.client.gui.editor.StonesStudioScreen;
import net.stones.client.gui.editor.section.StudioContextMenu;

public class ActionSelectionModal extends ActionEditModal {

    private final TreeNode parentCategory;
    private final boolean isAction;
    
    private final List<TypeEntry> availableTypes = new ArrayList<>();
    private int selectedIndex = 0;

    private final int width = 360;
    private final int height = 180;

    private record TypeEntry(String id, String displayName, String icon, String description, JsonObject defaultJson) {}

    public ActionSelectionModal(StonesStudioScreen screen, TreeNode parentCategory, boolean isAction) {
        super(screen, parentCategory); 
        this.parentCategory = parentCategory;
        this.isAction = isAction;

        int cx = screen.width / 2;
        int cy = screen.height / 2;

        if (isAction) {
            populateActions();
        } else {
            populateConditions();
        }

        this.btnSave.setX((screen.width / 2) - 110);
        this.btnSave.setY((screen.height / 2) + 60);
        // Text: "Hinzufügen"
        this.btnSave.setMessage(Component.translatable("gui.stones.studio.actionselection.button.add"));

        this.btnCancel.setX((screen.width / 2) + 10);
        this.btnCancel.setY((screen.height / 2) + 60);
    }

    private void populateActions() {
        // add_combo
        JsonObject addCombo = new JsonObject();
        addCombo.addProperty("type", "stones:add_combo");
        addCombo.addProperty("id", "my_combo");
        addCombo.addProperty("value", 1.0);
        addCombo.addProperty("max", 5.0);
        addCombo.addProperty("timeout", 100);
        availableTypes.add(new TypeEntry("stones:add_combo", 
            Component.translatable("gui.stones.studio.actionselection.action.add_combo.name").getString(), "⚔️", 
            Component.translatable("gui.stones.studio.actionselection.action.add_combo.desc").getString(), addCombo));

        // play_sound
        JsonObject sound = new JsonObject();
        sound.addProperty("type", "stones:play_sound");
        sound.addProperty("sound", "minecraft:entity.experience_orb.pickup");
        sound.addProperty("volume", 1.0);
        sound.addProperty("pitch", 1.0);
        availableTypes.add(new TypeEntry("stones:play_sound", 
            Component.translatable("gui.stones.studio.actionselection.action.play_sound.name").getString(), "🔊", 
            Component.translatable("gui.stones.studio.actionselection.action.play_sound.desc").getString(), sound));

        // cooldown
        JsonObject cd = new JsonObject();
        cd.addProperty("type", "stones:cooldown");
        cd.addProperty("name", "pyro_shot");
        cd.addProperty("ticks", 100);
        availableTypes.add(new TypeEntry("stones:cooldown", 
            Component.translatable("gui.stones.studio.actionselection.action.cooldown.name").getString(), "⏳", 
            Component.translatable("gui.stones.studio.actionselection.action.cooldown.desc").getString(), cd));

        // apply_effect
        JsonObject fx = new JsonObject();
        fx.addProperty("type", "stones:apply_effect");
        fx.addProperty("effect", "minecraft:speed");
        fx.addProperty("duration", 100);
        fx.addProperty("amplifier", 0);
        availableTypes.add(new TypeEntry("stones:apply_effect", 
            Component.translatable("gui.stones.studio.actionselection.action.apply_effect.name").getString(), "🧪", 
            Component.translatable("gui.stones.studio.actionselection.action.apply_effect.desc").getString(), fx));

        // heal
        JsonObject heal = new JsonObject();
        heal.addProperty("type", "stones:heal");
        heal.addProperty("amount", 4.0);
        availableTypes.add(new TypeEntry("stones:heal", 
            Component.translatable("gui.stones.studio.actionselection.action.heal.name").getString(), "❤️", 
            Component.translatable("gui.stones.studio.actionselection.action.heal.desc").getString(), heal));

        // delay
        JsonObject delay = new JsonObject();
        delay.addProperty("type", "stones:delay");
        delay.addProperty("ticks", 20);
        availableTypes.add(new TypeEntry("stones:delay", 
            Component.translatable("gui.stones.studio.actionselection.action.delay.name").getString(), "⏰", 
            Component.translatable("gui.stones.studio.actionselection.action.delay.desc").getString(), delay));

        // case
        JsonObject caseObj = new JsonObject();
        caseObj.addProperty("type", "stones:case");
        availableTypes.add(new TypeEntry("stones:case", 
            Component.translatable("gui.stones.studio.actionselection.action.case.name").getString(), "📁", 
            Component.translatable("gui.stones.studio.actionselection.action.case.desc").getString(), caseObj));

        // invoke
        JsonObject invoke = new JsonObject();
        invoke.addProperty("type", "stones:invoke");
        invoke.addProperty("call", "player.getLookAngle()");
        availableTypes.add(new TypeEntry("stones:invoke", 
            Component.translatable("gui.stones.studio.actionselection.action.invoke.name").getString(), "⚡", 
            Component.translatable("gui.stones.studio.actionselection.action.invoke.desc").getString(), invoke));
    }

    private void populateConditions() {
        // chance
        JsonObject chance = new JsonObject();
        chance.addProperty("type", "stones:chance");
        chance.addProperty("value", 0.5);
        availableTypes.add(new TypeEntry("stones:chance", 
            Component.translatable("gui.stones.studio.actionselection.condition.chance.name").getString(), "🎲", 
            Component.translatable("gui.stones.studio.actionselection.condition.chance.desc").getString(), chance));

        // is_ready
        JsonObject ready = new JsonObject();
        ready.addProperty("type", "stones:is_ready");
        ready.addProperty("name", "pyro_shot");
        availableTypes.add(new TypeEntry("stones:is_ready", 
            Component.translatable("gui.stones.studio.actionselection.condition.is_ready.name").getString(), "⌛", 
            Component.translatable("gui.stones.studio.actionselection.condition.is_ready.desc").getString(), ready));

        // variable_compare
        JsonObject compare = new JsonObject();
        compare.addProperty("type", "stones:variable_compare");
        compare.addProperty("variable", "RuneLevel");
        compare.addProperty("operator", ">=");
        compare.addProperty("value", 5.0);
        availableTypes.add(new TypeEntry("stones:variable_compare", 
            Component.translatable("gui.stones.studio.actionselection.condition.variable_compare.name").getString(), "📊", 
            Component.translatable("gui.stones.studio.actionselection.condition.variable_compare.desc").getString(), compare));

        // has_air
        JsonObject air = new JsonObject();
        air.addProperty("type", "stones:has_air");
        air.addProperty("min", 100);
        availableTypes.add(new TypeEntry("stones:has_air", 
            Component.translatable("gui.stones.studio.actionselection.condition.has_air.name").getString(), "🫧", 
            Component.translatable("gui.stones.studio.actionselection.condition.has_air.desc").getString(), air));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 300);

        graphics.fill(0, 0, screen.width, screen.height, 0x88000000);

        int x = (screen.width - width) / 2;
        int y = (screen.height - height) / 2;

        graphics.fill(x, y, x + width, y + height, 0xFF18181B);
        graphics.renderOutline(x, y, width, height, 0xFF444449);

        String titleText = isAction ? Component.translatable("gui.stones.studio.actionselection.title.action").getString() : Component.translatable("gui.stones.studio.actionselection.title.condition").getString();
        
        graphics.drawString(screen.getFont(), "➕ " + titleText, x + 10, y + 10, 0xFFFFAA00);
        graphics.fill(x + 10, y + 22, x + width - 10, y + 23, 0xFF333333);

        int listX = x + 10;
        int listY = y + 28;
        int listW = 140;
        int listH = height - 64;

        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF111113);
        graphics.renderOutline(listX, listY, listW, listH, 0xFF2D2D31);

        for (int i = 0; i < availableTypes.size(); i++) {
            TypeEntry entry = availableTypes.get(i);
            int entryY = listY + 2 + (i * 15);
            if (entryY + 14 > listY + listH) break; 

            boolean isHovered = mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= entryY && mouseY < entryY + 14;
            boolean isSelected = (i == selectedIndex);

            if (isSelected) {
                graphics.fill(listX + 2, entryY, listX + listW - 2, entryY + 14, 0x33FFAA00);
            } else if (isHovered) {
                graphics.fill(listX + 2, entryY, listX + listW - 2, entryY + 14, 0x15FFFFFF);
            }

            int color = isSelected ? 0xFFFFAA00 : 0xFFCCCCCC;
            graphics.drawString(screen.getFont(), entry.icon + " " + entry.displayName, listX + 6, entryY + 3, color);
        }

        int detailsX = x + 160;
        int detailsY = y + 28;
        int detailsW = width - 170;

        if (selectedIndex >= 0 && selectedIndex < availableTypes.size()) {
            TypeEntry selected = availableTypes.get(selectedIndex);
            
            graphics.drawString(screen.getFont(), selected.icon + " " + selected.displayName, detailsX, detailsY, 0xFFFFFFFF);
            graphics.fill(detailsX, detailsY + 12, detailsX + detailsW, detailsY + 13, 0xFF2D2D31);

            // FIX: Repariertes Text-Splitting
            List<FormattedCharSequence> descLines = screen.getFont().split(Component.literal("§7" + selected.description), detailsW);
            int textY = detailsY + 20;
            for (FormattedCharSequence line : descLines) {
                if (textY + 10 > y + height - 40) break; 
                graphics.drawString(screen.getFont(), line, detailsX, textY, 0xFFAAAAAA);
                textY += 10;
            }

            // FIX: Reparierte Präfix-Lokalisierung
            graphics.drawString(screen.getFont(), Component.translatable("gui.stones.studio.actionselection.type_prefix").getString() + selected.id, detailsX, y + height - 44, 0xFF555555);
        }

        btnSave.render(graphics, mouseX, mouseY, partialTick);
        btnCancel.render(graphics, mouseX, mouseY, partialTick);

        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (screen.width - width) / 2;
        int y = (screen.height - height) / 2;

        int listX = x + 10;
        int listY = y + 28;
        int listW = 140;
        int listH = height - 64;

        if (mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= listY && mouseY < listY + listH && button == 0) {
            int clickedIdx = (int) ((mouseY - listY - 2) / 15);
            if (clickedIdx >= 0 && clickedIdx < availableTypes.size()) {
                if (clickedIdx == selectedIndex) {
                    addSelectedNode();
                } else {
                    selectedIndex = clickedIdx;
                }
            }
            return true;
        }

        if (btnSave.mouseClicked(mouseX, mouseY, button)) {
            addSelectedNode();
            return true;
        }
        if (btnCancel.mouseClicked(mouseX, mouseY, button)) {
            screen.closeModal();
            return true;
        }

        return true; 
    }

    private void addSelectedNode() {
        if (selectedIndex >= 0 && selectedIndex < availableTypes.size()) {
            StudioContextMenu.saveState();

            TypeEntry selected = availableTypes.get(selectedIndex);
            JsonObject config = selected.defaultJson.deepCopy();
            
            TreeNode.Type nodeType = isAction ? TreeNode.Type.ACTION : TreeNode.Type.CONDITION;
            String icon = isAction ? "!" : "✦";
            if (selected.id.equals("stones:delay")) icon = "⏳";
            else if (selected.id.equals("stones:case")) icon = "📁";
            else if (selected.id.equals("stones:add_combo")) icon = "⚔️";

            TreeNode newNode = new TreeNode(icon, StudioSerializer.getReadableText(config, nodeType), nodeType, parentCategory);
            newNode.jsonData = config;
            
            parentCategory.addChild(newNode);
            
            StudioContextMenu.postProcessTree();
            screen.closeModal();
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { 
            screen.closeModal();
            return true;
        }
        if (keyCode == 264) { 
            if (selectedIndex < availableTypes.size() - 1) {
                selectedIndex++;
            }
            return true;
        }
        if (keyCode == 265) { 
            if (selectedIndex > 0) {
                selectedIndex--;
            }
            return true;
        }
        if (keyCode == 258 || keyCode == 257) { 
            addSelectedNode();
            return true;
        }
        return false;
    }
}