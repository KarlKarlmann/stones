package net.stones.client.gui.editor.section;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.stones.client.gui.editor.TreeNode;
import net.stones.client.gui.editor.StonesStudioScreen;
import net.stones.client.gui.editor.section.StudioContextMenu;

/**
 * Sub-Renderer für das visuelle Logik-Tree-System im Stones Studio.
 * Nutzt das vom StonesStudioScreen vorgegebene globale mainScrollY.
 * Bietet hierarchische Verbindungslinien und einen flüssigen Drag & Drop Workflow.
 * * AKTUALISIERT: Holt sich die Event-Trigger nun dynamisch per Reflection aus der
 * echten 'TriggerType' Registry der Mod! Dadurch werden auch Addon-Mod Trigger automatisch unterstützt.
 */
public class BehaviorTreeRenderer {

    private final StonesStudioScreen screen;
    private TreeNode draggedNode = null;
    
    // Für die Unterscheidung von Klick (Bearbeiten) und Ziehen (Drag & Drop)
    private double dragStartX, dragStartY;
    private TreeNode clickedNodeOnPress = null;
    
    // Cached Trigger-Array aus der Registry
    private static String[] cachedTriggers = null;
    
    // Optisches Farbschema
    private static final int COLOR_EVENT = 0xFFFFAA00;     // Orange für Events
    private static final int COLOR_CATEGORY = 0xFF5555FF;  // Blau für Kategorien
    private static final int COLOR_CONDITION = 0xFF55FF55; // Grün für Bedingungen
    private static final int COLOR_ACTION = 0xFFFF5555;    // Rot für Aktionen
    private static final int COLOR_GUIDELINE = 0x33FFFFFF;  // Subtile Führungslinien
    private static final int COLOR_HOVER_BG = 0x15FFFFFF;   // Sanfter Hintergrund beim Hovern
    private static final int COLOR_PLUS_BUTTON = 0xFF55FF55;// Neongrün für den Schnell-Add-Button
    private static final int COLOR_PLUS_HOVER = 0xFF00FF00;

    public BehaviorTreeRenderer(StonesStudioScreen screen) {
        this.screen = screen;
    }

    public void resetScroll() {}
    
    public TreeNode getDraggedNode() { return draggedNode; }
    public void setDraggedNode(TreeNode node) { this.draggedNode = node; }

    /**
     * Holt die Trigger-Liste dynamisch aus der TriggerType Registry der Mod.
     * Nutzt Reflection für Zero-Touch-Kompilierung und bietet ein sicheres Fallback.
     */
    private static String[] getRegisteredTriggers() {
        if (cachedTriggers == null) {
            try {
                // Wir holen uns die Klasse und das private REGISTRY-Feld per Reflection
                Class<?> triggerTypeClass = Class.forName("net.stones.enchantment.behavior.TriggerType");
                java.lang.reflect.Field registryField = triggerTypeClass.getDeclaredField("REGISTRY");
                registryField.setAccessible(true);
                
                @SuppressWarnings("unchecked")
                Map<String, ?> registry = (Map<String, ?>) registryField.get(null);
                if (registry != null && !registry.isEmpty()) {
                    cachedTriggers = registry.keySet().toArray(new String[0]);
                }
            } catch (Exception e) {
                // Sicheres, exaktes Fallback auf deine registrierten Core-Trigger, falls der Classpath abweicht
                cachedTriggers = new String[]{
                    "ON_ATTACK", "ON_HURT", "ON_KILL", "ON_SWING", "ON_TICK", 
                    "ON_BLOCK_BREAK", "ON_PROJECTILE_HIT", "ON_JUMP", "ON_ACTION_BUTTON"
                };
            }
        }
        return cachedTriggers;
    }

