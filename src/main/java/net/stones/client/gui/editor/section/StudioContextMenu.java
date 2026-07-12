package net.stones.client.gui.editor.section;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import net.stones.client.gui.editor.TreeNode;
import net.stones.client.gui.editor.StudioSerializer;
import net.stones.client.gui.editor.StonesStudioScreen;
import net.stones.client.gui.editor.modal.ActionSelectionModal;
import net.stones.client.gui.editor.modal.ActionEditModal;
import net.stones.client.gui.editor.modal.DeleteConfirmationModal;
import net.stones.client.gui.editor.widget.StudioSuggestTextField;

/**
 * Kontextmenü für das Stones Studio.
 * Unterstützt vollumfängliche Node-Operationen: Kopieren, Einfügen, Löschen, 
 * Ausschneiden sowie ein systemweites Undo-/Redo-System.
 * * NEU: Der dedizierte Parameter-Schnelleditor verwendet nun das neue UniversalSuggestField,
 * um auch dort komfortabel Variablen mit dem $-Operator einzufügen.
 */
public class StudioContextMenu {
    public boolean isOpen = false;
    private int x, y;
    private TreeNode targetNode;
    private final List<String> options = new ArrayList<>();
    private final StonesStudioScreen screen;

    // Globale Clipboard-Daten
    private static TreeNode clipboardNode = null;
    
    // Globale Undo/Redo Verlaufsstacks (bis zu 50 snapshots)
    private static final List<String> undoStack = new ArrayList<>();
    private static final List<String> redoStack = new ArrayList<>();
    private static final int MAX_HISTORY = 50;

    public StudioContextMenu(StonesStudioScreen screen) {
        this.screen = screen;
    }

    /**
     * Speichert den aktuellen Zustand des Logikbaums im Undo-Stack.
     */
    public static void saveState() {
        prepareTreeForSaving(StonesStudioScreen.activeTree);
        JsonArray state = serializeTreeState(StonesStudioScreen.activeTree);
        undoStack.add(state.toString());
        redoStack.clear();
        if (undoStack.size() > MAX_HISTORY) {
            undoStack.remove(0);
        }
    }

    public static void undo() {
        if (!undoStack.isEmpty()) {
            prepareTreeForSaving(StonesStudioScreen.activeTree);
            JsonArray currentState = serializeTreeState(StonesStudioScreen.activeTree);
            redoStack.add(currentState.toString());
            
            String prevStateStr = undoStack.remove(undoStack.size() - 1);
            restoreTreeState(prevStateStr);
        }
    }

    public static void redo() {
        if (!redoStack.isEmpty()) {
            prepareTreeForSaving(StonesStudioScreen.activeTree);
            JsonArray currentState = serializeTreeState(StonesStudioScreen.activeTree);
            undoStack.add(currentState.toString());
            
            String nextStateStr = redoStack.remove(redoStack.size() - 1);
            restoreTreeState(nextStateStr);
        }
    }

