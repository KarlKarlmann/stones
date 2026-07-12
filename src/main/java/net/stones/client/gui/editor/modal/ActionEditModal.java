package net.stones.client.gui.editor.modal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.stones.client.gui.editor.StonesStudioScreen;
import net.stones.client.gui.editor.TreeNode;
import net.stones.client.gui.editor.StudioSerializer;
import net.stones.client.gui.editor.section.StudioContextMenu;
import net.stones.client.gui.editor.widget.StudioMultiLineEditBox;
import net.stones.client.gui.editor.widget.StudioSuggestTextField;
import net.stones.client.gui.editor.widget.StudioTextField;

import java.util.ArrayList;
import java.util.List;

/**
 * Eigenschaften-Modal zum Bearbeiten der logischen Trigger-Knoten im Stones Studio.
 * Verwendet das "Warcraft 3 Trigger Editor" UI-Layout für fließende Sätze mit eingebetteten Werten.
 * * AKTUALISIERT: Behebt Code-Abschnitte und fügt add_combo, get_combo sowie Target-Auswahl für update_combo hinzu.
 */
public class ActionEditModal extends AbstractStudioModal {
    protected final TreeNode targetNode;
    protected Button btnSave;
    protected Button btnCancel;

    protected StudioMultiLineEditBox jsonEditor;
    protected SentenceLayoutHelper sentenceLayout;

    protected String actionTitle = Component.translatable("gui.stones.studio.actionedit.title.default").getString();

    public ActionEditModal(StonesStudioScreen screen, TreeNode node) {
        // Text: "Eigenschaften bearbeiten"
        super(screen, net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_01"), 500, 200); 
        this.targetNode = node;
        this.init(); 
    }

    @Override
    protected void initFields(int startX, int startY) {
        sentenceLayout = new SentenceLayoutHelper(startX + 15, startY + 45, startX + width - 15);
        buildSentenceUI(sentenceLayout);

        if (!sentenceLayout.usesFallback) {
            this.height = (sentenceLayout.currentY - startY) + 50;
            this.title = Component.literal(actionTitle);
            
            // Text: "Übernehmen"
            btnSave = addModalWidget(Button.builder(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_02"), b -> {
                StudioContextMenu.saveState();
                for (Runnable hook : sentenceLayout.saveHooks) {
                    hook.run(); 
                }
                targetNode.readableText = StudioSerializer.getReadableText(targetNode.jsonData, targetNode.type);
                screen.closeModal();
            }).bounds(startX + (width / 2) - 105, startY + height - 28, 100, 20).build());

            // Text: "Abbrechen"
            btnCancel = addModalWidget(Button.builder(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_03"), b -> screen.closeModal())
                    .bounds(startX + (width / 2) + 5, startY + height - 28, 100, 20).build());
        } else {
            for (Object child : new ArrayList<>(this.children)) {
                this.children.remove(child); 
            }
            
            this.width = 450; this.height = 200;
            
            // Text: "JSON"
            // Text: "JSON"
            jsonEditor = addModalWidget(new StudioMultiLineEditBox(screen, font, startX + 15, startY + 40, width - 30, 110, net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_04"), net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_05")));
            jsonEditor.setValue(targetNode.jsonData.toString());

            // Text: "Übernehmen"
            btnSave = addModalWidget(Button.builder(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_06"), b -> {
                StudioContextMenu.saveState();
                try {
                    targetNode.jsonData = JsonParser.parseString(jsonEditor.getValue()).getAsJsonObject();
                    targetNode.readableText = StudioSerializer.getReadableText(targetNode.jsonData, targetNode.type);
                } catch (Exception ignored) {} 
                screen.closeModal();
            }).bounds(startX + (width / 2) - 105, startY + height - 30, 100, 20).build());
            
            // Text: "Abbrechen"
            btnCancel = addModalWidget(Button.builder(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_07"), b -> screen.closeModal())
                    .bounds(startX + (width / 2) + 5, startY + height - 30, 100, 20).build());
        }
    }

