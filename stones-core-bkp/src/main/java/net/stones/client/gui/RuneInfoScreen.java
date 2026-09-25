package net.stones.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.stones.enchantment.AmplifyEnchantment;
import net.stones.enchantment.RuneEnchantment;
import net.stones.enchantment.RuneStat;
import net.stones.item.StoneItem;
import net.stones.util.RuneCalculator;

import java.util.*;

/**
 * Zeigt aktive Rune-Boni im Stil eines dreigeteilten Panels in sauberer Vanilla-Optik (Core-Variante).
 * 100% PNG-frei, performant und barrierefrei.
 */
public class RuneInfoScreen extends Screen {

    private final ItemStack runeStack;
    private final boolean isCorrupted;
    private final boolean isEmptyRune;
    private final double amplifyMultiplier;

    // --- RETAINED MODE DATA MODEL (Row Cache) ---
    private final List<RenderableRow> allCompiledRows = new ArrayList<>();
    private final List<RenderableRow> visibleRows = new ArrayList<>();
    private final List<FormattedCharSequence> squishedStats = new ArrayList<>();

    // --- KINETIC SMOOTH SCROLLING ---
    private float scrollTarget = 0.0F;
    private float scrollCurrent = 0.0F;
    private int totalContentHeight = 0;

    private EditBox searchBox;

    private int paneY;
    private int paneHeight;

    public RuneInfoScreen(ItemStack stack) {
        super(stack.getHoverName());
        this.runeStack = stack;

        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(runeStack);
        this.isEmptyRune = enchants.isEmpty();
        this.isCorrupted = enchants.keySet().stream().anyMatch(Enchantment::isCurse);

        int ampLvl = 0;
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            if (entry.getKey() instanceof AmplifyEnchantment) {
                ampLvl = entry.getValue();
                break;
            }
        }
        this.amplifyMultiplier = AmplifyEnchantment.getMultiplier(ampLvl);
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.paneY = centerY - 80;
        this.paneHeight = 150;

        // Sucheingabe im mittleren Panel unten
        this.searchBox = new EditBox(this.font, centerX - 85, centerY + 48, 170, 14, Component.translatable("gui.stones.rune_info.search_narration"));
        this.searchBox.setHint(Component.translatable("gui.stones.rune_info.search_placeholder"));
        this.searchBox.setMaxLength(30);
        this.searchBox.setBordered(true);
        this.searchBox.setValue("");
        this.searchBox.setTextColor(0xFFFFFFFF);
        this.searchBox.setResponder(this::onSearchQueryChanged);
        this.addRenderableWidget(this.searchBox);

        // Schließen-Knopf am unteren Rand
        this.addRenderableWidget(Button.builder(Component.translatable("gui.stones.leaderboard.back"), (btn) -> this.onClose())
                .bounds(centerX - 50, centerY + 76, 100, 18).build());