    public void open(TreeNode node, int mouseX, int mouseY) {
        this.targetNode = node;
        this.x = mouseX;
        this.y = mouseY;
        this.isOpen = true;
        this.options.clear();

        if (node == null) {
            // Text: "Trigger (Ereignis) hinzufügen..."
            options.add(Component.translatable("gui.stones.studio.contextmenu.add_trigger").getString());
            if (clipboardNode != null && clipboardNode.type == TreeNode.Type.EVENT) {
                // Text: "Ereignis einfügen"
                options.add(Component.translatable("gui.stones.studio.contextmenu.paste_trigger").getString());
            }
            if (!undoStack.isEmpty()) {
                // Text: "Rückgängig (Undo)"
                options.add(Component.translatable("gui.stones.studio.contextmenu.undo").getString());
            }
            if (!redoStack.isEmpty()) {
                // Text: "Wiederholen (Redo)"
                options.add(Component.translatable("gui.stones.studio.contextmenu.redo").getString());
            }
        } 
        else if (node.type == TreeNode.Type.EVENT) {
            // Text: "Bedingung hinzufügen..."
            options.add(Component.translatable("gui.stones.studio.contextmenu.add_condition").getString());
            // Text: "Aktion hinzufügen..."
            options.add(Component.translatable("gui.stones.studio.contextmenu.add_action").getString());
            // Text: "Bearbeiten..."
            options.add(Component.translatable("gui.stones.studio.contextmenu.edit").getString());
            // Text: "Kopieren"
            options.add(Component.translatable("gui.stones.studio.contextmenu.copy").getString());
            // Text: "Ausschneiden"
            options.add(Component.translatable("gui.stones.studio.contextmenu.cut").getString());
            // Text: "Löschen"
            options.add(Component.translatable("gui.stones.studio.contextmenu.delete").getString());
            
            if (!undoStack.isEmpty()) {
                // Text: "Rückgängig (Undo)"
                options.add(Component.translatable("gui.stones.studio.contextmenu.undo").getString());
            }
            if (!redoStack.isEmpty()) {
                // Text: "Wiederholen (Redo)"
                options.add(Component.translatable("gui.stones.studio.contextmenu.redo").getString());
            }
        } 
        else if (node.type == TreeNode.Type.CATEGORY) {
            if (node.rawId.equals("CONDITIONS") || node.rawId.equals("CONDITION")) {
                // Text: "Bedingung hinzufügen..."
                options.add(Component.translatable("gui.stones.studio.contextmenu.add_condition").getString());
                if (clipboardNode != null && clipboardNode.type == TreeNode.Type.CONDITION) {
                    // Text: "Bedingung einfügen"
                    options.add(Component.translatable("gui.stones.studio.contextmenu.paste_condition").getString());
                }
            } else if (node.rawId.equals("ACTIONS") || node.rawId.equals("DEFAULT")) {
                // Text: "Aktion hinzufügen..."
                options.add(Component.translatable("gui.stones.studio.contextmenu.add_action").getString());
                if (clipboardNode != null && clipboardNode.type == TreeNode.Type.ACTION) {
                    // Text: "Aktion einfügen"
                    options.add(Component.translatable("gui.stones.studio.contextmenu.paste_action").getString());
                }
            } else if (node.rawId.equals("CASES")) {
                // Text: "Neuen Fall (Case) hinzufügen..."
                options.add(Component.translatable("gui.stones.studio.contextmenu.add_case").getString());
            } else if (node.rawId.equals("CASE")) {
                // Text: "Bearbeiten..."
                options.add(Component.translatable("gui.stones.studio.contextmenu.edit").getString());
                // Text: "Kopieren"
                options.add(Component.translatable("gui.stones.studio.contextmenu.copy").getString());
                // Text: "Ausschneiden"
                options.add(Component.translatable("gui.stones.studio.contextmenu.cut").getString());
                // Text: "Löschen"
                options.add(Component.translatable("gui.stones.studio.contextmenu.delete").getString());
            }
            
            if (!undoStack.isEmpty()) {
                // Text: "Rückgängig (Undo)"
                options.add(Component.translatable("gui.stones.studio.contextmenu.undo").getString());
            }
            if (!redoStack.isEmpty()) {
                // Text: "Wiederholen (Redo)"
                options.add(Component.translatable("gui.stones.studio.contextmenu.redo").getString());
            }
        } 
        else {
            // Text: "Bearbeiten..."
            options.add(Component.translatable("gui.stones.studio.contextmenu.edit").getString());
            // Text: "Kopieren"
            options.add(Component.translatable("gui.stones.studio.contextmenu.copy").getString());
            // Text: "Ausschneiden"
            options.add(Component.translatable("gui.stones.studio.contextmenu.cut").getString());
            // Text: "Löschen"
            options.add(Component.translatable("gui.stones.studio.contextmenu.delete").getString());
            
            addSpecificParametersMenu(node);

            if (clipboardNode != null && clipboardNode.type == node.type) {
                // Text: "Einfügen"
                options.add(Component.translatable("gui.stones.studio.contextmenu.paste").getString());
            }
            if (!undoStack.isEmpty()) {
                // Text: "Rückgängig (Undo)"
                options.add(Component.translatable("gui.stones.studio.contextmenu.undo").getString());
            }
            if (!redoStack.isEmpty()) {
                // Text: "Wiederholen (Redo)"
                options.add(Component.translatable("gui.stones.studio.contextmenu.redo").getString());
            }
        }

        int w = 180;
        int h = options.size() * 16 + 4;
        
        if (this.x + w > screen.width) {
            this.x = screen.width - w - 5;
        }
        if (this.y + h > screen.height) {
            this.y = screen.height - h - 5;
        }
        if (this.x < 0) this.x = 5;
        if (this.y < 0) this.y = 5;
    }

