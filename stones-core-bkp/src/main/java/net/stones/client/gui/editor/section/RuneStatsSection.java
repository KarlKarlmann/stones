package net.stones.client.gui.editor.section;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;
import net.stones.client.gui.editor.StonesStudioScreen;

/**
 * Sub-Sektion für das Stats-Accordion ("Stats & Skalierungen").
 * Zeichnet das einklappbare Panel, bei dem jeder Stat-Eintrag in einem eigenen,
 * formatierten Kasten (Card) über genau 3 Zeilen dargestellt wird.
 * Ordnet die Buttons vertikal an, um Text-Überlagerungen komplett zu verhindern.
 * Aktualisiert: Zeigt nun auch 'min' und 'max' Limits an, falls konfiguriert!
 */
public class RuneStatsSection {

    private final StonesStudioScreen screen;
    
    // Definition der Abstände und Kasten-Dimensionen für das 3-zeilige Card-Layout
    public static final int CARD_HEIGHT = 44; // Genug Platz für 3 Zeilen Text (je 12px)
    public static final int ROW_HEIGHT = 50;  // 44px Box-Höhe + 6px Abstand zwischen den Boxen

    public RuneStatsSection(StonesStudioScreen screen) {
        this.screen = screen;
    }

    public void resetScroll() {
        // Das Scrollen wird jetzt zentral und einheitlich über 'mainScrollY' in StonesStudioScreen verwaltet.
    }

