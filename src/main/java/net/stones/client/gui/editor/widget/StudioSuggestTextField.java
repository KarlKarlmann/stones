package net.stones.client.gui.editor.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import net.stones.client.gui.editor.StonesStudioScreen;
import net.stones.client.gui.editor.widget.StudioTextField;
import net.stones.client.gui.editor.modal.IconEditModal;

/**
 * Eine abstrakte Elternklasse für Textfelder mit automatischer Vorschlagsliste (Auto-Complete).
 * Kapselt die gesamte komplexe Navigations-, Filter-, Scroll- und Rendering-Logik.
 * Subklassen müssen lediglich die Methode {@link #populateSuggestions} implementieren.
 * * NEU: Unterstützt jetzt die automatische Erkennung des $-Zeichens, um universell 
 * an jedem Textfeld Variablen-Autocomplete anzubieten.
 */
public abstract class StudioSuggestTextField extends StudioTextField {

    protected final StonesStudioScreen screen;
    protected final List<String> suggestions = new ArrayList<>();
    protected final List<String> filteredSuggestions = new ArrayList<>();
    
    // Der Kontext-Knoten, um lokale Variablen im Event-Baum zu scannen
    protected net.stones.client.gui.editor.TreeNode contextNode;
    protected final List<String> dynamicVarSuggestions = new ArrayList<>();
    protected final java.util.Map<String, String> dynamicVarDescriptions = new java.util.HashMap<>();

    protected boolean showSuggestions = false;
    protected int selectedSuggestionIndex = 0;
    protected int scrollOffset = 0;
    protected static final int MAX_VISIBLE_SUGGESTIONS = 6;
    
    // Verhindert das unabsichtliche Sperren des Textfeldes nach einer Auswahl
    protected boolean shouldIgnoreFocusRequest = false;