    private void addSpecificParametersMenu(TreeNode node) {
        if (node.jsonData != null && node.jsonData.has("type")) {
            String type = node.jsonData.get("type").getAsString();
            if (node.type == TreeNode.Type.ACTION) {
                switch (type) {
                    case "stones:play_sound" -> {
                        // Text: "Parameter: Sound-ID ändern..."
                        options.add(Component.translatable("gui.stones.studio.contextmenu.param.sound_id").getString());
                        // Text: "Parameter: Lautstärke ändern..."
                        options.add(Component.translatable("gui.stones.studio.contextmenu.param.volume").getString());
                        // Text: "Parameter: Tonhöhe ändern..."
                        options.add(Component.translatable("gui.stones.studio.contextmenu.param.pitch").getString());
                    }
                    case "stones:cooldown" -> {
                        // Text: "Parameter: Cooldown-Name ändern..."
                        options.add(Component.translatable("gui.stones.studio.contextmenu.param.cooldown_name").getString());
                        // Text: "Parameter: Ticks ändern..."
                        options.add(Component.translatable("gui.stones.studio.contextmenu.param.ticks").getString());
                    }
                    case "stones:apply_effect" -> {
                        // Text: "Parameter: Effekt-ID ändern..."
                        options.add(Component.translatable("gui.stones.studio.contextmenu.param.effect_id").getString());
                        // Text: "Parameter: Dauer ändern..."
                        options.add(Component.translatable("gui.stones.studio.contextmenu.param.duration").getString());
                        // Text: "Parameter: Verstärkung ändern..."
                        options.add(Component.translatable("gui.stones.studio.contextmenu.param.amplifier").getString());
                    }
                    case "stones:heal" -> {
                        // Text: "Parameter: Heilwert ändern..."
                        options.add(Component.translatable("gui.stones.studio.contextmenu.param.heal_amount").getString());
                    }
                    case "stones:delay" -> {
                        // Text: "Parameter: Verzögerungsticks ändern..."
                        options.add(Component.translatable("gui.stones.studio.contextmenu.param.delay_ticks").getString());
                    }
                }
            } else if (node.type == TreeNode.Type.CONDITION) {
                if (type.equals("stones:chance")) {
                    // Text: "Parameter: Wahrscheinlichkeit ändern..."
                    options.add(Component.translatable("gui.stones.studio.contextmenu.param.chance").getString());
                }
            }
        }
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        postProcessTree();

        if (!isOpen) return;
        
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500); 

        int w = 180;
        int h = options.size() * 16 + 4;
        graphics.fill(x, y, x + w, y + h, 0xFA1E1E1E);
        graphics.renderOutline(x, y, w, h, 0xFF444449);