    public void render(GuiGraphics graphics, int editorX, int mouseX, int mouseY) {
        Font font = screen.getFont();
        if (font == null) return;

        int areaWidth = screen.width - editorX - 20;

        // =========================================================================
        // SEKTION: STATS & SKALIERUNGEN (EINKLAPPBAR)
        // =========================================================================
        int statsHeaderY = screen.getStatsHeaderY();
        boolean hoverStats = mouseX >= editorX && mouseX < editorX + areaWidth && mouseY >= statsHeaderY && mouseY < statsHeaderY + 12;
        
        graphics.fill(editorX, statsHeaderY, editorX + areaWidth, statsHeaderY + 12, hoverStats ? 0x22FFFFFF : 0x11FFFFFF);
        graphics.renderOutline(editorX, statsHeaderY, areaWidth, 12, 0xFF444449);
        
        String statsArrow = StonesStudioScreen.isStatsExpanded ? "▼ " : "▶ ";
        graphics.drawString(font, statsArrow + "Stats & Skalierungen", editorX + 6, statsHeaderY + 2, 0xFFFFAA00);

        if (StonesStudioScreen.isStatsExpanded) {
            int statsContentY = screen.getStatsContentY();

            // "+ Stat hinzufügen" Button (Zielsicher rechtsbündig in der Headerzeile platziert)
            int addBtnX = editorX + areaWidth - 110;
            int addBtnY = statsHeaderY + 1;
            boolean hoverAdd = mouseX >= addBtnX && mouseX < addBtnX + 110 && mouseY >= addBtnY && mouseY < addBtnY + 11;
            graphics.fill(addBtnX, addBtnY, addBtnX + 110, addBtnY + 11, hoverAdd ? 0x4455FF55 : 0x2255FF55);
            graphics.renderOutline(addBtnX, addBtnY, 110, 11, hoverAdd ? 0xFF55FF55 : 0xFF00AA00);
        // Text: "➕ Stat hinzufügen"
            graphics.drawCenteredString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runestatssection.text_01").getString(), addBtnX + 55, addBtnY + 2, 0xFFFFFFFF);

            int rowY = statsContentY + 5;
            for (int i = 0; i < StonesStudioScreen.activeStats.size(); i++) {
                JsonObject stat = StonesStudioScreen.activeStats.get(i);
                String id = stat.has("id") ? stat.get("id").getAsString() : "unbenannt";
                double base = stat.has("base") ? stat.get("base").getAsDouble() : 0.0;
                double perLvl = stat.has("per_level") ? stat.get("per_level").getAsDouble() : 0.0;
                String suffix = stat.has("suffix") ? stat.get("suffix").getAsString() : "";
                String scaling = stat.has("scaling") ? stat.get("scaling").getAsString() : "RUNE_LEVEL";
                
                // NEU: Lese optionale min/max Werte aus
                String minLimit = stat.has("min") ? " | Min: " + stat.get("min").getAsString() : "";
                String maxLimit = stat.has("max") ? " | Max: " + stat.get("max").getAsString() : "";
                
                // Formatiertes Kasten-Rendering für jede geladene Stat (Eigene Card)
                int cardY = rowY;
                graphics.fill(editorX + 4, cardY, editorX + areaWidth - 4, cardY + CARD_HEIGHT, 0xFF141416);
                graphics.renderOutline(editorX + 4, cardY, areaWidth - 8, CARD_HEIGHT, 0xFF2D2D31);

                // =========================================================================
                // DREIZEILIGES DESIGN: Strukturierte Werte-Anzeige ohne Überlappung
                // =========================================================================
                // Zeile 1: Stat-ID (Auffällig in fettem Weiß)
        // Text: "✦ Stat: §f"
                graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runestatssection.text_02").getString() + id, editorX + 10, cardY + 5, 0xFFFFAA00);
                
                // Zeile 2: Basiswert & Stufen-Steigerung (In dezentem Hellgrau)
                String valuesText = "Basis: " + base + "  |  Pro Stufe: " + perLvl + (suffix.isEmpty() ? "" : " (" + suffix + ")");
        // Text: "§7"
                graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runestatssection.text_03").getString() + valuesText, editorX + 10, cardY + 17, 0xFFAAAAAA);

                // Zeile 3: Skalierungs-Typ (In modernem Aqua-Blau) + Min/Max Limits
                String scalingAndLimits = scaling + minLimit + maxLimit;
        // Text: "Skalierung: §b"
                graphics.drawString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runestatssection.text_04").getString() + scalingAndLimits, editorX + 10, cardY + 29, 0xFFAAAAAA);

                // =========================================================================
                // BUTTON-LAYOUT: Vertikal gestapelt auf der rechten Seite
                // =========================================================================
                int btnX = editorX + areaWidth - 65;
                int btnW = 58;
                int btnH = 16;

                int editBtnY = cardY + 4;
                int delBtnY = cardY + 24;

                boolean hoverEdit = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= editBtnY && mouseY < editBtnY + btnH;
                boolean hoverDel = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= delBtnY && mouseY < delBtnY + btnH;

                // Ändern Button (Oben)
                graphics.fill(btnX, editBtnY, btnX + btnW, editBtnY + btnH, hoverEdit ? 0x44FFAA00 : 0x22FFAA00);
                graphics.renderOutline(btnX, editBtnY, btnW, btnH, hoverEdit ? 0xFFFFAA00 : 0xFFD87A00);
        // Text: "Ändern"
                graphics.drawCenteredString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runestatssection.text_05").getString(), btnX + (btnW / 2), editBtnY + 4, 0xFFFFFFFF);

                // Löschen Button (Unten)
                graphics.fill(btnX, delBtnY, btnX + btnW, delBtnY + btnH, hoverDel ? 0x44FF5555 : 0x22FF5555);
                graphics.renderOutline(btnX, delBtnY, btnW, btnH, hoverDel ? 0xFFFF5555 : 0xFFD80000);
        // Text: "Löschen"
                graphics.drawCenteredString(font, net.minecraft.network.chat.Component.translatable("gui.stones.studio.runestatssection.text_06").getString(), btnX + (btnW / 2), delBtnY + 4, 0xFFFFFFFF);

                rowY += ROW_HEIGHT;
            }
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int currentLeftWidth = screen.isLeftPanelOpen() ? StonesStudioScreen.LEFT_PANEL_WIDTH : 0;
        int editorX = currentLeftWidth + 20;
        int areaWidth = screen.width - editorX - 20;
        int statsHeaderY = screen.getStatsHeaderY();

        // 1. Klick auf den "+ Stat hinzufügen" Button abfangen (Muss zwingend VOR dem Header-Klick abgefangen werden!)
        if (StonesStudioScreen.isStatsExpanded) {
            int addBtnX = editorX + areaWidth - 110;
            int addBtnY = statsHeaderY + 1;
            if (mouseX >= addBtnX && mouseX < addBtnX + 110 && mouseY >= addBtnY && mouseY < addBtnY + 11 && button == 0) {
                JsonObject newStat = new JsonObject();
                newStat.addProperty("id", "neue_stat_id");
                newStat.addProperty("label", "DICT:stat.stones.neue_stat");
                newStat.addProperty("type", "generic");
                newStat.addProperty("base", 0.0);
                newStat.addProperty("per_level", 0.0);
                newStat.addProperty("scaling", "RUNE_LEVEL");
                newStat.addProperty("suffix", "");
                
                screen.openStatModal(newStat, true);
                return true;
            }
        }

        // 2. Klick auf den Header-Balken der Stats toggelt den Zustand
        if (mouseX >= editorX && mouseX < editorX + areaWidth && mouseY >= statsHeaderY && mouseY < statsHeaderY + 12) {
            StonesStudioScreen.isStatsExpanded = !StonesStudioScreen.isStatsExpanded;
            screen.updateHeaderVisibility();
            return true;
        }

        // Interaktionen innerhalb der aufgeklappten Stats
        if (StonesStudioScreen.isStatsExpanded) {
            int statsContentY = screen.getStatsContentY();

            // Stat-Reihen Klicks abfangen anhand des neuen vertikalen Card-Layouts
            if (mouseX >= editorX && mouseX < screen.width - 20 && mouseY >= statsContentY && mouseY < statsContentY + (StonesStudioScreen.activeStats.size() * ROW_HEIGHT)) {
                int itemIndex = (int)((mouseY - statsContentY - 5) / ROW_HEIGHT);

                if (itemIndex >= 0 && itemIndex < StonesStudioScreen.activeStats.size()) {
                    int cardY = statsContentY + 5 + (itemIndex * ROW_HEIGHT);
                    
                    int btnX = editorX + areaWidth - 65;
                    int btnW = 58;
                    int btnH = 16;

                    int editBtnY = cardY + 4;
                    int delBtnY = cardY + 24;

                    // Klick auf "Ändern" (Oben)
                    if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= editBtnY && mouseY < editBtnY + btnH) {
                        screen.openStatModal(StonesStudioScreen.activeStats.get(itemIndex), false);
                        return true;
                    }
                    // Klick auf "Löschen" (Unten)
                    else if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= delBtnY && mouseY < delBtnY + btnH) {
                        StonesStudioScreen.activeStats.remove(itemIndex);
                        return true;
                    }
                }
            }
        }
        return false;
    }
}