    public StudioSuggestTextField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message) {
        super(screen, font, x, y, width, height, message);
        this.screen = screen;
        
        // Überwacht Wertänderungen beim Tippen, um Vorschläge live zu filtern
        this.setResponder(this::updateFilteredSuggestions);
    }

    public StudioSuggestTextField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message, Component tooltipText) {
        super(screen, font, x, y, width, height, message, tooltipText);
        this.screen = screen;
        
        this.setResponder(this::updateFilteredSuggestions);
    }

    public void setContextNode(net.stones.client.gui.editor.TreeNode contextNode) {
        this.contextNode = contextNode;
    }

    /**
     * Muss von den spezialisierten Kindern implementiert werden, um die Vorschlagsdatenbank zu füllen.
     */
    protected abstract void populateSuggestions();

    public boolean showSuggestions() {
        return this.showSuggestions;
    }

    /**
     * Generiert dynamisch Tooltips für Vorschläge, inklusive Erkennung von $-Variablen.
     */
    protected Component getSuggestionTooltip(String suggestion) {
        if (suggestion.startsWith("$")) {
            String desc = dynamicVarDescriptions.getOrDefault(suggestion, "");
            net.minecraft.network.chat.MutableComponent tooltip = Component.literal("")
                .append(Component.literal(suggestion).withStyle(ChatFormatting.AQUA));
            if (!desc.isEmpty()) {
                tooltip.append(Component.literal("\n" + desc).withStyle(ChatFormatting.GRAY));
            }
            return tooltip;
        }
        return Component.literal(suggestion);
    }

    /**
     * Bestimmt die Render-Farbe eines Vorschlags dynamisch.
     */
    protected int getSuggestionColor(String suggestion, boolean isSelected, boolean isHovered) {
        if (isSelected) return 0xFFFFAA00; // Gold für Selektion
        if (suggestion.startsWith("$")) {
            return 0xFF55FFFF; // Cyan/Aqua für Variablen
        }
        return 0xFFCCCCCC; // Standard Grau
    }

    /**
     * Scannt und befüllt in Echtzeit alle Variablen des Event-Zweigs für das Autocomplete.
     */
    protected void populateDynamicVariables() {
        this.dynamicVarSuggestions.clear();
        this.dynamicVarDescriptions.clear();
        if (this.contextNode == null) return;

        addDynamicVar("$player", Component.translatable("gui.stones.studio.suggest.var.player").getString());
        addDynamicVar("$level", Component.translatable("gui.stones.studio.suggest.var.level").getString());
        addDynamicVar("$playerHealth", Component.translatable("gui.stones.studio.suggest.var.playerHealth").getString());
        addDynamicVar("$playerLevel", Component.translatable("gui.stones.studio.suggest.var.playerLevel").getString());
        addDynamicVar("$RuneLevel", Component.translatable("gui.stones.studio.suggest.var.runeLevel").getString());
        addDynamicVar("$SockLevel", Component.translatable("gui.stones.studio.suggest.var.sockLevel").getString());
        addDynamicVar("$AmplifyMultiplier", Component.translatable("gui.stones.studio.suggest.var.amplifyMultiplier").getString());
        addDynamicVar("$runeId", Component.translatable("gui.stones.studio.suggest.var.runeId").getString());

        String trigger = "UNKNOWN";
        net.stones.client.gui.editor.TreeNode rootEvent = null;
        net.stones.client.gui.editor.TreeNode current = contextNode;
        while (current != null) {
            if (current.type == net.stones.client.gui.editor.TreeNode.Type.EVENT) {
                rootEvent = current;
                trigger = current.rawId;
                break;
            }
            current = current.parent;
        }

        if (trigger.equals("ON_ATTACK") || trigger.equals("ON_HURT")) {
            addDynamicVar("$damage", Component.translatable("gui.stones.studio.suggest.var.damage").getString());
            addDynamicVar("$victim", Component.translatable("gui.stones.studio.suggest.var.victim").getString());
            addDynamicVar("$attacker", Component.translatable("gui.stones.studio.suggest.var.attacker").getString());
        }
        if (trigger.equals("ON_PROJECTILE_HIT")) {
            addDynamicVar("$projectile", Component.translatable("gui.stones.studio.suggest.var.projectile").getString());
            addDynamicVar("$hitPos", Component.translatable("gui.stones.studio.suggest.var.hitPos").getString());
        }
        if (trigger.equals("ON_BLOCK_BREAK")) {
            addDynamicVar("$blockPos", Component.translatable("gui.stones.studio.suggest.var.blockPos").getString());
            addDynamicVar("$blockState", Component.translatable("gui.stones.studio.suggest.var.blockState").getString());
        }

        for (com.google.gson.JsonObject stat : StonesStudioScreen.activeStats) {
            if (stat.has("id")) {
                String statId = stat.get("id").getAsString();
                String label = stat.has("label") ? stat.get("label").getAsString() : "Custom Stat";
                if (label.startsWith("DICT:")) label = Component.translatable(label.substring(5)).getString();
                addDynamicVar("$" + statId, Component.translatable("gui.stones.studio.suggest.var.custom_stat").getString() + label);
            }
        }

        if (rootEvent != null) {
            scanForDynamicVariables(rootEvent);
        }
    }

    private void scanForDynamicVariables(net.stones.client.gui.editor.TreeNode node) {
        if (node.type == net.stones.client.gui.editor.TreeNode.Type.ACTION && node.jsonData != null && node.jsonData.has("type")) {
            String type = node.jsonData.get("type").getAsString();
            switch (type) {
                case "stones:set_variable" -> addActionDynamicVar(node.jsonData, "name", Component.translatable("gui.stones.studio.suggest.var.local_temp").getString(), null);
                case "stones:random" -> addActionDynamicVar(node.jsonData, "into", Component.translatable("gui.stones.studio.suggest.var.roll_result").getString(), "roll");
                case "stones:invoke" -> addActionDynamicVar(node.jsonData, "save_result_to", Component.translatable("gui.stones.studio.suggest.var.reflection_result").getString(), null);
                case "stones:new" -> addActionDynamicVar(node.jsonData, "save_to", Component.translatable("gui.stones.studio.suggest.var.new_object").getString(), null);
                case "stones:get_persistent_var" -> addActionDynamicVar(node.jsonData, "into", Component.translatable("gui.stones.studio.suggest.var.nbt_value").getString(), null);
                case "stones:get_attribute" -> addActionDynamicVar(node.jsonData, "into", Component.translatable("gui.stones.studio.suggest.var.attribute_value").getString(), null);
                case "stones:for_each" -> addActionDynamicVar(node.jsonData, "as", Component.translatable("gui.stones.studio.suggest.var.loop_element").getString(), null);
                case "stones:remove_random_enchantment" -> addActionDynamicVar(node.jsonData, "save_level_to", Component.translatable("gui.stones.studio.suggest.var.sacrificed_level").getString(), null);
                case "stones:find_blocks" -> {
                    String baseVar = addActionDynamicVar(node.jsonData, "save_to", Component.translatable("gui.stones.studio.suggest.var.found_blocks").getString(), "found_blocks");
                    if (baseVar != null) {
                        addDynamicVar("$" + baseVar + "_count", Component.translatable("gui.stones.studio.suggest.var.found_blocks_count").getString());
                    }
                }
            }
        }
        for (net.stones.client.gui.editor.TreeNode child : node.children) {
            scanForDynamicVariables(child);
        }
    }

    private String addActionDynamicVar(com.google.gson.JsonObject json, String key, String desc, String defaultVal) {
        String varName = defaultVal;
        if (json.has(key)) {
            String val = json.get(key).getAsString().trim();
            if (!val.isEmpty()) {
                varName = val;
            }
        }
        if (varName != null && !varName.isEmpty()) {
            if (varName.startsWith("$")) varName = varName.substring(1);
            if (varName.contains(".")) {
                varName = varName.substring(0, varName.indexOf('.'));
            }
            addDynamicVar("$" + varName, desc);
            return varName;
        }
        return null;
    }

    private void addDynamicVar(String name, String desc) {
        if (!this.dynamicVarSuggestions.contains(name)) {
            this.dynamicVarSuggestions.add(name);
        }
        this.dynamicVarDescriptions.put(name, desc);
    }

    protected void updateFilteredSuggestions(String input) {
        this.filteredSuggestions.clear();
        String cleanInput = input.trim().toLowerCase();

        // Falls die Eingabe ein '$' enthält, schalten wir live auf Variablen-Autocomplete um!
        if (cleanInput.contains("$")) {
            populateDynamicVariables();
            
            int lastDollar = cleanInput.lastIndexOf('$');
            String varQuery = cleanInput.substring(lastDollar);

            String baseVarQuery = varQuery;
            if (varQuery.contains(".")) {
                baseVarQuery = varQuery.substring(0, varQuery.indexOf('.'));
            }

            for (String suggestion : this.dynamicVarSuggestions) {
                if (baseVarQuery.isEmpty() || suggestion.toLowerCase().startsWith(baseVarQuery)) {
                    this.filteredSuggestions.add(suggestion);
                }
            }
        } else {
            // Klassischer Filter für statische Registrierungen (Attribute, Sounds, etc.)
            for (String suggestion : this.suggestions) {
                if (cleanInput.isEmpty() || suggestion.toLowerCase().startsWith(cleanInput)) {
                    this.filteredSuggestions.add(suggestion);
                }
            }
        }

        // Selektions-Index validieren
        if (this.selectedSuggestionIndex >= this.filteredSuggestions.size()) {
            this.selectedSuggestionIndex = Math.max(0, this.filteredSuggestions.size() - 1);
        }

        // Scroll-Offset validieren
        if (this.scrollOffset + MAX_VISIBLE_SUGGESTIONS > this.filteredSuggestions.size()) {
            this.scrollOffset = Math.max(0, this.filteredSuggestions.size() - MAX_VISIBLE_SUGGESTIONS);
        }
    }

    protected void selectSuggestion(String value) {
        String currentInput = this.getValue().trim();
        
        // Fügt die Variable an der Cursorstelle ein, falls davor normaler Text war
        if (currentInput.contains("$")) {
            int lastDollar = currentInput.lastIndexOf('$');
            String prefix = currentInput.substring(0, lastDollar);
            
            if (currentInput.substring(lastDollar).contains(".")) {
                String suffix = currentInput.substring(currentInput.indexOf('.'));
                this.setValue(prefix + value + suffix);
            } else {
                this.setValue(prefix + value);
            }
        } else {
            this.setValue(value);
        }
        
        this.showSuggestions = false;
        this.shouldIgnoreFocusRequest = true; // Signalisiert dem Focus-System den temporären Abbruch
        this.setFocused(false);
        
        // Säubere Fokus-Verweise im globalen Screen-Zustand
        if (this.screen.getFocused() == this) {
            this.screen.setFocused(null);
        }
        if (this.screen.propertiesSection.getIconModal() != null) {
            this.screen.propertiesSection.getIconModal().setFocused(null);
        }
    }

    @Override
    public void setFocused(boolean focused) {
        if (focused && this.shouldIgnoreFocusRequest) {
            this.shouldIgnoreFocusRequest = false;
            super.setFocused(false); // Erzwingt Unfokussiertheit gegen den Minecraft-Refokus-Bug!
            this.showSuggestions = false;
            return;
        }
        
        super.setFocused(focused);
        this.showSuggestions = focused;
        if (focused) {
            this.updateFilteredSuggestions(this.getValue());
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!this.visible) return false;
        
        if (super.isMouseOver(mouseX, mouseY)) {
            return true;
        }
        
        // Erweitert die Hitbox des Widgets, um Klicks auf die Vorschlags-Liste abzufangen
        if (this.showSuggestions && !this.filteredSuggestions.isEmpty()) {
            int visibleCount = Math.min(MAX_VISIBLE_SUGGESTIONS, this.filteredSuggestions.size());
            int dropdownHeight = visibleCount * 12 + 4;
            return mouseX >= this.getX() && mouseX < this.getX() + this.width &&
                   mouseY >= this.getY() + this.height && mouseY < this.getY() + this.height + dropdownHeight;
        }
        
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible) return false;

        // Wenn wir direkt in das eigentliche Textfeld klicken, brechen wir jegliches Ignorieren ab
        if (super.isMouseOver(mouseX, mouseY)) {
            this.shouldIgnoreFocusRequest = false;
        }

        // Klicks auf die Vorschlags-Liste abfangen
        if (this.showSuggestions && !this.filteredSuggestions.isEmpty()) {
            int startY = this.getY() + this.height + 1;
            int visibleCount = Math.min(MAX_VISIBLE_SUGGESTIONS, this.filteredSuggestions.size());
            int dropdownHeight = visibleCount * 12 + 4;

            if (mouseX >= this.getX() && mouseX < this.getX() + this.width &&
                mouseY >= startY && mouseY < startY + dropdownHeight && button == 0) {
                
                int clickedIndex = (int) ((mouseY - startY - 2) / 12);
                int actualIndex = clickedIndex + this.scrollOffset;
                if (actualIndex >= 0 && actualIndex < this.filteredSuggestions.size()) {
                    this.selectSuggestion(this.filteredSuggestions.get(actualIndex));
                }
                return true;
            }
        }

        boolean clicked = super.mouseClicked(mouseX, mouseY, button);
        if (clicked) {
            this.showSuggestions = true;
            this.updateFilteredSuggestions(this.getValue());
        }
        return clicked;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.visible || !this.isActive()) return false;

        if (this.showSuggestions && !this.filteredSuggestions.isEmpty()) {
            if (keyCode == 264) { // PFEIL RUNTER
                this.selectedSuggestionIndex = (this.selectedSuggestionIndex + 1) % this.filteredSuggestions.size();
                if (this.selectedSuggestionIndex >= this.scrollOffset + MAX_VISIBLE_SUGGESTIONS) {
                    this.scrollOffset = this.selectedSuggestionIndex - MAX_VISIBLE_SUGGESTIONS + 1;
                } else if (this.selectedSuggestionIndex < this.scrollOffset) {
                    this.scrollOffset = this.selectedSuggestionIndex;
                }
                return true;
            } else if (keyCode == 265) { // PFEIL RAUF
                this.selectedSuggestionIndex = (this.selectedSuggestionIndex - 1 + this.filteredSuggestions.size()) % this.filteredSuggestions.size();
                if (this.selectedSuggestionIndex < this.scrollOffset) {
                    this.scrollOffset = this.selectedSuggestionIndex;
                } else if (this.selectedSuggestionIndex >= this.scrollOffset + MAX_VISIBLE_SUGGESTIONS) {
                    this.scrollOffset = this.selectedSuggestionIndex - MAX_VISIBLE_SUGGESTIONS + 1;
                }
                return true;
            } else if (keyCode == 257 || keyCode == 258) { // ENTER oder TAB -> Vorschlag auswählen
                this.selectSuggestion(this.filteredSuggestions.get(this.selectedSuggestionIndex));
                return true;
            } else if (keyCode == 256) { // ESC -> Vorschläge einklappen
                this.showSuggestions = false;
                return true;
            }
        }

        boolean handled = super.keyPressed(keyCode, scanCode, modifiers);
        if (handled) {
            this.updateFilteredSuggestions(this.getValue());
        }
        return handled;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!this.visible) return false;

        if (this.showSuggestions && !this.filteredSuggestions.isEmpty()) {
            int startY = this.getY() + this.height + 1;
            int visibleCount = Math.min(MAX_VISIBLE_SUGGESTIONS, this.filteredSuggestions.size());
            int dropdownHeight = visibleCount * 12 + 4;

            if (mouseX >= this.getX() && mouseX < this.getX() + this.width &&
                mouseY >= startY && mouseY < startY + dropdownHeight) {
                
                if (this.filteredSuggestions.size() > MAX_VISIBLE_SUGGESTIONS) {
                    int maxScroll = this.filteredSuggestions.size() - MAX_VISIBLE_SUGGESTIONS;
                    this.scrollOffset = Math.max(0, Math.min(maxScroll, this.scrollOffset - (int) Math.signum(delta)));
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            this.showSuggestions = false;
            return;
        }
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        this.renderSuggestions(graphics, mouseX, mouseY);
    }

    private void renderSuggestions(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!this.showSuggestions || this.filteredSuggestions.isEmpty()) return;

        int dropdownX = this.getX();
        int dropdownY = this.getY() + this.height + 1;
        int dropdownW = this.width;
        int visibleCount = Math.min(MAX_VISIBLE_SUGGESTIONS, this.filteredSuggestions.size());
        int dropdownH = visibleCount * 12 + 4;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 510);
        RenderSystem.disableDepthTest();

        graphics.fill(dropdownX, dropdownY, dropdownX + dropdownW, dropdownY + dropdownH, 0xFA18181B);
        graphics.renderOutline(dropdownX, dropdownY, dropdownW, dropdownH, 0xFF444449);

        for (int i = 0; i < visibleCount; i++) {
            int actualIndex = i + this.scrollOffset;
            if (actualIndex >= this.filteredSuggestions.size()) break;

            String text = this.filteredSuggestions.get(actualIndex);
            int itemY = dropdownY + 2 + (i * 12);
            
            boolean isHovered = mouseX >= dropdownX && mouseX < dropdownX + dropdownW &&
                               mouseY >= itemY && mouseY < itemY + 12;
            boolean isSelected = (actualIndex == this.selectedSuggestionIndex);

            if (isSelected) {
                graphics.fill(dropdownX + 1, itemY, dropdownX + dropdownW - 1, itemY + 12, 0x33FFAA00);
            } else if (isHovered) {
                graphics.fill(dropdownX + 1, itemY, dropdownX + dropdownW - 1, itemY + 12, 0x1AFFFFFF);
            }

            if (isHovered) {
                screen.queueTooltip(getSuggestionTooltip(text), mouseX, mouseY);
            }

            int textColor = getSuggestionColor(text, isSelected, isHovered);
            
            String displayTxt = text;
            int maxTextW = dropdownW - 8;
            if (this.screen.getFont().width(displayTxt) > maxTextW) {
                displayTxt = this.screen.getFont().plainSubstrByWidth(displayTxt, maxTextW - 8) + "...";
            }
            
            graphics.drawString(this.screen.getFont(), displayTxt, dropdownX + 4, itemY + 2, textColor, false);
        }

        RenderSystem.enableDepthTest();
        graphics.pose().popPose();
    }

    // =========================================================================
    // SUB-SPEZIALISIERUNGEN (FÜR MINECRAFT REGISTRIERUNGEN)
    // =========================================================================

    public static class UniversalSuggestField extends StudioSuggestTextField {
        public UniversalSuggestField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message) {
            super(screen, font, x, y, width, height, message);
            this.populateSuggestions();
        }

        public UniversalSuggestField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message, Component tooltipText) {
            super(screen, font, x, y, width, height, message, tooltipText);
            this.populateSuggestions();
        }

        @Override
        protected void populateSuggestions() {
            this.suggestions.clear();
            // Startet leer. Die $ Erkennung in der Basisklasse übernimmt das Laden bei Eingabe selbst.
        }
    }

    public static class VariableByNameSuggestField extends StudioSuggestTextField {
        private final net.stones.client.gui.editor.TreeNode contextNode;
        private final java.util.Map<String, String> varDescriptions = new java.util.HashMap<>();

        public VariableByNameSuggestField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message, net.stones.client.gui.editor.TreeNode contextNode) {
            super(screen, font, x, y, width, height, message, Component.translatable("gui.stones.studio.suggest.varname_no_dollar"));
            this.contextNode = contextNode;
            this.populateSuggestions();
        }

        @Override
        protected Component getSuggestionTooltip(String suggestion) {
            String desc = varDescriptions.getOrDefault(suggestion, "");
            net.minecraft.network.chat.MutableComponent tooltip = Component.literal("")
                .append(Component.literal(suggestion).withStyle(ChatFormatting.GOLD));
            if (!desc.isEmpty()) {
                tooltip.append(Component.literal("\n" + desc).withStyle(ChatFormatting.GRAY));
            }
            tooltip.append(Component.translatable("gui.stones.studio.suggest.no_dollar_warning").withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
            return tooltip;
        }

        @Override
        protected void updateFilteredSuggestions(String input) {
            this.filteredSuggestions.clear();
            String cleanInput = input.trim().toLowerCase();
            
            // Verhindert, dass fälschlicherweise das $ im Filter stört
            if (cleanInput.startsWith("$")) {
                cleanInput = cleanInput.substring(1);
            }

            for (String suggestion : this.suggestions) {
                if (cleanInput.isEmpty() || suggestion.toLowerCase().startsWith(cleanInput)) {
                    this.filteredSuggestions.add(suggestion);
                }
            }

            if (this.selectedSuggestionIndex >= this.filteredSuggestions.size()) {
                this.selectedSuggestionIndex = Math.max(0, this.filteredSuggestions.size() - 1);
            }
            if (this.scrollOffset + MAX_VISIBLE_SUGGESTIONS > this.filteredSuggestions.size()) {
                this.scrollOffset = Math.max(0, this.filteredSuggestions.size() - MAX_VISIBLE_SUGGESTIONS);
            }
        }

        @Override
        protected void populateSuggestions() {
            this.suggestions.clear();
            this.varDescriptions.clear();

            // Globale Variablen ohne $ eintragen
            addVar("player", Component.translatable("gui.stones.studio.suggest.var.player").getString());
            addVar("level", Component.translatable("gui.stones.studio.suggest.var.level").getString());
            addVar("playerHealth", Component.translatable("gui.stones.studio.suggest.var.playerHealth").getString());
            addVar("playerLevel", Component.translatable("gui.stones.studio.suggest.var.playerLevel").getString());
            addVar("RuneLevel", Component.translatable("gui.stones.studio.suggest.var.runeLevel").getString());
            addVar("SockLevel", Component.translatable("gui.stones.studio.suggest.var.sockLevel").getString());
            addVar("AmplifyMultiplier", Component.translatable("gui.stones.studio.suggest.var.amplifyMultiplier").getString());
            addVar("runeId", Component.translatable("gui.stones.studio.suggest.var.runeId").getString());

            String trigger = "UNKNOWN";
            net.stones.client.gui.editor.TreeNode rootEvent = null;
            net.stones.client.gui.editor.TreeNode current = contextNode;
            while (current != null) {
                if (current.type == net.stones.client.gui.editor.TreeNode.Type.EVENT) {
                    rootEvent = current;
                    trigger = current.rawId;
                    break;
                }
                current = current.parent;
            }

            if (trigger.equals("ON_ATTACK") || trigger.equals("ON_HURT")) {
                addVar("damage", Component.translatable("gui.stones.studio.suggest.var.damage").getString());
                addVar("victim", Component.translatable("gui.stones.studio.suggest.var.victim").getString());
                addVar("attacker", Component.translatable("gui.stones.studio.suggest.var.attacker").getString());
            }
            if (trigger.equals("ON_PROJECTILE_HIT")) {
                addVar("projectile", Component.translatable("gui.stones.studio.suggest.var.projectile").getString());
                addVar("hitPos", Component.translatable("gui.stones.studio.suggest.var.hitPos").getString());
            }
            if (trigger.equals("ON_BLOCK_BREAK")) {
                addVar("blockPos", Component.translatable("gui.stones.studio.suggest.var.blockPos").getString());
                addVar("blockState", Component.translatable("gui.stones.studio.suggest.var.blockState").getString());
            }

            for (com.google.gson.JsonObject stat : StonesStudioScreen.activeStats) {
                if (stat.has("id")) {
                    String statId = stat.get("id").getAsString();
                    String label = stat.has("label") ? stat.get("label").getAsString() : "Custom Stat";
                    if (label.startsWith("DICT:")) label = Component.translatable(label.substring(5)).getString();
                    addVar(statId, Component.translatable("gui.stones.studio.suggest.var.custom_stat").getString() + label);
                }
            }

            if (rootEvent != null) {
                scanForVariables(rootEvent);
            }
        }

        private void scanForVariables(net.stones.client.gui.editor.TreeNode node) {
            if (node.type == net.stones.client.gui.editor.TreeNode.Type.ACTION && node.jsonData != null && node.jsonData.has("type")) {
                String type = node.jsonData.get("type").getAsString();
                switch (type) {
                    case "stones:set_variable" -> addActionVar(node.jsonData, "name", Component.translatable("gui.stones.studio.suggest.var.local_temp").getString(), null);
                    case "stones:random" -> addActionVar(node.jsonData, "into", Component.translatable("gui.stones.studio.suggest.var.roll_result").getString(), "roll");
                    case "stones:invoke" -> addActionVar(node.jsonData, "save_result_to", Component.translatable("gui.stones.studio.suggest.var.reflection_result").getString(), null);
                    case "stones:new" -> addActionVar(node.jsonData, "save_to", Component.translatable("gui.stones.studio.suggest.var.new_object").getString(), null);
                    case "stones:get_persistent_var" -> addActionVar(node.jsonData, "into", Component.translatable("gui.stones.studio.suggest.var.nbt_value").getString(), null);
                    case "stones:get_attribute" -> addActionVar(node.jsonData, "into", Component.translatable("gui.stones.studio.suggest.var.attribute_value").getString(), null);
                    case "stones:for_each" -> addActionVar(node.jsonData, "as", Component.translatable("gui.stones.studio.suggest.var.loop_element").getString(), null);
                    case "stones:remove_random_enchantment" -> addActionVar(node.jsonData, "save_level_to", Component.translatable("gui.stones.studio.suggest.var.sacrificed_level").getString(), null);
                    case "stones:find_blocks" -> {
                        String baseVar = addActionVar(node.jsonData, "save_to", Component.translatable("gui.stones.studio.suggest.var.found_blocks").getString(), "found_blocks");
                        if (baseVar != null) {
                            addVar(baseVar + "_count", Component.translatable("gui.stones.studio.suggest.var.found_blocks_count").getString());
                        }
                    }
                }
            }
            for (net.stones.client.gui.editor.TreeNode child : node.children) {
                scanForVariables(child);
            }
        }

        private String addActionVar(com.google.gson.JsonObject json, String key, String desc, String defaultVal) {
            String varName = defaultVal;
            if (json.has(key)) {
                String val = json.get(key).getAsString().trim();
                if (!val.isEmpty()) {
                    varName = val;
                }
            }
            if (varName != null && !varName.isEmpty()) {
                if (varName.startsWith("$")) varName = varName.substring(1);
                if (varName.contains(".")) {
                    varName = varName.substring(0, varName.indexOf('.'));
                }
                addVar(varName, desc);
                return varName;
            }
            return null;
        }

        private void addVar(String name, String desc) {
            if (!this.suggestions.contains(name)) {
                this.suggestions.add(name);
            }
            this.varDescriptions.put(name, desc);
        }
    }

    public static class AttributeSuggestField extends StudioSuggestTextField {
        public AttributeSuggestField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message) {
            super(screen, font, x, y, width, height, message);
            this.populateSuggestions();
        }

        public AttributeSuggestField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message, Component tooltipText) {
            super(screen, font, x, y, width, height, message, tooltipText);
            this.populateSuggestions();
        }

        @Override
        protected void populateSuggestions() {
            this.suggestions.clear();
            try {
                for (ResourceLocation rl : ForgeRegistries.ATTRIBUTES.getKeys()) {
                    this.suggestions.add(rl.toString());
                }
                this.suggestions.sort(String::compareTo);
            } catch (Exception e) {
                this.suggestions.add("minecraft:generic.max_health");
                this.suggestions.add("minecraft:generic.movement_speed");
                this.suggestions.add("minecraft:generic.attack_damage");
                this.suggestions.add("minecraft:generic.armor");
                this.suggestions.add("minecraft:generic.luck");
            }
        }
    }

    public static class SoundSuggestField extends StudioSuggestTextField {
        public SoundSuggestField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message) {
            super(screen, font, x, y, width, height, message);
            this.populateSuggestions();
        }

        public SoundSuggestField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message, Component tooltipText) {
            super(screen, font, x, y, width, height, message, tooltipText);
            this.populateSuggestions();
        }

        @Override
        protected void populateSuggestions() {
            this.suggestions.clear();
            try {
                for (ResourceLocation rl : ForgeRegistries.SOUND_EVENTS.getKeys()) {
                    this.suggestions.add(rl.toString());
                }
                this.suggestions.sort(String::compareTo);
            } catch (Exception e) {
                this.suggestions.add("minecraft:entity.experience_orb.pickup");
                this.suggestions.add("minecraft:entity.player.levelup");
                this.suggestions.add("minecraft:block.amethyst_block.chime");
                this.suggestions.add("stones:echo_trader_emerge");
                this.suggestions.add("stones:shrine_bind");
            }
        }
    }

    public static class EntitySuggestField extends StudioSuggestTextField {
        public EntitySuggestField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message) {
            super(screen, font, x, y, width, height, message);
            this.populateSuggestions();
        }

        public EntitySuggestField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message, Component tooltipText) {
            super(screen, font, x, y, width, height, message, tooltipText);
            this.populateSuggestions();
        }

        @Override
        protected void populateSuggestions() {
            this.suggestions.clear();
            try {
                for (ResourceLocation rl : ForgeRegistries.ENTITY_TYPES.getKeys()) {
                    this.suggestions.add(rl.toString());
                }
                this.suggestions.sort(String::compareTo);
            } catch (Exception e) {
                this.suggestions.add("minecraft:player");
                this.suggestions.add("minecraft:zombie");
                this.suggestions.add("minecraft:skeleton");
                this.suggestions.add("stones:echo_trader");
            }
        }
    }
    
    public static class EffectSuggestField extends StudioSuggestTextField {
        public EffectSuggestField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message) {
            super(screen, font, x, y, width, height, message);
            this.populateSuggestions();
        }

        public EffectSuggestField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message, Component tooltipText) {
            super(screen, font, x, y, width, height, message, tooltipText);
            this.populateSuggestions();
        }

        @Override
        protected void populateSuggestions() {
            this.suggestions.clear();
            try {
                for (ResourceLocation rl : ForgeRegistries.MOB_EFFECTS.getKeys()) {
                    this.suggestions.add(rl.toString());
                }
                this.suggestions.sort(String::compareTo);
            } catch (Exception e) {
                this.suggestions.add("minecraft:speed");
                this.suggestions.add("minecraft:slowness");
                this.suggestions.add("minecraft:haste");
                this.suggestions.add("minecraft:strength");
                this.suggestions.add("minecraft:regeneration");
                this.suggestions.add("minecraft:resistance");
            }
        }
    }
    
    public static class ParticleSuggestField extends StudioSuggestTextField {
        public ParticleSuggestField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message) {
            super(screen, font, x, y, width, height, message);
            this.populateSuggestions();
        }

        public ParticleSuggestField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message, Component tooltipText) {
            super(screen, font, x, y, width, height, message, tooltipText);
            this.populateSuggestions();
        }

        @Override
        protected void populateSuggestions() {
            this.suggestions.clear();
            try {
                for (ResourceLocation rl : ForgeRegistries.PARTICLE_TYPES.getKeys()) {
                    this.suggestions.add(rl.toString());
                }
                this.suggestions.sort(String::compareTo);
            } catch (Exception e) {
                this.suggestions.add("minecraft:flame");
                this.suggestions.add("minecraft:soul_fire_flame");
                this.suggestions.add("minecraft:smoke");
                this.suggestions.add("minecraft:large_smoke");
                this.suggestions.add("minecraft:sweep_attack");
                this.suggestions.add("minecraft:crit");
                this.suggestions.add("minecraft:magic_crit");
                this.suggestions.add("minecraft:enchant");
                this.suggestions.add("stones:echo_moth");
            }
        }
    }

    public static class IconSuggestField extends StudioSuggestTextField {
        public IconSuggestField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message) {
            super(screen, font, x, y, width, height, message);
            this.populateSuggestions();
        }

        public IconSuggestField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message, Component tooltipText) {
            super(screen, font, x, y, width, height, message, tooltipText);
            this.populateSuggestions();
        }

        @Override
        protected void populateSuggestions() {
            this.suggestions.clear();
            try {
                var resourceManager = Minecraft.getInstance().getResourceManager();
                var resources = resourceManager.listResources("textures", rl -> rl.getPath().endsWith(".png"));
                
                for (ResourceLocation rl : resources.keySet()) {
                    this.suggestions.add(rl.toString());
                }
                this.suggestions.sort(String::compareTo);
            } catch (Exception e) {
                this.suggestions.add("stones:textures/block/runestone.png");
                this.suggestions.add("stones:textures/items/rune_minor.png");
                this.suggestions.add("stones:textures/items/rune_major.png");
                this.suggestions.add("stones:textures/items/rune_milestone.png");
                this.suggestions.add("stones:textures/gui/echo_trader_head.png");
            }
        }
    }

    public static class VariableSuggestField extends StudioSuggestTextField {
        private static final int TYPE_GLOBAL = 0;
        private static final int TYPE_CONTEXT = 1;
        private static final int TYPE_STAT = 2;
        private static final int TYPE_ACTION_VAR = 3;

        private final net.stones.client.gui.editor.TreeNode contextNode;
        private final java.util.Map<String, String> varDescriptions = new java.util.HashMap<>();
        private final java.util.Map<String, Integer> varTypes = new java.util.HashMap<>();

        public VariableSuggestField(StonesStudioScreen screen, Font font, int x, int y, int width, int height, Component message, net.stones.client.gui.editor.TreeNode contextNode) {
            super(screen, font, x, y, width, height, message);
            this.contextNode = contextNode;
            this.populateSuggestions();
        }

        @Override
        protected Component getSuggestionTooltip(String suggestion) {
            String desc = varDescriptions.getOrDefault(suggestion, "");
            int color = getSuggestionColor(suggestion, false, false) & 0xFFFFFF;
            
            net.minecraft.network.chat.MutableComponent tooltip = Component.literal("")
                .append(Component.literal(suggestion).withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(color)));
            
            if (!desc.isEmpty()) {
                tooltip.append(Component.literal("\n" + desc).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            
            String currentInput = this.getValue().trim();
            if (currentInput.contains(".")) {
                tooltip.append(Component.literal("\n").append(Component.translatable("gui.stones.studio.suggest.complex_call", suggestion))
                       .withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.ITALIC));
            }
            
            return tooltip;
        }

        @Override
        protected int getSuggestionColor(String suggestion, boolean isSelected, boolean isHovered) {
            if (isSelected) return 0xFFFFAA00;
            
            int type = varTypes.getOrDefault(suggestion, TYPE_GLOBAL);
            return switch (type) {
                case TYPE_STAT -> 0xFF55FF55;
                case TYPE_ACTION_VAR -> 0xFF55FFFF;
                case TYPE_CONTEXT -> 0xFFFFAA55;
                default -> 0xFFCCCCCC;
            };
        }

        @Override
        protected void updateFilteredSuggestions(String input) {
            this.filteredSuggestions.clear();
            String cleanInput = input.trim().toLowerCase();

            if (!cleanInput.isEmpty() && !cleanInput.startsWith("$") && !cleanInput.startsWith("-")) {
                try {
                    Double.parseDouble(cleanInput);
                    this.selectedSuggestionIndex = 0;
                    this.scrollOffset = 0;
                    return;
                } catch (Exception ignored) {}
            }

            String baseInput = cleanInput;
            if (cleanInput.contains(".")) {
                baseInput = cleanInput.substring(0, cleanInput.indexOf('.'));
            }

            for (String suggestion : this.suggestions) {
                if (baseInput.isEmpty() || suggestion.toLowerCase().startsWith(baseInput)) {
                    this.filteredSuggestions.add(suggestion);
                }
            }

            if (this.selectedSuggestionIndex >= this.filteredSuggestions.size()) {
                this.selectedSuggestionIndex = Math.max(0, this.filteredSuggestions.size() - 1);
            }

            if (this.scrollOffset + MAX_VISIBLE_SUGGESTIONS > this.filteredSuggestions.size()) {
                this.scrollOffset = Math.max(0, this.filteredSuggestions.size() - MAX_VISIBLE_SUGGESTIONS);
            }
        }

        @Override
        protected void selectSuggestion(String value) {
            String currentInput = this.getValue().trim();
            if (currentInput.contains(".")) {
                String suffix = currentInput.substring(currentInput.indexOf('.'));
                super.selectSuggestion(value + suffix);
            } else {
                super.selectSuggestion(value);
            }
        }

        private void addVar(String name, String desc, int type) {
            if (!this.suggestions.contains(name)) {
                this.suggestions.add(name);
            }
            this.varDescriptions.put(name, desc);
            this.varTypes.put(name, type);
        }

        @Override
        protected void populateSuggestions() {
            this.suggestions.clear();
            this.varDescriptions.clear();
            this.varTypes.clear();

            addVar("$player", Component.translatable("gui.stones.studio.suggest.var.player").getString(), TYPE_GLOBAL);
            addVar("$level", Component.translatable("gui.stones.studio.suggest.var.level").getString(), TYPE_GLOBAL);
            addVar("$playerHealth", Component.translatable("gui.stones.studio.suggest.var.playerHealth").getString(), TYPE_GLOBAL);
            addVar("$playerLevel", Component.translatable("gui.stones.studio.suggest.var.playerLevel").getString(), TYPE_GLOBAL);
            addVar("$RuneLevel", Component.translatable("gui.stones.studio.suggest.var.runeLevel").getString(), TYPE_GLOBAL);
            addVar("$SockLevel", Component.translatable("gui.stones.studio.suggest.var.sockLevel").getString(), TYPE_GLOBAL);
            addVar("$AmplifyMultiplier", Component.translatable("gui.stones.studio.suggest.var.amplifyMultiplier").getString(), TYPE_GLOBAL);
            addVar("$runeId", Component.translatable("gui.stones.studio.suggest.var.runeId").getString(), TYPE_GLOBAL);

            String trigger = "UNKNOWN";
            net.stones.client.gui.editor.TreeNode rootEvent = null;
            net.stones.client.gui.editor.TreeNode current = contextNode;
            while (current != null) {
                if (current.type == net.stones.client.gui.editor.TreeNode.Type.EVENT) {
                    rootEvent = current;
                    trigger = current.rawId;
                    break;
                }
                current = current.parent;
            }

            if (trigger.equals("ON_ATTACK") || trigger.equals("ON_HURT")) {
                addVar("$damage", Component.translatable("gui.stones.studio.suggest.var.damage").getString(), TYPE_CONTEXT);
                addVar("$victim", Component.translatable("gui.stones.studio.suggest.var.victim").getString(), TYPE_CONTEXT);
                addVar("$attacker", Component.translatable("gui.stones.studio.suggest.var.attacker").getString(), TYPE_CONTEXT);
            }
            if (trigger.equals("ON_PROJECTILE_HIT")) {
                addVar("$projectile", Component.translatable("gui.stones.studio.suggest.var.projectile").getString(), TYPE_CONTEXT);
                addVar("$hitPos", Component.translatable("gui.stones.studio.suggest.var.hitPos").getString(), TYPE_CONTEXT);
            }
            if (trigger.equals("ON_BLOCK_BREAK")) {
                addVar("$blockPos", Component.translatable("gui.stones.studio.suggest.var.blockPos").getString(), TYPE_CONTEXT);
                addVar("$blockState", Component.translatable("gui.stones.studio.suggest.var.blockState").getString(), TYPE_CONTEXT);
            }

            for (com.google.gson.JsonObject stat : StonesStudioScreen.activeStats) {
                if (stat.has("id")) {
                    String statId = stat.get("id").getAsString();
                    String label = stat.has("label") ? stat.get("label").getAsString() : "Custom Stat";
                    if (label.startsWith("DICT:")) label = Component.translatable(label.substring(5)).getString();
                    addVar("$" + statId, Component.translatable("gui.stones.studio.suggest.var.custom_stat").getString() + label, TYPE_STAT);
                }
            }

            if (rootEvent != null) {
                scanForVariables(rootEvent);
            }
        }

        private void scanForVariables(net.stones.client.gui.editor.TreeNode node) {
            if (node.type == net.stones.client.gui.editor.TreeNode.Type.ACTION && node.jsonData != null && node.jsonData.has("type")) {
                String type = node.jsonData.get("type").getAsString();
                switch (type) {
                    case "stones:set_variable" -> addActionVar(node.jsonData, "name", Component.translatable("gui.stones.studio.suggest.var.local_temp").getString(), null);
                    case "stones:random" -> addActionVar(node.jsonData, "into", Component.translatable("gui.stones.studio.suggest.var.roll_result").getString(), "roll");
                    case "stones:invoke" -> addActionVar(node.jsonData, "save_result_to", Component.translatable("gui.stones.studio.suggest.var.reflection_result").getString(), null);
                    case "stones:new" -> addActionVar(node.jsonData, "save_to", Component.translatable("gui.stones.studio.suggest.var.new_object").getString(), null);
                    case "stones:get_persistent_var" -> addActionVar(node.jsonData, "into", Component.translatable("gui.stones.studio.suggest.var.nbt_value").getString(), null);
                    case "stones:get_attribute" -> addActionVar(node.jsonData, "into", Component.translatable("gui.stones.studio.suggest.var.attribute_value").getString(), null);
                    case "stones:for_each" -> addActionVar(node.jsonData, "as", Component.translatable("gui.stones.studio.suggest.var.loop_element").getString(), null);
                    case "stones:remove_random_enchantment" -> addActionVar(node.jsonData, "save_level_to", Component.translatable("gui.stones.studio.suggest.var.sacrificed_level").getString(), null);
                    case "stones:find_blocks" -> {
                        String baseVar = addActionVar(node.jsonData, "save_to", Component.translatable("gui.stones.studio.suggest.var.found_blocks").getString(), "found_blocks");
                        if (baseVar != null) {
                            addVar("$" + baseVar + "_count", Component.translatable("gui.stones.studio.suggest.var.found_blocks_count").getString(), TYPE_ACTION_VAR);
                        }
                    }
                }
            }
            
            for (net.stones.client.gui.editor.TreeNode child : node.children) {
                scanForVariables(child);
            }
        }

        private String addActionVar(com.google.gson.JsonObject json, String key, String desc, String defaultVal) {
            String varName = defaultVal;
            if (json.has(key)) {
                String val = json.get(key).getAsString().trim();
                if (!val.isEmpty()) {
                    varName = val;
                }
            }
            if (varName != null && !varName.isEmpty()) {
                if (varName.startsWith("$")) varName = varName.substring(1);
                if (varName.contains(".")) {
                    varName = varName.substring(0, varName.indexOf('.'));
                }
                addVar("$" + varName, desc, TYPE_ACTION_VAR);
                return varName;
            }
            return null;
        }
    }
}