        this.compileData("");
        this.performStatSquishing();
    }

    private void onSearchQueryChanged(String query) {
        this.compileData(query);
        this.scrollTarget = 0.0F;
    }

    private void compileData(String query) {
        this.allCompiledRows.clear();
        String filter = query.trim().toLowerCase();

        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(runeStack);

        int ampLvl = 0;
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            if (entry.getKey() instanceof AmplifyEnchantment) {
                ampLvl = entry.getValue();
                break;
            }
        }

        if (ampLvl > 0 && filter.isEmpty()) {
            MutableComponent ampHeader = Component.translatable("gui.stones.rune_info.amplify_prefix")
                    .append(" ")
                    .append(Component.translatable("enchantment.level." + ampLvl))
                    .withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_PURPLE);
            addRuneRow(this.allCompiledRows, ampHeader, true, false, 14);

            double percentBonus = (amplifyMultiplier - 1.0) * 100;
            Component ampSub = Component.translatable("gui.stones.rune_info.potential", String.format(Locale.ROOT, "%.1f", percentBonus))
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
            addRuneRow(this.allCompiledRows, ampSub, false, false, 10);
            
            this.allCompiledRows.add(new RenderableRow(Component.empty().getVisualOrderText(), false, false, 6));
        }

        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            if (entry.getKey() instanceof RuneEnchantment rune) {
                int lvl = entry.getValue();
                Component fullname = rune.getFullname(lvl);
                String runeNameLower = fullname.getString().toLowerCase();

                List<RenderableRow> tempRuneRows = new ArrayList<>();
                boolean matchesFilter = filter.isEmpty() || runeNameLower.contains(filter);

                if (!this.allCompiledRows.isEmpty()) {
                    tempRuneRows.add(new RenderableRow(Component.empty().getVisualOrderText(), false, true, 8));
                }

                ChatFormatting headingColor = rune.isCurse() ? ChatFormatting.RED : ChatFormatting.GOLD;
                addRuneRow(tempRuneRows, fullname.copy().withStyle(headingColor, ChatFormatting.BOLD), true, false, 12);

                for (RuneStat stat : rune.getStats()) {
                    float val = RuneCalculator.calculateStatValue(stat, lvl, 1, getClientPlayerLevel(), amplifyMultiplier);
                    MutableComponent statLine = Component.literal("  ➤ ").withStyle(ChatFormatting.DARK_GRAY)
                            .append(RuneEnchantment.resolveComponent(stat.label()).copy().withStyle(ChatFormatting.GRAY)).append(": ")
                            .append(Component.literal(String.format("%.1f", val * stat.displayFactor())).withStyle(ChatFormatting.AQUA))
                            .append(RuneEnchantment.resolveComponent(stat.suffix()).copy().withStyle(ChatFormatting.AQUA));
                    addRuneRow(tempRuneRows, statLine, false, false, 10);
                }

                if (rune.targetAttribute != null) {
                    if (rune.type == RuneEnchantment.Type.MAJOR) {
                        double scaledFactor = rune.factor * lvl * amplifyMultiplier;
                        String valStr = (rune.operation != AttributeModifier.Operation.ADDITION) ? String.format("%.1f%%", scaledFactor * 100) : String.format("%.1f", scaledFactor);
                        MutableComponent attrLine = Component.literal("  ➤ ").withStyle(ChatFormatting.DARK_GRAY)
                                .append(Component.translatable("tooltip.stones.scaling_info").withStyle(ChatFormatting.GRAY)).append(": ")
                                .append(Component.literal("+" + valStr).withStyle(ChatFormatting.GOLD))
                                .append(" ").append(Component.translatable(rune.targetAttribute.getDescriptionId()).withStyle(ChatFormatting.WHITE));
                        addRuneRow(tempRuneRows, attrLine, false, false, 10);
                    } else {
                        double previewBonus = (lvl * rune.factor) * amplifyMultiplier;
                        Component attrLine = StoneItem.formatAttributeLine(rune, previewBonus, amplifyMultiplier > 1.0, false);
                        addRuneRow(tempRuneRows, attrLine, false, false, 10);
                    }
                }

                Set<String> addedTriggers = new HashSet<>();
                for (String triggerId : rune.getTriggerIds()) {
                    if (addedTriggers.add(triggerId)) {
                        Component translatedTrig = translateTriggerType(triggerId);
                        MutableComponent triggerLine = Component.literal("  ✦ ").withStyle(ChatFormatting.DARK_PURPLE)
                                .append(translatedTrig.copy().withStyle(ChatFormatting.LIGHT_PURPLE));
                        addRuneRow(tempRuneRows, triggerLine, false, false, 10);
                    }
                }

                Component desc = rune.getCustomDescription(lvl);
                if (desc != null && !desc.getString().isEmpty()) {
                    tempRuneRows.add(new RenderableRow(Component.empty().getVisualOrderText(), false, false, 4));
                    String descStr = desc.getString();
                    if (filter.isEmpty() || descStr.toLowerCase().contains(filter)) {
                        matchesFilter = true;
                    }
                    Component descLine = desc.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
                    addRuneRow(tempRuneRows, descLine, false, false, 9);
                }

                if (matchesFilter) {
                    this.allCompiledRows.addAll(tempRuneRows);
                }
            }
        }

        if (this.allCompiledRows.isEmpty()) {
            addRuneRow(this.allCompiledRows, Component.translatable("gui.stones.rune_info.no_modifiers").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false, false, 12);
        }

        this.visibleRows.clear();
        this.visibleRows.addAll(this.allCompiledRows);

        this.totalContentHeight = 0;
        for (RenderableRow row : this.visibleRows) {
            this.totalContentHeight += row.height;
        }
    }

    private void addRuneRow(List<RenderableRow> list, Component comp, boolean isHeader, boolean isSeparator, int height) {
        int maxTextWidth = 166;
        List<FormattedCharSequence> lines = this.font.split(comp, maxTextWidth);
        for (int i = 0; i < lines.size(); i++) {
            int rowHeight = (i == 0) ? height : 9;
            list.add(new RenderableRow(lines.get(i), isHeader, isSeparator, rowHeight));
        }
    }

    private void performStatSquishing() {
        this.squishedStats.clear();
        Map<String, Float> statSums = new LinkedHashMap<>();
        Map<String, String> statSuffixes = new HashMap<>();
        List<Component> activeMilestones = new ArrayList<>();

        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(runeStack);
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            if (entry.getKey() instanceof RuneEnchantment rune) {
                int lvl = entry.getValue();
                
                if (rune.type == RuneEnchantment.Type.MILESTONE) {
                    activeMilestones.add(rune.getFullname(lvl));
                    continue;
                }
                
                for (RuneStat stat : rune.getStats()) {
                    float val = RuneCalculator.calculateStatValue(stat, lvl, 1, getClientPlayerLevel(), amplifyMultiplier);
                    String rawLabel = stat.label();
                    statSums.put(rawLabel, statSums.getOrDefault(rawLabel, 0.0F) + (val * stat.displayFactor()));
                    statSuffixes.put(rawLabel, stat.suffix());
                }
                if (rune.targetAttribute != null) {
                    double bonus = RuneCalculator.calculateAttributeBonus(rune, lvl, getClientPlayerLevel(), 1, amplifyMultiplier);
                    String rawLabel = "ATTR:" + rune.targetAttribute.getDescriptionId();
                    float displayVal = (float) (rune.operation != AttributeModifier.Operation.ADDITION ? bonus * 100.0 : bonus);
                    statSums.put(rawLabel, statSums.getOrDefault(rawLabel, 0.0F) + displayVal);
                    statSuffixes.put(rawLabel, rune.operation != AttributeModifier.Operation.ADDITION ? "%" : "");
                }
            }
        }

        for (Map.Entry<String, Float> entry : statSums.entrySet()) {
            String rawLabel = entry.getKey();
            Component resolvedLabel;
            if (rawLabel.startsWith("ATTR:")) {
                resolvedLabel = Component.translatable(rawLabel.substring(5));
            } else {
                resolvedLabel = RuneEnchantment.resolveComponent(rawLabel);
            }

            String suffix = statSuffixes.getOrDefault(rawLabel, "");
            Component resolvedSuffix = RuneEnchantment.resolveComponent(suffix);
            String prefix = entry.getValue() >= 0 ? "+" : "";
            String formattedVal = String.format(Locale.ROOT, "%.1f", entry.getValue());

            MutableComponent line = resolvedLabel.copy().withStyle(ChatFormatting.GRAY)
                    .append(": ")
                    .append(Component.literal(prefix + formattedVal).withStyle(amplifyMultiplier > 1.0 ? ChatFormatting.AQUA : ChatFormatting.GOLD))
                    .append(resolvedSuffix);
            
            for (FormattedCharSequence splitLine : this.font.split(line, 94)) {
                this.squishedStats.add(splitLine);
            }
        }

        if (!activeMilestones.isEmpty()) {
            if (!this.squishedStats.isEmpty()) {
                this.squishedStats.add(Component.empty().getVisualOrderText());
            }
            for (Component milestoneName : activeMilestones) {
                MutableComponent line = Component.literal("✦ ").withStyle(ChatFormatting.LIGHT_PURPLE)
                        .append(milestoneName.copy().withStyle(ChatFormatting.DARK_GRAY));
                for (FormattedCharSequence splitLine : this.font.split(line, 94)) {
                    this.squishedStats.add(splitLine);
                }
            }
        }

        if (this.squishedStats.isEmpty()) {
            this.squishedStats.add(Component.translatable("gui.stones.rune_info.no_active_effects").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC).getVisualOrderText());
        }
    }

    private Component translateTriggerType(String triggerId) {
        return Component.translatable("gui.stones.trigger." + triggerId.toLowerCase(Locale.ROOT));
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(gui);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int winX = centerX - 215;
        int winY = centerY - 100;
        int winW = 430;
        int winH = 200;

        // 1. Hauptfenster (Vanilla-Grau mit Bezel-Rahmen)
        gui.fill(winX, winY, winX + winW, winY + winH, 0xFFC6C6C6);
        gui.fill(winX, winY, winX + winW, winY + 2, 0xFFFFFFFF);
        gui.fill(winX, winY, winX + 2, winY + winH, 0xFFFFFFFF);
        gui.fill(winX, winY + winH - 2, winX + winW, winY + winH, 0xFF555555);
        gui.fill(winX + winW - 2, winY, winX + winW, winY + winH, 0xFF555555);

        // Titelanzeige oben
        gui.drawCenteredString(this.font, this.title, centerX, winY + 8, 0x333333);

        // 2. Dreiteilige abgesenkte Panels
        this.renderTriptychPanels(gui, centerX);

        // 3. Linkes Panel: Item-Vorschau (Sauber gerendertes 2D Item)
        this.renderItemArtifact(gui, centerX, centerY);

        // 4. Mittleres Panel: Scrollbare Modifikatoren-Liste
        this.renderMiddlePaneList(gui, centerX);

        // 5. Rechtes Panel: Boni-Zusammenfassung
        this.renderRightPaneSummary(gui, centerX);

        super.render(gui, mouseX, mouseY, partialTicks);
    }

    private void renderTriptychPanels(GuiGraphics gui, int centerX) {
        // Links: Item Vorschau Box
        drawRecessedPanel(gui, centerX - 205, paneY, 100, paneHeight);

        // Mitte: Scrollbare Liste Panel (Dunkler Hintergrund für gute Lesbarkeit der Farben)
        drawRecessedPanel(gui, centerX - 95, paneY, 190, paneHeight);
        gui.fill(centerX - 94, paneY + 1, centerX + 94, paneY + paneHeight - 1, 0xFF1B1B1B);

        // Rechts: Zusammenfassung Panel
        drawRecessedPanel(gui, centerX + 105, paneY, 100, paneHeight);
    }

    private void renderItemArtifact(GuiGraphics gui, int centerX, int centerY) {
        int itemX = centerX - 155;
        int itemY = centerY - 30;

        // Item-Sockelrahmen in der Mitte des linken Panels
        drawRecessedPanel(gui, itemX - 16, itemY - 16, 32, 32);

        // Skaliertes Item rendern
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(itemX, itemY, 100);
        pose.scale(1.5f, 1.5f, 1.0f);
        gui.renderItem(this.runeStack, -8, -8);
        gui.renderItemDecorations(this.font, this.runeStack, -8, -8);
        pose.popPose();

        // Item Name unter der Vorschau
        Component hoverName = this.runeStack.getHoverName();
        List<FormattedCharSequence> splitName = this.font.split(hoverName, 90);
        int nameY = itemY + 24;
        for (FormattedCharSequence line : splitName) {
            int w = this.font.width(line);
            gui.drawString(this.font, line, itemX - (w / 2), nameY, 0x333333, false);
            nameY += 10;
            if (nameY > paneY + paneHeight - 12) break;
        }
    }

    private void renderMiddlePaneList(GuiGraphics gui, int centerX) {
        PoseStack poseStack = gui.pose();

        this.scrollCurrent = this.scrollCurrent + (this.scrollTarget - this.scrollCurrent) * 0.2F;
        if (Math.abs(this.scrollCurrent - this.scrollTarget) < 0.1F) {
            this.scrollCurrent = this.scrollTarget;
        }

        int clipX = centerX - 92;
        int clipY = paneY + 4;
        int clipW = 184;
        int clipH = paneHeight - 32; // Platz für die Suchleiste unten

        gui.enableScissor(clipX, clipY, clipX + clipW, clipY + clipH);

        poseStack.pushPose();
        poseStack.translate(0, -scrollCurrent, 0);

        int currentY = clipY + 2;
        for (RenderableRow row : this.visibleRows) {
            boolean isVisible = (currentY + row.height >= clipY + scrollCurrent) && (currentY <= clipY + scrollCurrent + clipH);

            if (isVisible) {
                if (row.isSeparator) {
                    gui.fill(clipX + 10, currentY + 3, clipX + clipW - 10, currentY + 4, 0xFF555555);
                } else {
                    gui.drawString(this.font, row.component, clipX + 4, currentY, 0xFFFFFFFF, false);
                }
            }
            currentY += row.height;
        }

        poseStack.popPose();
        gui.disableScissor();
    }

    private void renderRightPaneSummary(GuiGraphics gui, int centerX) {
        gui.drawString(this.font, Component.translatable("gui.stones.rune_info.properties").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD), centerX + 112, paneY + 8, 0x333333, false);

        int statY = paneY + 22;
        for (FormattedCharSequence statComp : this.squishedStats) {
            if (statY + 10 > paneY + paneHeight - 4) break;
            gui.drawString(this.font, statComp, centerX + 110, statY, 0x333333, false);
            statY += 10;
        }
    }

    private void drawRecessedPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF8B8B8B);
        g.fill(x, y, x + w, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + h, 0xFF373737);
        g.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int clipH = paneHeight - 32;
        this.scrollTarget = Mth.clamp(this.scrollTarget - (float) (delta * 18.0F), 0.0F, Math.max(0.0F, totalContentHeight - clipH));
        return true;
    }

    private int getClientPlayerLevel() {
        return this.minecraft.player != null ? this.minecraft.player.experienceLevel : 0;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class RenderableRow {
        final FormattedCharSequence component;
        final boolean isHeader;
        final boolean isSeparator;
        final int height;

        RenderableRow(FormattedCharSequence component, boolean isHeader, boolean isSeparator, int height) {
            this.component = component;
            this.isHeader = isHeader;
            this.isSeparator = isSeparator;
            this.height = height;
        }
    }
}