    public void render(GuiGraphics graphics, int editorX, int treeStartY, int mouseX, int mouseY) {
        Font font = screen.getFont();
        if (font == null) return;

        // Header des Logik-Editors zeichnen (Bereinigt)
        // Text: "▼ ⚡ Trigger Logik-Editor"
        graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.behaviortreerenderer.text_01").getString(), editorX, treeStartY, 0xFF5555FF);
        graphics.fill(editorX, treeStartY + 12, screen.width - 20, treeStartY + 13, 0xFF333333); 

        // Initialer Y-Startwert unterhalb des Headers
        int currentY = treeStartY + 20;
        
        // Liste für die aktiven Pfade der Verbindungslinien
        List<Boolean> verticalLineMap = new ArrayList<>();
        
        // Rendere alle Root-Events im Baum
        for (int i = 0; i < StonesStudioScreen.activeTree.size(); i++) {
            TreeNode node = StonesStudioScreen.activeTree.get(i);
            boolean isLast = (i == StonesStudioScreen.activeTree.size() - 1);
            currentY = renderNodeTree(graphics, node, editorX + 5, currentY, mouseX, mouseY, 0, verticalLineMap, isLast);
        }
        
        // Rendering für das aktuell gezogene Element (Drag and Drop)
        if (draggedNode != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 400); 
            graphics.fill(mouseX + 10, mouseY - 5, mouseX + 250, mouseY + 11, 0x88000000);
            graphics.drawString(font, draggedNode.icon + " " + draggedNode.readableText, mouseX + 15, mouseY - 2, 0xFFAAAAAA);
            graphics.pose().popPose();
        }
    }

    /**
     * Rekursive Rendering-Methode für den gesamten logischen Baum.
     */
    private int renderNodeTree(GuiGraphics graphics, TreeNode node, int x, int y, int mouseX, int mouseY, 
                               int depth, List<Boolean> verticalLineMap, boolean isLastChild) {
        Font font = screen.getFont();
        if (font == null) return y;

        int nodeHeight = 16;
        node.hitboxY = y; 
        node.hitboxX = x + (depth * 14);
        node.hitboxW = screen.width - 40 - node.hitboxX;

        // Hover-Abfrage über die Zeilenbreite hinweg
        boolean isHovered = screen.isBackgroundActive() && 
                            mouseX >= node.hitboxX && mouseX <= node.hitboxX + node.hitboxW && 
                            mouseY >= node.hitboxY && mouseY < node.hitboxY + nodeHeight;

        // Hintergrund-Banding bei Hover zeichnen
        if (isHovered) {
            graphics.fill(node.hitboxX - 2, y - 2, node.hitboxX + node.hitboxW, y + 12, COLOR_HOVER_BG);
        }

        // Drag & Drop Ziel-Vorschau
        if (draggedNode != null && isHovered && draggedNode.type == node.type) {
            graphics.fill(node.hitboxX, y + 12, node.hitboxX + node.hitboxW, y + 13, 0xFF55FF55); 
        }

        // Verbindungslinien zeichnen
        drawHierarchicalLines(graphics, node.hitboxX, y, depth, verticalLineMap, isLastChild);

        // Icon-Farbe basierend auf dem Typ bestimmen
        int color = 0xFFFFFFFF;
        if (node.type == TreeNode.Type.EVENT) color = COLOR_EVENT;
        else if (node.type == TreeNode.Type.CATEGORY) color = COLOR_CATEGORY;
        else if (node.type == TreeNode.Type.CONDITION) color = COLOR_CONDITION;
        else if (node.type == TreeNode.Type.ACTION) color = COLOR_ACTION;

        // Ordner-Indikator
        String prefix = "";
        if (!node.children.isEmpty()) {
            prefix = node.isExpanded ? "▼ " : "▶ ";
        } else if (node.type == TreeNode.Type.CONDITION || node.type == TreeNode.Type.ACTION) {
            prefix = "• ";
        }

        // =========================================================================
        // RENDERING FÜR TRIGGER: Klassischer, grauer Minecraft Button!
        // =========================================================================
        boolean hoverTrigger = false;
        if (node.type == TreeNode.Type.EVENT) {
            String baseText = prefix + node.icon + " Ereignis: ";
            int baseWidth = font.width(baseText);
            graphics.drawString(font, baseText, node.hitboxX + 4, y, COLOR_EVENT);

            String triggerText = node.rawId;
            int triggerWidth = font.width(triggerText);
            int triggerX = node.hitboxX + 4 + baseWidth;
            
            // Abmessungen für den klassischen Grauen Minecraft-Button
            int btnPaddingX = 6;
            int btnW = triggerWidth + btnPaddingX * 2;
            int btnH = 14;
            int btnY = y - 1;

            hoverTrigger = screen.isBackgroundActive() && 
                           mouseX >= triggerX && mouseX < triggerX + btnW && 
                           mouseY >= y && mouseY < y + nodeHeight;

            // 1. Hintergrund zeichnen (Minecraft dunkelgrau, hellgrau bei Hover)
            graphics.fill(triggerX, btnY, triggerX + btnW, btnY + btnH, hoverTrigger ? 0xFF7A7A7D : 0xFF5A5A5D);

            // 2. Äußerer schwarzer Rahmen
            graphics.renderOutline(triggerX, btnY, btnW, btnH, 0xFF000000);

            // 3. Plastische 3D-Kanten zeichnen (Oben/Links hell, Unten/Rechts dunkel)
            graphics.fill(triggerX + 1, btnY + 1, triggerX + btnW - 1, btnY + 2, hoverTrigger ? 0xFF9E9E9E : 0xFF8E8E8E); // Oben hell
            graphics.fill(triggerX + 1, btnY + 1, triggerX + 2, btnY + btnH - 1, hoverTrigger ? 0xFF9E9E9E : 0xFF8E8E8E); // Links hell
            graphics.fill(triggerX + 1, btnY + btnH - 2, triggerX + btnW - 1, btnY + btnH - 1, hoverTrigger ? 0xFF3E3E40 : 0xFF2E2E30); // Unten schatten
            graphics.fill(triggerX + btnW - 2, btnY + 1, triggerX + btnW - 1, btnY + btnH - 1, hoverTrigger ? 0xFF3E3E40 : 0xFF2E2E30); // Rechts schatten

            // 4. Text zeichnen (Weiß / Hellgelb bei Hover)
            int buttonTextColor = hoverTrigger ? 0xFFFFFF90 : 0xFFE0E0E0;
            graphics.drawString(font, triggerText, triggerX + btnPaddingX, y + 2, buttonTextColor);

            if (hoverTrigger && draggedNode == null) {
        // Text: "§aKlicken, um zum nächsten Trigger durchzuschalten."
                screen.queueTooltip(net.minecraft.network.chat.Component.translatable("gui.stones.studio.behaviortreerenderer.text_02"), mouseX, mouseY);
            }
        } else {
            String textToDraw = prefix + node.icon + " " + node.readableText;
            graphics.drawString(font, textToDraw, node.hitboxX + 4, y, color);
        }

        // Schnell-Hinzufügen Button [+]
        boolean showPlusButton = (node.type == TreeNode.Type.CATEGORY || node.type == TreeNode.Type.EVENT);
        int plusBtnX = node.hitboxX + node.hitboxW - 16;
        boolean hoverPlus = false;
        
        if (showPlusButton && isHovered && draggedNode == null && !hoverTrigger) {
            hoverPlus = mouseX >= plusBtnX - 4 && mouseX <= plusBtnX + 24 && mouseY >= y && mouseY < y + 16;
            int btnColor = hoverPlus ? COLOR_PLUS_HOVER : COLOR_PLUS_BUTTON;
        // Text: "[+]"
            graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.behaviortreerenderer.text_03").getString(), plusBtnX, y, btnColor);
            
            // Tooltip via Deferred Queue einreihen
            if (hoverPlus) {
                Component tooltipMsg = node.type == TreeNode.Type.EVENT ? 
        // Text: "§aNeue Bedingung oder Aktion zu diesem Trigger hinzufügen."
                    net.minecraft.network.chat.Component.translatable("gui.stones.studio.behaviortreerenderer.text_04") :
        // Text: "§aNeues Element in dieser Kategorie erstellen."
                    net.minecraft.network.chat.Component.translatable("gui.stones.studio.behaviortreerenderer.text_05");
                screen.queueTooltip(tooltipMsg, mouseX, mouseY);
            }
        }

        // Struktur-Erklärungen als Hover-Tooltips (DAU- und Prost-Sicherung)
        if (isHovered && draggedNode == null && !hoverPlus && !hoverTrigger) {
            if (node.type == TreeNode.Type.CATEGORY) {
                if (node.rawId.equals("DEFAULT")) {
        // Text: "§6Standard-Aktionen (Default) (Else)\n§7Verhält sich wie der §e'Else' (Ansonsten)§7-Zweig.\n\n§c⚠️ Wichtig: §7Wird §cNIEMALS §7ausgeführt, wenn zuvor einer der Fälle (If-Weichen) oben zutraf!\n\nAktionen, die §eIMMER §7passieren sollen (egal ob ein Fall zutrifft oder nicht), gehören §eunter/außerhalb §7der gesamten 'stones:case' Weiche platziert."
                    screen.queueTooltip(net.minecraft.network.chat.Component.translatable("gui.stones.studio.behaviortreerenderer.text_06"), mouseX, mouseY);
                } else if (node.rawId.equals("CASES")) {
        // Text: "§6Fälle (Cases) (If)\n§7Hier liegen deine 'Wenn... Dann...'-Weichen.\n\n§7Die Bedingungen werden von oben nach unten geprüft. Sobald §eeine §7Bedingung zutrifft (If), werden ihre Aktionen ausgeführt und der gesamte Block wird sofort beendet. Nur wenn §cKEIN §7einziger Fall zutrifft, wird am Ende 'Default (Else)' ausgeführt."
                    screen.queueTooltip(net.minecraft.network.chat.Component.translatable("gui.stones.studio.behaviortreerenderer.text_07"), mouseX, mouseY);
                } else if (node.rawId.equals("ACTIONS") && node.parent != null && node.parent.jsonData != null && node.parent.jsonData.has("type") && "stones:delay".equals(node.parent.jsonData.get("type").getAsString())) {
        // Text: "§6Verzögerte Ausführung\n§7Alle hier platzierten Aktionen werden nach Ablauf der oben definierten Wartezeit (Ticks) ausgeführt."
                    screen.queueTooltip(net.minecraft.network.chat.Component.translatable("gui.stones.studio.behaviortreerenderer.text_08"), mouseX, mouseY);
                }
            }
        }

        y += nodeHeight;

        // Rekursion für die Kinder-Knoten
        if (node.isExpanded && !node.children.isEmpty()) {
            List<Boolean> nextLineMap = new ArrayList<>(verticalLineMap);
            if (depth < nextLineMap.size()) {
                nextLineMap.set(depth, !isLastChild);
            } else {
                nextLineMap.add(!isLastChild);
            }

            for (int i = 0; i < node.children.size(); i++) {
                TreeNode child = node.children.get(i);
                boolean isLast = (i == node.children.size() - 1);
                y = renderNodeTree(graphics, child, x, y, mouseX, mouseY, depth + 1, nextLineMap, isLast);
            }
        }
        return y;
    }

    /**
     * Zeichnet die hierarchischen Verbindungslinien.
     */
    private void drawHierarchicalLines(GuiGraphics graphics, int nodeX, int y, int depth, List<Boolean> lineMap, boolean isLast) {
        if (depth <= 0) return;

        for (int i = 0; i < depth - 1; i++) {
            if (i < lineMap.size() && lineMap.get(i)) {
                int lineX = nodeX - ((depth - i) * 14) + 8;
                graphics.fill(lineX, y - 4, lineX + 1, y + 12, COLOR_GUIDELINE);
            }
        }

        int localLineX = nodeX - 6;
        int verticalLineBottom = isLast ? y + 2 : y + 12;
        graphics.fill(localLineX, y - 4, localLineX + 1, verticalLineBottom, COLOR_GUIDELINE);
        graphics.fill(localLineX, y + 2, localLineX + 8, y + 3, COLOR_GUIDELINE);
    }

    /**
     * Prüft, ob ein gezogenes Element mit dem anvisierten Ordner (Kategorie) kompatibel ist.
     */
    private boolean isCompatibleWithCategory(TreeNode node, TreeNode category) {
        if (category == null || category.type != TreeNode.Type.CATEGORY) return false;
        
        if (node.type == TreeNode.Type.ACTION) {
            return category.rawId.equals("ACTIONS") || category.rawId.equals("DEFAULT");
        }
        if (node.type == TreeNode.Type.CONDITION) {
            return category.rawId.equals("CONDITIONS") || category.rawId.equals("CONDITION");
        }
        return false;
    }

    /**
     * Fängt Klicks ab. Erhält scrolledMouseY für Hitboxen und screenY (absolut unscrolled) für das Kontextmenü-Placement.
     */
    public boolean handleMouseClick(double mouseX, double mouseY, double screenY, int button) {
        TreeNode clickedNode = findClickedNode(StonesStudioScreen.activeTree, mouseX, mouseY);
        
        if (clickedNode != null) {
            // Linksklick-Abfang auf dem Ereignis-Button (Minecraft gray button)
            if (clickedNode.type == TreeNode.Type.EVENT && button == 0) {
                Font font = screen.getFont();
                String prefix = !clickedNode.children.isEmpty() ? (clickedNode.isExpanded ? "▼ " : "▶ ") : "";
                String baseText = prefix + clickedNode.icon + " Ereignis: ";
                int baseWidth = font.width(baseText);
                int triggerX = clickedNode.hitboxX + 4 + baseWidth;
                
                String triggerText = clickedNode.rawId;
                int triggerWidth = font.width(triggerText);
                int btnPaddingX = 6;
                int btnW = triggerWidth + btnPaddingX * 2;

                if (mouseX >= triggerX && mouseX < triggerX + btnW) {
                    // Weiche sichern & die echten, registrierten Trigger holen!
                    StudioContextMenu.saveState();
                    String[] triggers = getRegisteredTriggers();
                    
                    int currentIndex = -1;
                    for (int i = 0; i < triggers.length; i++) {
                        if (triggers[i].equalsIgnoreCase(clickedNode.rawId)) {
                            currentIndex = i;
                            break;
                        }
                    }
                    int nextIndex = (currentIndex + 1) % triggers.length;
                    clickedNode.rawId = triggers[nextIndex];
                    clickedNode.readableText = "Ereignis: " + triggers[nextIndex];
                    return true;
                }
            }

            if (button == 1) { 
                // Rechter Mausklick öffnet das kontextbezogene Menü punktgenau an unscrolled screenY!
                screen.openContextMenu(clickedNode, (int)mouseX, (int)screenY);
                return true;
            } else if (button == 0) { 
                // Linker Mausklick auf dem verbleibenden Ast
                if (!clickedNode.children.isEmpty() && mouseX < clickedNode.hitboxX + 15) {
                    clickedNode.isExpanded = !clickedNode.isExpanded;
                } else {
                    if (clickedNode.type == TreeNode.Type.ACTION || clickedNode.type == TreeNode.Type.CONDITION) {
                        draggedNode = clickedNode;
                        dragStartX = mouseX;
                        dragStartY = mouseY;
                        clickedNodeOnPress = clickedNode;
                    } else if (clickedNode.type != TreeNode.Type.CATEGORY && clickedNode.type != TreeNode.Type.EVENT) {
                        screen.openEditModal(clickedNode);
                    }
                }
                return true;
            }
        } else {
            // Rechtsklick auf freien Hintergrund
            if (button == 1) {
                screen.openContextMenu(null, (int)mouseX, (int)screenY);
                return true;
            }
        }
        return false;
    }

    public boolean handleMouseRelease(double mouseX, double mouseY, int button) {
        if (draggedNode != null && button == 0) {
            double deltaX = Math.abs(mouseX - dragStartX);
            double deltaY = Math.abs(mouseY - dragStartY);
            
            // Bei minimaler Mausbewegung interpretieren wir es als Klick zum Bearbeiten!
            if (deltaX < 3.0 && deltaY < 3.0 && draggedNode == clickedNodeOnPress) {
                screen.openEditModal(draggedNode);
            } else {
                TreeNode target = findClickedNode(StonesStudioScreen.activeTree, mouseX, mouseY);
                if (target != null && target != draggedNode) {
                    // Sibling-Reordering (Ziel ist vom selben Typ und hat einen kompatiblen Elterknoten)
                    if (target.type == draggedNode.type && target.parent != null && isCompatibleWithCategory(draggedNode, target.parent)) {
                        TreeNode parent = target.parent;
                        
                        // Aus altem Parent entfernen
                        if (draggedNode.parent != null) {
                            draggedNode.parent.children.remove(draggedNode);
                        } else {
                            StonesStudioScreen.activeTree.remove(draggedNode);
                        }
                        
                        int targetIndex = parent.children.indexOf(target);
                        draggedNode.parent = parent;
                        
                        // Obere oder untere Hälfte der Hitbox bestimmen
                        boolean dropOnTopHalf = mouseY < (target.hitboxY + 8);
                        if (dropOnTopHalf) {
                            parent.children.add(targetIndex, draggedNode);
                        } else {
                            parent.children.add(targetIndex + 1, draggedNode);
                        }
                    } 
                    // Ziehen direkt auf einen kompatiblen leeren/vollen Ordner (Category)
                    else if (isCompatibleWithCategory(draggedNode, target)) {
                        // Aus altem Parent entfernen
                        if (draggedNode.parent != null) {
                            draggedNode.parent.children.remove(draggedNode);
                        } else {
                            StonesStudioScreen.activeTree.remove(draggedNode);
                        }
                        draggedNode.parent = target;
                        target.children.add(0, draggedNode); // Füge ganz oben ein
                    }
                }
            }
            draggedNode = null;
            clickedNodeOnPress = null;
            return true;
        }
        return false;
    }

    public void handleScroll(double delta) {}

    public TreeNode findClickedNode(List<TreeNode> nodes, double mouseX, double mouseY) {
        for (TreeNode node : nodes) {
            if (node == draggedNode) continue;
            
            if (mouseY >= node.hitboxY && mouseY < node.hitboxY + 16 && mouseX >= node.hitboxX && mouseX <= node.hitboxX + node.hitboxW) {
                return node;
            }
            if (node.isExpanded && !node.children.isEmpty()) {
                TreeNode found = findClickedNode(node.children, mouseX, mouseY);
                if (found != null) return found;
            }
        }
        return null;
    }
}