    private void buildSentenceUI(SentenceLayoutHelper layout) {
        String type = targetNode.jsonData.has("type") ? targetNode.jsonData.get("type").getAsString() : "";
        
        switch (type) {
            // ================== A K T I O N E N ==================
            case "stones:add_combo" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.add_combo").getString();
                // Text: "Erhöht die Kombo"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_08").getString());
                layout.addInput("id", 80, "my_combo", false);
                // Text: "auf Ziel"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_09").getString());
                layout.addInput("target", 80, "$player", false);
                // Text: "um"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_10").getString());
                layout.addInput("value", 30, "1.0", true);
                // Text: "Punkte. Max:"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_11").getString());
                layout.addInput("max", 30, "5.0", true);
                layout.nextLine();
                // Text: "Behalte bei Entladung"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_12").getString());
                layout.addInput("retained", 30, "0.0", true);
                // Text: "Punkte. Ablaufzeit:"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_13").getString());
                layout.addInput("timeout", 40, "100", true);
                // Text: "Ticks."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_14").getString());
                layout.nextLine();
                // Text: "Textur:"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_15").getString());
                layout.addInput("texture", 120, "minecraft:textures/particle/glint.png", false);
                // Text: "Größe:"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_16").getString());
                layout.addInput("size", 30, "0.4", true);
                // Text: "Radius:"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_17").getString());
                layout.addInput("radius", 30, "1.2", true);
                layout.nextLine();
                // Text: "Tempo:"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_18").getString());
                layout.addInput("speed", 30, "0.1", true);
                // Text: "Farbe:"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_19").getString());
                layout.addInput("color", 50, "#FFFFFF", false);
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_20").getString());
                layout.nextLine();
                // Text: "(Tipp: Aktionen für on_add/on_max können im JSON Tab bearbeitet werden)"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_21").getString());
            }
            case "stones:get_combo" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.get_combo").getString();
                // Text: "Frage die Combo"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_22").getString());
                layout.addInput("id", 80, "my_combo", false);
                // Text: "von Ziel"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_23").getString());
                layout.addInput("target", 80, "$target", false);
                layout.nextLine();
                // Text: "ab und speichere den Datensatz in"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_24").getString());
                layout.addVariableByNameInput("into", 80, "my_combo");
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_25").getString());
            }
            case "stones:delay" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.delay").getString();
                // Text: "Warte exakt"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_26").getString());
                layout.addInput("ticks", 40, "20", true);
                // Text: "Spiel-Ticks (20 Ticks = 1s), bevor die nachfolgenden"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_27").getString());
                layout.nextLine();
                // Text: "Aktionen in diesem verzögerten Block ausgeführt werden."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_28").getString());
            }
            case "stones:update_combo" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.update_combo").getString();
                // Text: "Setze das Kombo-Display mit der ID"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_29").getString());
                layout.addInput("id", 80, "$runeId", false);
                // Text: "auf Ziel"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_30").getString());
                layout.addInput("target", 80, "$player", false);
                layout.nextLine();
                // Text: "auf den Zähler"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_31").getString());
                layout.addInput("count", 30, "1", true);
                // Text: "von Maximal"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_32").getString());
                layout.addInput("max", 30, "5", true);
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_33").getString());
                layout.nextLine();
                // Text: "Verwende Textur"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_34").getString());
                layout.addInput("texture", 150, "minecraft:textures/particle/glint.png", false);
                // Text: "mit Größe"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_35").getString());
                layout.addInput("size", 30, "0.4", true);
                // Text: "im Radius"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_36").getString());
                layout.addInput("radius", 30, "1.2", true);
                layout.nextLine();
                // Text: "Das Rotationstempo ist"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_37").getString());
                layout.addInput("speed", 40, "0.1", true);
                // Text: ", die Farbe (Hex) ist"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_38").getString());
                layout.addInput("color", 60, "#FFFFFF", false);
                // Text: "und sie erlischt nach"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_39").getString());
                layout.addInput("timeout", 40, "100", true);
                // Text: "Ticks."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_40").getString());
            }
            case "stones:get_persistent_var" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.get_persistent_var").getString();
                // Text: "Lese den permanenten NBT-Wert mit dem Namen"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_41").getString());
                layout.addInput("name", 80, "my_data", false);
                layout.nextLine();
                // Text: "aus dem Spieler aus und speichere ihn für dieses Event in die Variable"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_42").getString());
                layout.addVariableByNameInput("into", 60, "temp_var"); 
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_43").getString());
            }
            case "stones:set_persistent_var" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.set_persistent_var").getString();
                // Text: "Speichere den Wert"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_44").getString());
                layout.addInput("value", 60, "1.0", false);
                // Text: "permanent im Spieler-NBT unter dem Namen"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_45").getString());
                layout.addInput("name", 80, "my_data", false);
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_46").getString());
            }
            case "stones:get_attribute" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.get_attribute").getString();
                // Text: "Lese das Minecraft-Attribut"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_47").getString());
                layout.addSuggestInput("attribute", 160, "minecraft:generic.max_health", 1);
                layout.nextLine();
                // Text: "des Spielers aus und speichere den Wert in die Variable"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_48").getString());
                layout.addVariableByNameInput("into", 60, "attr_val"); 
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_49").getString());
            }
            case "stones:random" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.random").getString();
                // Text: "Generiere eine Zufallszahl zwischen"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_50").getString());
                layout.addInput("min", 40, "0.0", true);
                // Text: "und"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_51").getString());
                layout.addInput("max", 40, "1.0", true);
                layout.nextLine();
                // Text: "und speichere das Ergebnis in die Variable"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_52").getString());
                layout.addVariableByNameInput("into", 60, "roll"); 
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_53").getString());
            }
            case "stones:add_velocity" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.add_velocity").getString();
                // Text: "Schleudere den Spieler in die Richtung  X:"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_54").getString());
                layout.addInput("x", 30, "0.0", true);
                // Text: " Y:"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_55").getString());
                layout.addInput("y", 30, "1.0", true);
                // Text: " Z:"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_56").getString());
                layout.addInput("z", 30, "0.0", true);
                layout.nextLine();
                // Text: "Multipliziere die Stärke dieses Impulses mit der Skalierung"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_57").getString());
                layout.addInput("scale", 40, "1.0", true);
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_58").getString());
            }
            case "stones:invoke" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.invoke").getString();
                // Text: "Führe über Reflection die Methode"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_59").getString());
                layout.addInput("call", 180, "player.getHealth()", false);
                // Text: "aus"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_60").getString());
                layout.nextLine();
                // Text: "und speichere das Ergebnis (optional) in die Variable"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_61").getString());
                layout.addVariableByNameInput("save_result_to", 60, ""); 
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_62").getString());
            }
            case "stones:set_field" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.set_field").getString();
                // Text: "Setze im Ziel-Objekt"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_63").getString());
                layout.addInput("target", 80, "$player", false);
                // Text: "die Eigenschaft (Field)"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_64").getString());
                layout.addInput("field", 80, "hurtTime", false);
                layout.nextLine();
                // Text: "auf den neuen Wert"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_65").getString());
                layout.addInput("value", 80, "0", false);
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_66").getString());
            }
            case "stones:new" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.new").getString();
                // Text: "Instanziiere die Klasse"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_67").getString());
                layout.addInput("class", 180, "net.minecraft.world.phys.Vec3", false);
                layout.nextLine();
                // Text: "und speichere das neue Objekt in die Variable"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_68").getString());
                layout.addVariableByNameInput("save_to", 80, "my_vec"); 
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_69").getString());
                layout.nextLine();
                // Text: "(Tipp: Argumente können aktuell nur im JSON Tab per Array definiert werden)."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_70").getString());
            }
            case "stones:modify_damage" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.modify_damage").getString();
                // Text: "Modifiziere den ursprünglichen Schaden dieses Events:"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_71").getString());
                layout.nextLine();
                // Text: "Multipliziere ihn mit"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_72").getString());
                layout.addInput("multiplier", 40, "1.0", true);
                // Text: "und addiere danach noch"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_73").getString());
                layout.addInput("add", 40, "0.0", true);
                // Text: "Bonusschaden hinzu."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_74").getString());
            }
            case "stones:heal" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.heal").getString();
                // Text: "Heile den Spieler um"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_75").getString());
                layout.addInput("amount", 40, "0.0", true);
                // Text: "Lebenspunkte (halbe Herzen),"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_76").getString());
                layout.nextLine();
                // Text: "ODER um"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_77").getString());
                layout.addInput("percent_of_max_health", 40, "0.0", true);
                // Text: "% seiner maximalen Lebenspunkte,"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_78").getString());
                layout.nextLine();
                // Text: "ODER um"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_79").getString());
                layout.addInput("percent_of_damage", 40, "0.0", true);
                // Text: "% des in diesem Event verursachten Schadens."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_80").getString());
            }
            case "stones:apply_effect" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.apply_effect").getString();
                // Text: "Verleihe dem Spieler den Statuseffekt"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_81").getString());
                layout.addSuggestInput("effect", 140, "minecraft:speed", 4);
                layout.nextLine();
                // Text: "für eine Dauer von"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_82").getString());
                layout.addInput("duration", 40, "100", true);
                // Text: "Ticks auf Verstärkungsstufe"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_83").getString());
                layout.addInput("amplifier", 30, "0", true);
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_84").getString());
            }
            case "stones:explode" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.explode").getString();
                layout.addRadioVariableInput("pos", 80, "an Position des Spielers", "an Position", "§e(o) Auto-Pos.§7\nNutzt standardmäßig den Spieler als Position.\n\n§e( ) Eigene Variable:§7\nErlaubt z.B. $hitPos oder $blockPos.");
                layout.nextLine();
                // Text: "Erzeuge eine Explosion an der Position des Spielers mit Radius"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_85").getString());
                layout.addInput("radius", 40, "3.0", true);
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_86").getString());
                layout.nextLine();
                // Text: "Soll die Explosion umliegende Blöcke in Brand setzen?"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_87").getString());
                layout.addDropdown("fire", 60, new String[]{"false", "true"}, "false");
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_88").getString());
            }
            case "stones:math" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.math").getString();
                // Text: "Nimm die Variable"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_89").getString());
                layout.addVariableByNameInput("variable", 80, "my_var"); 
                // Text: "und"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_90").getString());
                layout.addDropdown("operation", 70, new String[]{"add", "subtract", "multiply", "divide"}, "add");
                layout.nextLine();
                // Text: "den Wert"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_91").getString());
                layout.addInput("value", 60, "1.0", true);
                // Text: "damit."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_92").getString());
            }
            case "stones:cooldown" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.cooldown").getString();
                // Text: "Sperre den Action-Trigger mit dem Namen"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_93").getString());
                layout.addInput("name", 100, "$runeId", false);
                layout.nextLine();
                // Text: "für exakt"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_94").getString());
                layout.addInput("ticks", 40, "100", true);
                // Text: "Spiel-Ticks (20 Ticks = 1 Sekunde)."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_95").getString());
            }
            case "stones:cancel" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.cancel").getString();
                // Text: "Breche das ursprüngliche Vanilla-Event (z.B. den erhaltenen Schaden)"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_96").getString());
                layout.nextLine();
                // Text: "vollständig und ersatzlos ab."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_97").getString());
            }
            case "stones:play_sound" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.play_sound").getString();
                // Text: "Spiele den Sound"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_98").getString());
                layout.addSuggestInputWithPreview("sound", 180, "minecraft:entity.experience_orb.pickup", 2, "sound");
                layout.nextLine();
                // Text: "auf dem Audiokanal"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_99").getString());
                layout.addDropdown("source", 80, new String[]{"players", "master", "music", "weather", "blocks", "hostile", "neutral", "ambient"}, "players");
                // Text: "mit Lautstärke"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_100").getString());
                layout.addInput("volume", 30, "1.0", true);
                layout.nextLine();
                // Text: "und Tonhöhe/Geschwindigkeit"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_101").getString());
                layout.addInput("pitch", 30, "1.0", true);
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_102").getString());
            }
            case "stones:spawn_particles" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.spawn_particles").getString();
                // Text: "Erzeuge"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_103").getString());
                layout.addInput("count", 30, "10", true);
                // Text: "Partikel vom Typ"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_104").getString());
                layout.addSuggestInput("particle", 140, "minecraft:flame", 5); 
                layout.nextLine();
                layout.addRadioVariableInput("pos", 80, "an Position des Spielers", "an Position", "§e(o) Auto-Pos.§7\nNutzt standardmäßig den Spieler als Position.\n\n§e( ) Eigene Variable:§7\nErlaubt z.B. $blockPos oder $hitPos.");
                layout.nextLine();
                // Text: "mit einer Streuung von"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_105").getString());
                layout.addInput("spread", 30, "0.2", true);
                // Text: "und einem Tempo von"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_106").getString());
                layout.addInput("speed", 30, "0.0", true);
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_107").getString());
            }
            case "stones:particle_orbit" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.particle_orbit").getString();
                // Text: "Lass"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_108").getString());
                layout.addInput("count", 30, "1.0", true);
                // Text: "Partikel vom Typ"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_109").getString());
                layout.addSuggestInput("particle", 140, "minecraft:flame", 5);
                layout.nextLine();
                // Text: "um den Spieler rotieren."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_110").getString());
            }
            case "stones:set_variable" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.set_variable").getString();
                // Text: "Setze die temporäre Event-Variable"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_111").getString());
                layout.addVariableByNameInput("name", 80, "my_var"); 
                layout.nextLine();
                // Text: "auf den Wert"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_112").getString());
                layout.addInput("value", 80, "1.0", false);
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_113").getString());
            }
            case "stones:set_block" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.set_block").getString();
                // Text: "Platziere"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_114").getString());
                layout.nextLine();
                layout.addRadioVariableInput("pos", 80, "an der Position des Fadenkreuzes", "an Position", "§e(o) Auto-Pos.§7\nSucht automatisch den anvisierten Block.\n\n§e( ) Eigene Variable:§7\nErlaubt die gezielte Angabe einer Koordinate (z.B. $blockPos).");
                layout.nextLine();
                // Text: "den Block"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_115").getString());
                layout.addInput("block", 160, "minecraft:air", false);
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_116").getString());
            }
            case "stones:for_each" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.for_each").getString();
                // Text: "Iteriere durch die Liste in Variable"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_117").getString());
                layout.addInput("from", 80, "$found_blocks", false);
                layout.nextLine();
                // Text: "und speichere jedes Element während des Durchlaufs als"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_118").getString());
                layout.addVariableByNameInput("as", 60, "pos"); 
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_119").getString());
                layout.nextLine();
                // Text: "(Die auszuführenden Aktionen befinden sich in diesem Block)"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_120").getString());
            }
            case "stones:marker" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.marker").getString();
                // Text: "Zeichne eine optische X-Ray Markierung im Modus"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_121").getString());
                layout.addDropdown("mode", 60, new String[]{"point", "box"}, "point");
                layout.nextLine();
                layout.addRadioVariableInput("pos", 80, "an der Event-Position", "an Position", "§e(o) Auto-Pos.§7\nNutzt automatisch die Position des Events.\n\n§e( ) Eigene Variable:§7\nErlaubt z.B. $hitPos oder $blockPos.");
                layout.nextLine();
                // Text: "mit der Größe"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_122").getString());
                layout.addInput("size", 30, "1.0", true);
                // Text: "für eine Dauer von"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_123").getString());
                layout.addInput("duration", 40, "100", true);
                // Text: "Ticks."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_124").getString());
            }
            case "stones:find_blocks" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.find_blocks").getString();
                // Text: "Suche in der Nähe im Modus"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_125").getString());
                layout.addDropdown("mode", 80, new String[]{"radius", "raycast"}, "radius");
                // Text: "nach Blöcken."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_126").getString());
                layout.nextLine();
                // Text: "Suchradius / Suchdistanz:"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_127").getString());
                layout.addInput("radius", 40, "5.0", true);
                // Text: "Sichtlinie erzwingen?"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_128").getString());
                layout.addDropdown("line_of_sight", 60, new String[]{"false", "true"}, "false");
                layout.nextLine();
                // Text: "Speichere die gefundene Block-Liste in die Variable"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_129").getString());
                layout.addVariableByNameInput("save_to", 80, "found_blocks"); 
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_130").getString());
                layout.nextLine();
                // Text: "(Tipp: Filter-Tags und IDs können aktuell nur im JSON Tab bearbeitet werden)."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_131").getString());
            }
            case "stones:command" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.command").getString();
                // Text: "Führe folgenden Chat-Befehl lautlos (als Server-OP) aus:"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_132").getString());
                layout.nextLine();
                // Text: "/"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_133").getString());
                layout.addInput("command", 300, "say Hallo Welt!", false);
            }
            case "stones:remove_random_enchantment" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.remove_random_enchantment").getString();
                // Text: "Entferne ein zufälliges Enchantment der getragenen Waffe"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_134").getString());
                layout.nextLine();
                // Text: "und speichere das alte Level in die Variable"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_135").getString());
                layout.addVariableByNameInput("save_level_to", 80, "sacrificed_lvl"); 
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_136").getString());
            }

            // ================== B E D I N G U N G E N ==================
            case "stones:chance" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.chance").getString();
                // Text: "Es besteht eine Wahrscheinlichkeit von"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_137").getString());
                layout.addInput("value", 40, "0.5", true);
                layout.nextLine();
                // Text: "(0.0 bis 1.0), dass diese Bedingung zutrifft."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_138").getString());
            }
            case "stones:health_below" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.health_below").getString();
                // Text: "Das Leben des Spielers liegt unter oder bei"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_139").getString());
                layout.addInput("percent", 40, "0.5", true);
                layout.nextLine();
                // Text: "(% der Max-HP)."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_140").getString());
            }
            case "stones:variable_compare" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.variable_compare").getString();
                // Text: "Vergleiche den Wert der Variable"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_141").getString());
                layout.addVariableByNameInput("variable", 80, "my_var"); 
                layout.nextLine();
                layout.addDropdown("operator", 40, new String[]{">", "<", ">=", "<=", "==", "!="}, ">");
                // Text: "mit dem Kontroll-Wert"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_142").getString());
                layout.addInput("value", 80, "0.0", false);
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_143").getString());
            }
            case "stones:persistent_var_compare" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.persistent_var_compare").getString();
                // Text: "Lese den permanenten NBT-Wert"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_144").getString());
                layout.addInput("name", 80, "my_data", false);
                layout.nextLine();
                // Text: "und prüfe, ob er"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_145").getString());
                layout.addDropdown("operator", 40, new String[]{">", "<", ">=", "<=", "==", "!="}, ">");
                // Text: "dem Wert"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_146").getString());
                layout.addInput("value", 80, "0.0", true);
                // Text: "entspricht."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_147").getString());
            }
            case "stones:has_air" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.has_air").getString();
                // Text: "Der Spieler hat unter Wasser noch mindestens"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_148").getString());
                layout.addInput("min", 40, "100", true);
                // Text: "Luftblasen."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_149").getString());
            }
            case "stones:is_ready" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.is_ready").getString();
                // Text: "Der Cooldown / Timer für den Action-Trigger"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_150").getString());
                layout.addInput("name", 100, "$runeId", false);
                layout.nextLine();
                // Text: "ist vollständig abgelaufen und die Fähigkeit ist wieder bereit."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_151").getString());
            }
            case "stones:block_check" -> {
                actionTitle = Component.translatable("gui.stones.studio.actionedit.title.block_check").getString();
                // Text: "Prüfe, ob der Block"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_152").getString());
                layout.nextLine();
                layout.addRadioVariableInput("pos", 80, "an der Position des Fadenkreuzes", "an Position", "§e(o) Auto-Pos.§7\nPrüft automatisch den anvisierten Block.\n\n§e( ) Eigene Variable:§7\nErlaubt die gezielte Angabe einer Koordinate (z.B. $blockPos).");
                layout.nextLine();
                // Text: "dem Block"
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_153").getString());
                layout.addInput("block", 160, "minecraft:stone", false);
                // Text: "."
                layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_154").getString());
            }
        // Text: "Bedingung: Es regnet derzeit in der Spielwelt."
            case "stones:is_raining" -> { actionTitle = Component.translatable("gui.stones.studio.actionedit.title.is_raining").getString(); layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_155").getString()); }
        // Text: "Bedingung: Es gibt derzeit ein Gewitter in der Spielwelt."
            case "stones:is_thundering" -> { actionTitle = Component.translatable("gui.stones.studio.actionedit.title.is_thundering").getString(); layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_156").getString()); }
        // Text: "Bedingung: Der Spieler brennt."
            case "stones:is_on_fire" -> { actionTitle = Component.translatable("gui.stones.studio.actionedit.title.is_on_fire").getString(); layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_157").getString()); }
        // Text: "Bedingung: Es ist derzeit Tag in der Spielwelt."
            case "stones:is_day" -> { actionTitle = Component.translatable("gui.stones.studio.actionedit.title.is_day").getString(); layout.addText(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_158").getString()); }
            
            default -> layout.usesFallback = true;
        }
    }

    @Override
    protected void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int startX, int startY) {
        if (!sentenceLayout.usesFallback) {
            sentenceLayout.render(graphics, font);
        } else {
        // Text: "Rohdaten (JSON):"
            graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_159").getString(), startX + 15, startY + 28, 0xFFAAAAAA);
        }
    }

    @Override
    public void onCancel() {
        screen.closeModal();
    }

    // =========================================================================================
    // WC3 Layout Engine
    // =========================================================================================
    protected class SentenceLayoutHelper {
        public int currentX, currentY;
        public int startX, maxX;
        public boolean usesFallback = false;
        
        public List<TextElement> texts = new ArrayList<>();
        public List<Runnable> saveHooks = new ArrayList<>();

        public SentenceLayoutHelper(int startX, int startY, int maxX) {
            this.startX = startX;
            this.currentX = startX;
            this.currentY = startY;
            this.maxX = maxX;
        }

        public void addText(String text) {
            int w = font.width(text);
            if (currentX + w > maxX) nextLine();
            
            texts.add(new TextElement(text, currentX, currentY + 3));
            currentX += w + 4;
        }

        /**
         * Erstellt ein Texteingabefeld. Nutzt jetzt UniversalSuggestField, 
         * um bei der Eingabe von '$' automatisch das Variablen-Dropdown anzuzeigen.
         */
        public void addInput(String key, int width, String defVal, boolean isNumeric) {
            if (currentX + width > maxX) nextLine();
            
            String currentVal = targetNode.jsonData.has(key) ? targetNode.jsonData.get(key).getAsString() : defVal;
            
            // Nutzt UniversalSuggestField für automatischen Variablen-Support
            StudioSuggestTextField.UniversalSuggestField field = new StudioSuggestTextField.UniversalSuggestField(
                screen, font, currentX, currentY, width, 14, Component.literal("")
            );
            field.setContextNode(targetNode);
            field.setValue(currentVal);
            addModalWidget(field);
            
            saveHooks.add(() -> {
                String val = field.getValue().trim();
                
                if (val.isEmpty() && defVal != null && !defVal.isEmpty()) {
                    val = defVal;
                }
                
                if (val.isEmpty()) {
                    targetNode.jsonData.remove(key);
                } else if (isNumeric && !val.startsWith("$")) {
                    try {
                        if (val.contains(".")) targetNode.jsonData.addProperty(key, Double.parseDouble(val));
                        else targetNode.jsonData.addProperty(key, Integer.parseInt(val));
                    } catch (Exception e) { targetNode.jsonData.addProperty(key, val); }
                } else {
                    targetNode.jsonData.addProperty(key, val);
                }
            });
            
            currentX += width + 4;
        }

        /**
         * NEU: Erzeugt ein Eingabefeld, welches die Variable OHNE das führende '$'-Zeichen 
         * speichert und anzeigt. Verwendet das neue VariableByNameSuggestField-Dropdown.
         */
        public void addVariableByNameInput(String key, int width, String defVal) {
            if (currentX + width > maxX) nextLine();
            
            String currentVal = targetNode.jsonData.has(key) ? targetNode.jsonData.get(key).getAsString() : defVal;
            
            // Bereinigt das führende '$', falls dieses versehentlich im JSON steht
            if (currentVal.startsWith("$")) {
                currentVal = currentVal.substring(1);
            }
            
            StudioSuggestTextField.VariableByNameSuggestField field = new StudioSuggestTextField.VariableByNameSuggestField(
                screen, font, currentX, currentY, width, 14, Component.literal(""), targetNode
            );
            field.setValue(currentVal);
            addModalWidget(field);
            
            saveHooks.add(() -> {
                String val = field.getValue().trim();
                
                // Erzwingt das Entfernen des '$' beim Sichern
                if (val.startsWith("$")) {
                    val = val.substring(1);
                }
                
                if (val.isEmpty() && defVal != null && !defVal.isEmpty()) {
                    val = defVal;
                }
                
                if (val.isEmpty()) {
                    targetNode.jsonData.remove(key);
                } else {
                    targetNode.jsonData.addProperty(key, val);
                }
            });
            
            currentX += width + 4;
        }

        public void addSuggestInput(String key, int width, String defVal, int suggestType) {
            if (currentX + width > maxX) nextLine();
            
            String currentVal = targetNode.jsonData.has(key) ? targetNode.jsonData.get(key).getAsString() : defVal;
            StudioSuggestTextField field = null;
            
            if (suggestType == 1) field = new StudioSuggestTextField.AttributeSuggestField(screen, font, currentX, currentY, width, 14, Component.literal(""));
            else if (suggestType == 2) field = new StudioSuggestTextField.SoundSuggestField(screen, font, currentX, currentY, width, 14, Component.literal(""));
            else if (suggestType == 3) field = new StudioSuggestTextField.EntitySuggestField(screen, font, currentX, currentY, width, 14, Component.literal(""));
            else if (suggestType == 4) field = new StudioSuggestTextField.EffectSuggestField(screen, font, currentX, currentY, width, 14, Component.literal(""));
            else if (suggestType == 5) field = new StudioSuggestTextField.ParticleSuggestField(screen, font, currentX, currentY, width, 14, Component.literal(""));
            
            if (field == null) { 
                addInput(key, width, defVal, false); 
                return; 
            }

            field.setContextNode(targetNode); // Setzt den Kontext für den $-Variablen-Trigger
            field.setValue(currentVal);
            addModalWidget(field);
            
            StudioSuggestTextField finalField = field;
            saveHooks.add(() -> {
                String val = finalField.getValue().trim();
                if (val.isEmpty() && defVal != null && !defVal.isEmpty()) val = defVal;
                
                if (val.isEmpty()) {
                    targetNode.jsonData.remove(key);
                } else {
                    targetNode.jsonData.addProperty(key, val);
                }
            });
            
            currentX += width + 4;
        }

        public void addSuggestInputWithPreview(String key, int width, String defVal, int suggestType, String previewType) {
            if (currentX + width + 20 > maxX) nextLine(); 
            
            String currentVal = targetNode.jsonData.has(key) ? targetNode.jsonData.get(key).getAsString() : defVal;
            StudioSuggestTextField field = null;
            
            if (suggestType == 2) field = new StudioSuggestTextField.SoundSuggestField(screen, font, currentX, currentY, width, 14, Component.literal(""));
            
            if (field == null) { 
                addInput(key, width, defVal, false); 
                return; 
            }

            field.setContextNode(targetNode); // Setzt den Kontext für den $-Variablen-Trigger
            field.setValue(currentVal);
            addModalWidget(field);
            
            StudioSuggestTextField finalField = field;
            saveHooks.add(() -> {
                String val = finalField.getValue().trim();
                if (val.isEmpty() && defVal != null && !defVal.isEmpty()) val = defVal;
                
                if (val.isEmpty()) {
                    targetNode.jsonData.remove(key);
                } else {
                    targetNode.jsonData.addProperty(key, val);
                }
            });
            
            currentX += width + 4;

        // Text: "▶"
            Button playBtn = Button.builder(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_160"), b -> {
                String val = finalField.getValue().trim();
                if (previewType.equals("sound")) {
                    try {
                        net.minecraft.resources.ResourceLocation loc = new net.minecraft.resources.ResourceLocation(val);
                        net.minecraft.sounds.SoundEvent event = net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.getValue(loc);
                        if (event != null) {
                            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(event, 1.0F, 1.0F)
                            );
                        }
                    } catch (Exception ignored) {}
                }
            }).bounds(currentX, currentY, 14, 14)
        // Text: "§aVorschau abspielen"
              .tooltip(net.minecraft.client.gui.components.Tooltip.create(net.minecraft.network.chat.Component.translatable("gui.stones.studio.actionedit.text_161")))
              .build();

            addModalWidget(playBtn);
            currentX += 14 + 4;
        }

        public void addRadioVariableInput(String key, int inputWidth, String radio1Label, String radio2Label, String tooltipText) {
            boolean hasVal = targetNode.jsonData.has(key);
            boolean[] useDefault = { !hasVal || targetNode.jsonData.get(key).getAsString().isEmpty() };
            String currentVal = hasVal ? targetNode.jsonData.get(key).getAsString() : "";

            String btnText1 = "(o) " + radio1Label;
            String btnText1Unselected = "( ) " + radio1Label;
            String btnText2 = "(o) " + radio2Label;
            String btnText2Unselected = "( ) " + radio2Label;

            int maxTextWidth = Math.max(font.width(btnText1), font.width(btnText2));
            int btnWidth = maxTextWidth + 10;
            
            if (currentX + btnWidth > maxX) nextLine();

            StudioSuggestTextField.VariableSuggestField field = new StudioSuggestTextField.VariableSuggestField(
                screen, font, 0, 0, inputWidth, 14, Component.literal(""), targetNode);
            field.setValue(currentVal);
            field.visible = !useDefault[0];
            addModalWidget(field);

            Button[] btns = new Button[2];

            btns[0] = Button.builder(Component.literal(useDefault[0] ? btnText1 : btnText1Unselected), b -> {
                useDefault[0] = true;
                btns[0].setMessage(Component.literal(btnText1));
                btns[1].setMessage(Component.literal(btnText2Unselected));
                field.visible = false;
            }).bounds(currentX, currentY, btnWidth, 14)
              .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tooltipText)))
              .build();
            addModalWidget(btns[0]);

            nextLine();

            btns[1] = Button.builder(Component.literal(!useDefault[0] ? btnText2 : btnText2Unselected), b -> {
                useDefault[0] = false;
                btns[0].setMessage(Component.literal(btnText1Unselected));
                btns[1].setMessage(Component.literal(btnText2));
                field.visible = true;
            }).bounds(currentX, currentY, btnWidth, 14)
              .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tooltipText)))
              .build();
            addModalWidget(btns[1]);

            field.setX(currentX + btnWidth + 4);
            field.setY(currentY);

            saveHooks.add(() -> {
                if (useDefault[0] || field.getValue().trim().isEmpty()) {
                    targetNode.jsonData.remove(key);
                } else {
                    targetNode.jsonData.addProperty(key, field.getValue().trim());
                }
            });

            currentX += btnWidth + 4 + inputWidth + 4;
        }

        public void addDropdown(String key, int width, String[] options, String defVal) {
            if (currentX + width > maxX) nextLine();
            
            String currentVal = targetNode.jsonData.has(key) ? targetNode.jsonData.get(key).getAsString() : defVal;
            int[] idx = {0};
            for(int i = 0; i < options.length; i++) {
                if(options[i].equalsIgnoreCase(currentVal)) idx[0] = i;
            }
            String[] state = { options[idx[0]] };
            
            Button btn = Button.builder(Component.literal(state[0]), b -> {
                idx[0] = (idx[0] + 1) % options.length;
                state[0] = options[idx[0]];
                b.setMessage(Component.literal(state[0]));
            }).bounds(currentX, currentY, width, 14).build();
            
            addModalWidget(btn);
            
            saveHooks.add(() -> {
                targetNode.jsonData.addProperty(key, state[0]);
            });
            
            currentX += width + 4;
        }

        public void nextLine() {
            currentX = startX;
            currentY += 22;
        }

        public void render(GuiGraphics g, net.minecraft.client.gui.Font font) {
            for(TextElement t : texts) {
                g.drawString(font, t.text, t.x, t.y, 0xFFBBBBBB);
            }
        }
    }
    
    protected static class TextElement {
        public final String text;
        public final int x;
        public final int y;
        public TextElement(String text, int x, int y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }
}