        for (int i = 0; i < options.size(); i++) {
            int itemY = y + 2 + (i * 16);
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= itemY && mouseY < itemY + 16;
            
            if (hover) graphics.fill(x + 1, itemY, x + w - 1, itemY + 16, 0x33FFFFFF);
            
            String opt = options.get(i);
            
            // Verwende die Keys für die Farbgebung, um Sprachunabhängigkeit zu garantieren
            int color = 0xFFFFFFFF;
            if (opt.equals(Component.translatable("gui.stones.studio.contextmenu.delete").getString()) || 
                opt.equals(Component.translatable("gui.stones.studio.contextmenu.cut").getString())) {
                color = 0xFFFF5555;
            } else if (opt.equals(Component.translatable("gui.stones.studio.contextmenu.undo").getString()) || 
                       opt.equals(Component.translatable("gui.stones.studio.contextmenu.redo").getString()) || 
                       opt.startsWith(Component.translatable("gui.stones.studio.contextmenu.param_prefix").getString())) {
                color = 0xFFAAAAAA;
            }
            
            graphics.drawString(screen.getFont(), opt, x + 6, itemY + 4, color);
        }
        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isOpen) return false;
        
        int w = 180;
        int h = options.size() * 16 + 4;
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            int index = (int) ((mouseY - y - 2) / 16);
            if (index < 0 || index >= options.size()) return false;
            
            String option = options.get(index);

            // Vergleiche sicher über die Translation-Keys
            if (option.equals(Component.translatable("gui.stones.studio.contextmenu.undo").getString())) undo();
            else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.redo").getString())) redo();
            
            else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.add_trigger").getString())) {
                saveState();
                // Text: "Ereignis: ON_ATTACK"
                TreeNode evtNode = new TreeNode("⚡", Component.translatable("gui.stones.studio.contextmenu.event_on_attack").getString(), TreeNode.Type.EVENT, null);
                evtNode.rawId = "ON_ATTACK";
                // Text: "Bedingungen (Alle müssen erfüllt sein)"
                TreeNode condGroup = new TreeNode("🛡️", Component.translatable("gui.stones.studio.contextmenu.conditions_group").getString(), TreeNode.Type.CATEGORY, evtNode);
                condGroup.rawId = "CONDITIONS";
                // Text: "Aktionen (Nacheinander ausführen)"
                TreeNode actGroup = new TreeNode("🎬", Component.translatable("gui.stones.studio.contextmenu.actions_group").getString(), TreeNode.Type.CATEGORY, evtNode);
                actGroup.rawId = "ACTIONS";
                evtNode.addChild(condGroup);
                evtNode.addChild(actGroup);
                StonesStudioScreen.activeTree.add(evtNode);
            }
            else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.add_condition").getString())) {
                TreeNode parentCat = targetNode;
                if (targetNode.type == TreeNode.Type.EVENT) {
                    parentCat = targetNode.children.get(0);
                } else if (targetNode.type == TreeNode.Type.CATEGORY && targetNode.rawId.equals("CASE")) {
                    parentCat = targetNode.children.get(0);
                } else if (targetNode.type == TreeNode.Type.CATEGORY && (targetNode.rawId.equals("CONDITIONS") || targetNode.rawId.equals("CONDITION"))) {
                    parentCat = targetNode;
                }
                screen.openEditModal(new ActionSelectionModal(screen, parentCat, false));
            }
            else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.add_action").getString())) {
                TreeNode parentCat = targetNode;
                if (targetNode.type == TreeNode.Type.EVENT) {
                    parentCat = targetNode.children.get(1);
                } else if (targetNode.type == TreeNode.Type.CATEGORY && targetNode.rawId.equals("CASE")) {
                    parentCat = targetNode.children.get(1);
                } else if (targetNode.type == TreeNode.Type.CATEGORY && (targetNode.rawId.equals("ACTIONS") || targetNode.rawId.equals("DEFAULT"))) {
                    parentCat = targetNode;
                }
                screen.openEditModal(new ActionSelectionModal(screen, parentCat, true));
            }
            else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.add_case").getString())) {
                saveState();
                int idx = targetNode.children.size() + 1;
                // Text: "Fall {idx}"
                TreeNode caseNode = new TreeNode("🔍", Component.translatable("gui.stones.studio.contextmenu.case_prefix", idx).getString(), TreeNode.Type.CATEGORY, targetNode);
                caseNode.rawId = "CASE";
                targetNode.addChild(caseNode);

                // Text: "Bedingung"
                TreeNode caseCond = new TreeNode("🛡️", Component.translatable("gui.stones.studio.contextmenu.condition").getString(), TreeNode.Type.CATEGORY, caseNode);
                caseCond.rawId = "CONDITIONS"; 
                caseNode.addChild(caseCond);

                JsonObject cObj = new JsonObject();
                cObj.addProperty("type", "stones:chance");
                cObj.addProperty("value", 0.5f);
                TreeNode cNode = new TreeNode("✦", StudioSerializer.getReadableText(cObj, TreeNode.Type.CONDITION), TreeNode.Type.CONDITION, caseCond);
                cNode.jsonData = cObj;
                caseCond.addChild(cNode);

                // Text: "Aktionen (Fall)"
                TreeNode caseActions = new TreeNode("🎬", Component.translatable("gui.stones.studio.contextmenu.actions_case").getString(), TreeNode.Type.CATEGORY, caseNode);
                caseActions.rawId = "ACTIONS";
                caseNode.addChild(caseActions);
            }
            else if (option.startsWith(Component.translatable("gui.stones.studio.contextmenu.param_prefix").getString())) {
                handleParameterClick(option);
            }
            else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.edit").getString())) {
                screen.openEditModal(targetNode);
            } 
            else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.delete").getString())) {
                screen.openEditModal(new DeleteConfirmationModal(screen, targetNode));
            }
            else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.copy").getString())) {
                copyNodeToClipboard(targetNode);
            } 
            else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.cut").getString())) {
                saveState();
                copyNodeToClipboard(targetNode);
                if (targetNode.parent != null) targetNode.parent.children.remove(targetNode);
                else StonesStudioScreen.activeTree.remove(targetNode);
            }
            else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.paste_condition").getString()) ||
                     option.equals(Component.translatable("gui.stones.studio.contextmenu.paste_action").getString()) ||
                     option.equals(Component.translatable("gui.stones.studio.contextmenu.paste_trigger").getString()) ||
                     option.equals(Component.translatable("gui.stones.studio.contextmenu.paste").getString())) {
                if (clipboardNode != null) {
                    saveState();
                    if (targetNode == null) {
                        if (clipboardNode.type == TreeNode.Type.EVENT) {
                            StonesStudioScreen.activeTree.add(cloneTreeNode(clipboardNode, null));
                        }
                    } else if (targetNode.type == TreeNode.Type.CATEGORY) {
                        boolean compatible = false;
                        if ((targetNode.rawId.equals("CONDITIONS") || targetNode.rawId.equals("CONDITION")) && clipboardNode.type == TreeNode.Type.CONDITION) {
                            compatible = true;
                        } else if ((targetNode.rawId.equals("ACTIONS") || targetNode.rawId.equals("DEFAULT")) && clipboardNode.type == TreeNode.Type.ACTION) {
                            compatible = true;
                        } else if (targetNode.rawId.equals("CASES") && clipboardNode.type == TreeNode.Type.CATEGORY && clipboardNode.rawId.equals("CASE")) {
                            compatible = true;
                        }
                        if (compatible) {
                            targetNode.addChild(cloneTreeNode(clipboardNode, targetNode));
                        }
                    } else if (targetNode.type == TreeNode.Type.ACTION || targetNode.type == TreeNode.Type.CONDITION) {
                        TreeNode parent = targetNode.parent;
                        if (parent != null) {
                            int idx = parent.children.indexOf(targetNode);
                            parent.children.add(idx + 1, cloneTreeNode(clipboardNode, parent));
                        }
                    }
                }
            }

            isOpen = false;
            return true;
        }
        return false;
    }

    private void handleParameterClick(String option) {
        JsonObject json = targetNode.jsonData;
        if (option.equals(Component.translatable("gui.stones.studio.contextmenu.param.sound_id").getString())) {
            screen.openEditModal(new ActionParameterModal(screen, targetNode, "sound", 
                Component.translatable("gui.stones.studio.contextmenu.param.sound_id.title").getString(),
                Component.translatable("gui.stones.studio.contextmenu.param.sound_id.desc").getString(),
                json.has("sound") ? json.get("sound").getAsString() : "minecraft:entity.experience_orb.pickup", false));
        } else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.param.volume").getString())) {
            screen.openEditModal(new ActionParameterModal(screen, targetNode, "volume", 
                Component.translatable("gui.stones.studio.contextmenu.param.volume.title").getString(),
                Component.translatable("gui.stones.studio.contextmenu.param.volume.desc").getString(),
                json.has("volume") ? json.get("volume").getAsString() : "1.0", true));
        } else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.param.pitch").getString())) {
            screen.openEditModal(new ActionParameterModal(screen, targetNode, "pitch", 
                Component.translatable("gui.stones.studio.contextmenu.param.pitch.title").getString(),
                Component.translatable("gui.stones.studio.contextmenu.param.pitch.desc").getString(),
                json.has("pitch") ? json.get("pitch").getAsString() : "1.0", true));
        } else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.param.cooldown_name").getString())) {
            screen.openEditModal(new ActionParameterModal(screen, targetNode, "name", 
                Component.translatable("gui.stones.studio.contextmenu.param.cooldown_name.title").getString(),
                Component.translatable("gui.stones.studio.contextmenu.param.cooldown_name.desc").getString(),
                json.has("name") ? json.get("name").getAsString() : "pyro_shot", false));
        } else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.param.ticks").getString())) {
            screen.openEditModal(new ActionParameterModal(screen, targetNode, "ticks", 
                Component.translatable("gui.stones.studio.contextmenu.param.ticks.title").getString(),
                Component.translatable("gui.stones.studio.contextmenu.param.ticks.desc").getString(),
                json.has("ticks") ? json.get("ticks").getAsString() : "100", true));
        } else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.param.effect_id").getString())) {
            screen.openEditModal(new ActionParameterModal(screen, targetNode, "effect", 
                Component.translatable("gui.stones.studio.contextmenu.param.effect_id.title").getString(),
                Component.translatable("gui.stones.studio.contextmenu.param.effect_id.desc").getString(),
                json.has("effect") ? json.get("effect").getAsString() : "minecraft:speed", false));
        } else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.param.duration").getString())) {
            screen.openEditModal(new ActionParameterModal(screen, targetNode, "duration", 
                Component.translatable("gui.stones.studio.contextmenu.param.duration.title").getString(),
                Component.translatable("gui.stones.studio.contextmenu.param.duration.desc").getString(),
                json.has("duration") ? json.get("duration").getAsString() : "100", true));
        } else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.param.amplifier").getString())) {
            screen.openEditModal(new ActionParameterModal(screen, targetNode, "amplifier", 
                Component.translatable("gui.stones.studio.contextmenu.param.amplifier.title").getString(),
                Component.translatable("gui.stones.studio.contextmenu.param.amplifier.desc").getString(),
                json.has("amplifier") ? json.get("amplifier").getAsString() : "0", true));
        } else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.param.heal_amount").getString())) {
            screen.openEditModal(new ActionParameterModal(screen, targetNode, "amount", 
                Component.translatable("gui.stones.studio.contextmenu.param.heal_amount.title").getString(),
                Component.translatable("gui.stones.studio.contextmenu.param.heal_amount.desc").getString(),
                json.has("amount") ? json.get("amount").getAsString() : "4.0", true));
        } else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.param.delay_ticks").getString())) {
            screen.openEditModal(new ActionParameterModal(screen, targetNode, "ticks", 
                Component.translatable("gui.stones.studio.contextmenu.param.delay_ticks.title").getString(),
                Component.translatable("gui.stones.studio.contextmenu.param.delay_ticks.desc").getString(),
                json.has("ticks") ? json.get("ticks").getAsString() : "20", true));
        } else if (option.equals(Component.translatable("gui.stones.studio.contextmenu.param.chance").getString())) {
            screen.openEditModal(new ActionParameterModal(screen, targetNode, "value", 
                Component.translatable("gui.stones.studio.contextmenu.param.chance.title").getString(),
                Component.translatable("gui.stones.studio.contextmenu.param.chance.desc").getString(),
                json.has("value") ? json.get("value").getAsString() : "0.5", true));
        }
    }

    private static void copyNodeToClipboard(TreeNode src) {
        clipboardNode = cloneTreeNode(src, null);
    }

    private static TreeNode cloneTreeNode(TreeNode src, TreeNode parent) {
        TreeNode dest = new TreeNode(src.icon, src.readableText, src.type, parent);
        dest.rawId = src.rawId;
        dest.isExpanded = src.isExpanded;
        if (src.jsonData != null) {
            dest.jsonData = src.jsonData.deepCopy();
        }
        for (TreeNode child : src.children) {
            dest.addChild(cloneTreeNode(child, dest));
        }
        return dest;
    }

    public static void prepareTreeForSaving(List<TreeNode> tree) {
        StudioSerializer.prepareTreeForSaving(tree);
    }

    public static void postProcessTree() {
        StudioSerializer.postProcessTree(StonesStudioScreen.activeTree);
    }

    private static void postProcessNode(TreeNode node) {
        StudioSerializer.postProcessNode(node);
    }
    
    private static JsonArray serializeTreeState(List<TreeNode> tree) {
        return StudioSerializer.serializeTreeState(tree);
    }

    private static void restoreTreeState(String stateStr) {
        StudioSerializer.restoreTreeState(stateStr, StonesStudioScreen.activeTree);
    }

    // =========================================================================
    // DEDIZIERTER SUB-EDITOR-DIALOG FÜR PARAMETER
    // =========================================================================
    public static class ActionParameterModal extends ActionEditModal {
        private final TreeNode node;
        private final StonesStudioScreen screen;
        private final String key;
        private final String title;
        private final String explanation;
        private final boolean isNumeric;
        
        // Nutzt jetzt UniversalSuggestField, um Variablen via $ autovervollständigen zu können
        private final StudioSuggestTextField.UniversalSuggestField inputField; 
        private final net.minecraft.client.gui.components.Button btnOk;
        private final net.minecraft.client.gui.components.Button btnCancel;

        public ActionParameterModal(StonesStudioScreen screen, TreeNode node, String key, String title, String explanation, String defaultValue, boolean isNumeric) {
            super(screen, node);
            this.screen = screen;
            this.node = node;
            this.key = key;
            this.title = title;
            this.explanation = explanation;
            this.isNumeric = isNumeric;

            int cx = screen.width / 2;
            int cy = screen.height / 2;

            // Text: "Value"
            this.inputField = new StudioSuggestTextField.UniversalSuggestField(screen, screen.getFont(), cx - 110, cy + 5, 220, 16, Component.translatable("gui.stones.studio.contextmenu.value"));
            this.inputField.setContextNode(node); // Kontext übergeben, um Trigger-Variablen auszulesen
            this.inputField.setMaxLength(256);
            this.inputField.setValue(defaultValue);
            this.inputField.setFocused(true);

            // Text: "Übernehmen"
            this.btnOk = net.minecraft.client.gui.components.Button.builder(Component.translatable("gui.stones.studio.contextmenu.apply"), b -> {
                StudioContextMenu.saveState();
                String val = this.inputField.getValue().trim();
                
                // Falls es ein $-Variablenwert ist, parsen wir ihn nicht als Zahl!
                if (isNumeric && !val.startsWith("$")) {
                    try {
                        if (val.contains(".")) {
                            node.jsonData.addProperty(key, Double.parseDouble(val));
                        } else {
                            node.jsonData.addProperty(key, Integer.parseInt(val));
                        }
                    } catch (Exception e) {
                        node.jsonData.addProperty(key, val);
                    }
                } else {
                    node.jsonData.addProperty(key, val);
                }
                node.readableText = StudioSerializer.getReadableText(node.jsonData, node.type);
                screen.closeModal();
            }).bounds(cx - 105, cy + 40, 100, 20).build();

            // Text: "Abbrechen"
            this.btnCancel = net.minecraft.client.gui.components.Button.builder(Component.translatable("gui.stones.studio.contextmenu.cancel"), b -> screen.closeModal())
                    .bounds(cx + 5, cy + 40, 100, 20).build();
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 300);
            
            graphics.fill(0, 0, screen.width, screen.height, 0x88000000);
            
            int width = 280;
            int height = 150;
            int x = (screen.width - width) / 2;
            int y = (screen.height - height) / 2;

            graphics.fill(x, y, x + width, y + height, 0xFF18181B);
            graphics.renderOutline(x, y, width, height, 0xFF444449);
            
            graphics.drawString(screen.getFont(), title, x + 10, y + 10, 0xFFFFAA00);
            graphics.fill(x + 10, y + 22, x + width - 10, y + 23, 0xFF333333);
            
            // Text: "§7" + explanation
            List<net.minecraft.util.FormattedCharSequence> lines = screen.getFont().split(Component.literal("§7" + explanation), width - 30);
            int currentY = y + 28;
            for (net.minecraft.util.FormattedCharSequence line : lines) {
                graphics.drawString(screen.getFont(), line, x + 15, currentY, 0xFFAAAAAA);
                currentY += 10;
            }

            inputField.render(graphics, mouseX, mouseY, partialTick);
            btnOk.render(graphics, mouseX, mouseY, partialTick);
            btnCancel.render(graphics, mouseX, mouseY, partialTick);
            
            graphics.pose().popPose();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (inputField.mouseClicked(mouseX, mouseY, button)) return true;
            if (btnOk.mouseClicked(mouseX, mouseY, button)) return true;
            if (btnCancel.mouseClicked(mouseX, mouseY, button)) return true;
            return true;
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            return inputField.charTyped(codePoint, modifiers);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 256) { screen.closeModal(); return true; }
            return inputField.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}