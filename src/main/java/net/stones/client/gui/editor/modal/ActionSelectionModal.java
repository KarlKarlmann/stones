package net.stones.client.gui.editor.modal;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import net.stones.client.gui.editor.TreeNode;
import net.stones.client.gui.editor.StonesStudioScreen;
import net.stones.client.gui.editor.section.StudioContextMenu;
import net.stones.client.gui.editor.StudioSerializer;
public class ActionSelectionModal extends ActionEditModal {

    private final TreeNode parentCategory;
    private final boolean isAction;
    
    private final List<TypeEntry> availableTypes = new ArrayList<>();
    private int selectedIndex = 0;
    private int scrollOffset = 0;

    private final int width = 380;
    private final int height = 200;

    private record TypeEntry(String id, Component displayName, String icon, Component description, JsonObject defaultJson) {}

    public ActionSelectionModal(StonesStudioScreen screen, TreeNode parentCategory, boolean isAction) {
        super(screen, parentCategory); 
        this.parentCategory = parentCategory;
        this.isAction = isAction;

        if (isAction) {
            populateActions();
        } else {
            populateConditions();
        }

        this.btnSave.setX((screen.width / 2) - 110);
        this.btnSave.setY((screen.height / 2) + 70);
        this.btnSave.setMessage(Component.translatable("gui.stones.studio.actionselection.button.add"));

        this.btnCancel.setX((screen.width / 2) + 10);
        this.btnCancel.setY((screen.height / 2) + 70);
    }

    private void populateActions() {
        // COMBOS & STATS
        addType("stones:add_combo", "⚔️", json("type", "stones:add_combo", "id", "my_combo", "value", 1.0, "max", 5.0, "timeout", 100));
        addType("stones:update_combo", "📊", json("type", "stones:update_combo", "id", "$runeId", "count", 1, "max", 5));
        addType("stones:get_combo", "🔍", json("type", "stones:get_combo", "id", "my_combo", "into", "combo_val"));

        // VARIABLEN & NBT
        addType("stones:set_variable", "📝", json("type", "stones:set_variable", "name", "my_var", "value", "1.0"));
        addType("stones:get_persistent_var", "💾", json("type", "stones:get_persistent_var", "name", "my_data", "into", "temp_var"));
        addType("stones:set_persistent_var", "💾", json("type", "stones:set_persistent_var", "name", "my_data", "value", "1.0"));
        addType("stones:get_attribute", "🧬", json("type", "stones:get_attribute", "attribute", "minecraft:generic.max_health", "into", "attr_val"));

        // SCHADEN & HEILUNG
        addType("stones:modify_damage", "💥", json("type", "stones:modify_damage", "multiplier", 1.0, "add", 0.0));
        addType("stones:heal", "❤️", json("type", "stones:heal", "amount", 4.0));
        addType("stones:cancel", "🚫", json("type", "stones:cancel"));

        // LOGIK & KONTROLLE
        addType("stones:cooldown", "⏳", json("type", "stones:cooldown", "name", "$runeId", "ticks", 100));
        addType("stones:delay", "⏰", json("type", "stones:delay", "ticks", 20));
        addType("stones:case", "📁", json("type", "stones:case"));
        addType("stones:for_each", "🔄", json("type", "stones:for_each", "from", "$found_blocks", "as", "pos"));
        addType("stones:math", "🧮", json("type", "stones:math", "variable", "my_var", "operation", "add", "value", 1.0));
        addType("stones:random", "🎲", json("type", "stones:random", "min", 0.0, "max", 1.0, "into", "roll"));

        // WORLD & ENTITIES
        addType("stones:explode", "💣", json("type", "stones:explode", "radius", 3.0, "fire", "false"));
        addType("stones:add_velocity", "🚀", json("type", "stones:add_velocity", "x", 0.0, "y", 1.0, "z", 0.0, "scale", 1.0));
        addType("stones:set_block", "🧱", json("type", "stones:set_block", "block", "minecraft:air"));
        addType("stones:find_blocks", "🔎", json("type", "stones:find_blocks", "radius", 5.0, "save_to", "found_blocks"));
        addType("stones:marker", "📍", json("type", "stones:marker", "mode", "point", "size", 1.0, "duration", 100));
		addType("stones:read_nbt", "📦", json("type", "stones:read_nbt", "target", "$player", "path", "ForgeCaps.\"stones:shrine_link\".pos.X", "save_to", "shrine_x"));

        // EFFECTS & SOUNDS
        addType("stones:apply_effect", "🧪", json("type", "stones:apply_effect", "effect", "minecraft:speed", "duration", 100, "amplifier", 0));
        addType("stones:play_sound", "🔊", json("type", "stones:play_sound", "sound", "minecraft:entity.experience_orb.pickup", "volume", 1.0, "pitch", 1.0));
        addType("stones:spawn_particles", "✨", json("type", "stones:spawn_particles", "count", 10, "particle", "minecraft:flame", "spread", 0.2, "speed", 0.0));
        addType("stones:particle_orbit", "🌀", json("type", "stones:particle_orbit", "count", 1.0, "particle", "minecraft:flame"));

        // ADVANCED & REFLECTION
        addType("stones:invoke", "⚡", json("type", "stones:invoke", "call", "player.getLookAngle()"));
        addType("stones:set_field", "⚙️", json("type", "stones:set_field", "target", "$player", "field", "hurtTime", "value", "0"));
        addType("stones:new", "📦", json("type", "stones:new", "class", "net.minecraft.world.phys.Vec3", "save_to", "my_vec"));
        addType("stones:command", "💻", json("type", "stones:command", "command", "say Hello"));
        addType("stones:remove_random_enchantment", "🗡️", json("type", "stones:remove_random_enchantment", "save_level_to", "sacrificed_lvl"));
    }

    private void populateConditions() {
        addType("stones:chance", "🎲", json("type", "stones:chance", "value", 0.5));
        addType("stones:is_ready", "⌛", json("type", "stones:is_ready", "name", "pyro_shot"));
        addType("stones:health_below", "❤️", json("type", "stones:health_below", "percent", 0.5));
        addType("stones:variable_compare", "📊", json("type", "stones:variable_compare", "variable", "RuneLevel", "operator", ">=", "value", 5.0));
        addType("stones:persistent_var_compare", "💾", json("type", "stones:persistent_var_compare", "name", "my_data", "operator", ">", "value", 0.0));
        addType("stones:has_air", "🫧", json("type", "stones:has_air", "min", 100));
        addType("stones:block_check", "🧱", json("type", "stones:block_check", "block", "minecraft:stone"));
        addType("stones:is_raining", "🌧️", json("type", "stones:is_raining"));
        addType("stones:is_thundering", "⛈️", json("type", "stones:is_thundering"));
        addType("stones:is_on_fire", "🔥", json("type", "stones:is_on_fire"));
        addType("stones:is_day", "☀️", json("type", "stones:is_day"));
    }

    private void addType(String id, String icon, JsonObject defaultJson) {
        String path = id.replace("stones:", "");
        String prefix = isAction ? "gui.stones.studio.actionselection.action." : "gui.stones.studio.actionselection.condition.";
        Component name = Component.translatable(prefix + path + ".name");
        Component desc = Component.translatable(prefix + path + ".desc");
        availableTypes.add(new TypeEntry(id, name, icon, desc, defaultJson));
    }

    private JsonObject json(Object... pairs) {
        JsonObject obj = new JsonObject();
        for (int i = 0; i < pairs.length; i += 2) {
            String k = pairs[i].toString();
            Object v = pairs[i + 1];
            if (v instanceof Number n) obj.addProperty(k, n);
            else if (v instanceof Boolean b) obj.addProperty(k, b);
            else obj.addProperty(k, v.toString());
        }
        return obj;
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

        Component titleText = Component.literal("➕ ").append(
            Component.translatable(isAction ? "gui.stones.studio.actionselection.title.action" : "gui.stones.studio.actionselection.title.condition")
        );
        graphics.drawString(screen.getFont(), titleText, x + 10, y + 10, 0xFFFFAA00);
        graphics.fill(x + 10, y + 22, x + width - 10, y + 23, 0xFF333333);

        int listX = x + 10;
        int listY = y + 28;
        int listW = 150;
        int listH = height - 70;

        graphics.fill(listX, listY, listX + listW, listY + listH, 0xFF111113);
        graphics.renderOutline(listX, listY, listW, listH, 0xFF2D2D31);

        graphics.enableScissor(listX, listY, listX + listW, listY + listH);

        for (int i = 0; i < availableTypes.size(); i++) {
            TypeEntry entry = availableTypes.get(i);
            int entryY = listY + 2 + ((i - scrollOffset) * 15);

            if (entryY + 14 < listY || entryY > listY + listH) continue;

            boolean isHovered = mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= entryY && mouseY < entryY + 14;
            boolean isSelected = (i == selectedIndex);

            if (isSelected) {
                graphics.fill(listX + 2, entryY, listX + listW - 2, entryY + 14, 0x33FFAA00);
            } else if (isHovered) {
                graphics.fill(listX + 2, entryY, listX + listW - 2, entryY + 14, 0x15FFFFFF);
            }

            int color = isSelected ? 0xFFFFAA00 : 0xFFCCCCCC;
            Component itemText = Component.literal(entry.icon + " ").append(entry.displayName);
            graphics.drawString(screen.getFont(), itemText, listX + 6, entryY + 3, color);
        }

        graphics.disableScissor();

        int visibleCount = listH / 15;
        if (availableTypes.size() > visibleCount) {
            int scrollbarH = Math.max(10, (listH * visibleCount) / availableTypes.size());
            int maxScroll = availableTypes.size() - visibleCount;
            int scrollbarY = listY + ((listH - scrollbarH) * scrollOffset) / maxScroll;
            graphics.fill(listX + listW - 3, scrollbarY, listX + listW - 1, scrollbarY + scrollbarH, 0xAAFFFFFF);
        }

        int detailsX = x + 170;
        int detailsY = y + 28;
        int detailsW = width - 180;

        if (selectedIndex >= 0 && selectedIndex < availableTypes.size()) {
            TypeEntry selected = availableTypes.get(selectedIndex);
            
            Component header = Component.literal(selected.icon + " ").append(selected.displayName);
            graphics.drawString(screen.getFont(), header, detailsX, detailsY, 0xFFFFFFFF);
            graphics.fill(detailsX, detailsY + 12, detailsX + detailsW, detailsY + 13, 0xFF2D2D31);

            List<FormattedCharSequence> descLines = screen.getFont().split(selected.description, detailsW);
            int textY = detailsY + 20;
            for (FormattedCharSequence line : descLines) {
                if (textY + 10 > y + height - 40) break; 
                graphics.drawString(screen.getFont(), line, detailsX, textY, 0xFFAAAAAA);
                textY += 10;
            }

            Component typePrefix = Component.translatable("gui.stones.studio.actionselection.type_prefix").append(selected.id);
            graphics.drawString(screen.getFont(), typePrefix, detailsX, y + height - 44, 0xFF555555);
        }

        btnSave.render(graphics, mouseX, mouseY, partialTick);
        btnCancel.render(graphics, mouseX, mouseY, partialTick);

        graphics.pose().popPose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int listH = height - 70;
        int visibleCount = listH / 15;
        int maxScroll = Math.max(0, availableTypes.size() - visibleCount);

        if (delta > 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (delta < 0) {
            scrollOffset = Math.min(maxScroll, scrollOffset + 1);
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (screen.width - width) / 2;
        int y = (screen.height - height) / 2;

        int listX = x + 10;
        int listY = y + 28;
        int listW = 150;
        int listH = height - 70;

        if (mouseX >= listX + 2 && mouseX <= listX + listW - 2 && mouseY >= listY && mouseY < listY + listH && button == 0) {
            int clickedIdx = (int) ((mouseY - listY - 2) / 15) + scrollOffset;
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
                adjustScrollForSelection();
            }
            return true;
        }
        if (keyCode == 265) { 
            if (selectedIndex > 0) {
                selectedIndex--;
                adjustScrollForSelection();
            }
            return true;
        }
        if (keyCode == 258 || keyCode == 257) { 
            addSelectedNode();
            return true;
        }
        return false;
    }

    private void adjustScrollForSelection() {
        int listH = height - 70;
        int visibleCount = listH / 15;
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + visibleCount) {
            scrollOffset = selectedIndex - visibleCount + 1;
        }
    }
}