package net.stones.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments; // <-- KORRIGIERTER IMPORT
import net.stones.enchantment.AmplifyEnchantment;
import net.stones.enchantment.RuneEnchantment;
import net.stones.enchantment.RuneStat;
import net.stones.enchantment.behavior.RuneBehavior;
import net.stones.item.StoneItem;
import net.stones.logic.RuneCalculator;

import java.util.*;

/**
 * ARCHITEKTONISCHES MASTERPIECE: RuneInfoScreen (Path of Exile Adaption)
 * Hochperformantes, triptychon-basiertes Render-Modell fuer bis zu 256 Enchantments.
 * Integriert Retained-Mode Caching, Scissor Clipping, Lerp Scrolling und UI Partikel.
 * Volle Internationalisierung (I18n) fuer mehrsprachigen Support.
 */
public class RuneInfoScreen extends Screen {

    // --- DECO ASSETS ---
    private static final ResourceLocation BG_NEBULA = new ResourceLocation("stones", "textures/gui/shrine_nebula.png");

    // --- GRIMDARK COLOR SYSTEM (Blanchitsu Palette) ---
    private static final int COL_DEEP_BLACK    = 0xEE050505; // Opazitaetsreduziertes Schwarzbraun (Hintergrund)
    private static final int COL_DARK_BROWN    = 0xFF1E1914; // Harte Schatten/Umrandung
    private static final int COL_SICKLY_YELLOW = 0xFFE4E595; // Goldenes Verfall-Gelb (Headings)
    private static final int COL_OFF_WHITE     = 0xFFF5F5E0; // Lesbarer Basistext
    private static final int COL_ACCENT_RED    = 0xFFA01414; // Fluch des Bindens / Kritische Highlights
    private static final int COL_CYAN_GLOW     = 0xFF00FFFF; // Magisches Leuchten (Amplify)

    private final ItemStack runeStack;
    private final boolean isCorrupted; // Curse of Binding Aktivitaet
    private final double amplifyMultiplier;

    // --- RETAINED MODE DATA MODEL (Row Cache) ---
    private final List<RenderableRow> allCompiledRows = new ArrayList<>();
    private final List<RenderableRow> visibleRows = new ArrayList<>();
    private final List<FormattedCharSequence> squishedStats = new ArrayList<>();

    // --- KINETIC SMOOTH SCROLLING ---
    private float scrollTarget = 0.0F;
    private float scrollCurrent = 0.0F;
    private int totalContentHeight = 0;

    // --- INTERACTIVE COMPONENTS ---
    private EditBox searchBox;
    private final List<UIParticle> particles = new ArrayList<>();

    // --- TRIDIMENSIONAL TRIPTYCH LAYOUT BOUNDS ---
    private int paneY;
    private int paneHeight;
    private int paneWidth;

    public RuneInfoScreen(ItemStack stack) {
        super(stack.getHoverName());
        this.runeStack = stack;

        // Visual Checks initial beim Laden
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(runeStack);
        this.isCorrupted = enchants.containsKey(Enchantments.BINDING_CURSE);

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
        this.particles.clear();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.paneY = centerY - 90;
        this.paneHeight = 165;
        this.paneWidth = 180;

        // Sucheingabe initialisieren (Narration & Placeholder lokalisiert)
        this.searchBox = new EditBox(this.font, centerX - 85, centerY + 53, 170, 12, Component.translatable("gui.stones.rune_info.search_narration"));
        this.searchBox.setHint(Component.translatable("gui.stones.rune_info.search_placeholder"));
        this.searchBox.setMaxLength(30);
        this.searchBox.setBordered(false);
        this.searchBox.setValue("");
        this.searchBox.setTextColor(COL_OFF_WHITE);
        this.searchBox.setResponder(this::onSearchQueryChanged);
        this.addRenderableWidget(this.searchBox);

        // Zurück / Schließen Knopf am unteren Bildschirmrand platzieren
        this.addRenderableWidget(Button.builder(Component.translatable("gui.stones.leaderboard.back"), (btn) -> this.onClose())
                .bounds(centerX - 50, centerY + 82, 100, 18).build());

        // Daten einmalig kompilieren & Stat-Squishing ausführen
        this.compileData("");
        this.performStatSquishing();
    }

    private void onSearchQueryChanged(String query) {
        this.compileData(query);
        this.scrollTarget = 0.0F; // Reset Scrolling bei neuer Suche
    }

    /**
     * RETAINED MODE DATA EXTRACTION & TRANSLATION
     * Berechnet alle UI-Komponenten vorab und haelt sie im RAM.
     * Nutzt Font-Splitting, um Text-Ueberlauf im schmalen Paneel zu verhindern.
     */
    private void compileData(String query) {
        this.allCompiledRows.clear();
        String filter = query.trim().toLowerCase();

        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(runeStack);

        // 1. GLOBAL AMPLIFY HEADER
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

        // 2. ENCHANTMENTS ITERIEREN
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            if (entry.getKey() instanceof RuneEnchantment rune) {
                int lvl = entry.getValue();
                Component fullname = rune.getFullname(lvl);
                String runeNameLower = fullname.getString().toLowerCase();

                List<RenderableRow> tempRuneRows = new ArrayList<>();
                boolean matchesFilter = filter.isEmpty() || runeNameLower.contains(filter);

                // Ornate Separator vor jedem neuen Hauptblock (außer am Anfang)
                if (!this.allCompiledRows.isEmpty()) {
                    tempRuneRows.add(new RenderableRow(Component.empty().getVisualOrderText(), false, true, 8));
                }

                // Rune Name Heading
                addRuneRow(tempRuneRows, fullname.copy().withStyle(isCorrupted ? ChatFormatting.RED : ChatFormatting.GOLD, ChatFormatting.BOLD), true, false, 12);

                // Rune Stats (Formatierte Modifikatoren)
                for (RuneStat stat : rune.getStats()) {
                    float val = RuneCalculator.calculateStatValue(stat, lvl, 1, getClientPlayerLevel(), amplifyMultiplier);
                    MutableComponent statLine = Component.literal("  ➤ ").withStyle(ChatFormatting.DARK_GRAY)
                            .append(RuneEnchantment.resolveComponent(stat.label()).copy().withStyle(ChatFormatting.GRAY)).append(": ")
                            .append(Component.literal(String.format("%.1f", val * stat.displayFactor())).withStyle(ChatFormatting.AQUA))
                            .append(RuneEnchantment.resolveComponent(stat.suffix()).copy().withStyle(ChatFormatting.AQUA));
                    addRuneRow(tempRuneRows, statLine, false, false, 10);
                }

                // Attribute/Bypass Lines
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

                // Natural Language Trigger Translation (PoE Style) - Einzigartigkeit gewaehrleistet & Lokalisiert
                Set<String> addedTriggers = new HashSet<>();
                for (RuneBehavior behavior : rune.getBehaviors()) {
                    String triggerId = behavior.trigger.id;
                    // Jedes Trigger-Verhalten nur einmal pro Rune auflisten (verhindert Duplikate bei mehreren Behaviors mit gleichem Trigger)
                    if (addedTriggers.add(triggerId)) {
                        Component translatedTrig = translateTriggerType(triggerId);
                        MutableComponent triggerLine = Component.literal("  ✦ ").withStyle(ChatFormatting.DARK_PURPLE)
                                .append(translatedTrig.copy().withStyle(ChatFormatting.LIGHT_PURPLE));
                        addRuneRow(tempRuneRows, triggerLine, false, false, 10);
                    }
                }

                // Wrapped Descriptions (Automatisch passend auf Paneel-Breite skaliert)
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

        // Falls die Suche keine Ergebnisse liefert (Lokalisiert)
        if (this.allCompiledRows.isEmpty()) {
            addRuneRow(this.allCompiledRows, Component.translatable("gui.stones.rune_info.no_modifiers").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), false, false, 12);
        }

        this.visibleRows.clear();
        this.visibleRows.addAll(this.allCompiledRows);

        // Gesamthoehe fuer Scrollbar-Limit berechnen
        this.totalContentHeight = 0;
        for (RenderableRow row : this.visibleRows) {
            this.totalContentHeight += row.height;
        }
    }

    /**
     * Hilfsmethode, um laengere Text-Komponenten pixelgenau aufzuteilen und Zeilenueberlauf zu verhindern.
     */
    private void addRuneRow(List<RenderableRow> list, Component comp, boolean isHeader, boolean isSeparator, int height) {
        int maxTextWidth = 162; // Sichere Breite innerhalb der mittleren Box
        List<FormattedCharSequence> lines = this.font.split(comp, maxTextWidth);
        for (int i = 0; i < lines.size(); i++) {
            int rowHeight = (i == 0) ? height : 9; // Folgezeilen enger zusammenziehen
            list.add(new RenderableRow(lines.get(i), isHeader, isSeparator, rowHeight));
        }
    }

    /**
     * STAT SQUISHING CHARACTER SHEET SUMMARY
     * Aggregiert identische Runen-Eigenschaften der rechten Flanke auf Kerndaten.
     * Filtert Milestones heraus und verhindert Text-Overflow im Eigenschaften-Paneel.
     */
    private void performStatSquishing() {
        this.squishedStats.clear();
        Map<String, Float> statSums = new LinkedHashMap<>();
        Map<String, String> statSuffixes = new HashMap<>();

        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(runeStack);
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            if (entry.getKey() instanceof RuneEnchantment rune) {
                // Milestones aus der aggregierten Eigenschaftenleiste ignorieren
                if (rune.type == RuneEnchantment.Type.MILESTONE) {
                    continue;
                }
                int lvl = entry.getValue();
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
            
            // Rechten Text pixelgenau splitten, falls Eigenschaftenbezeichnungen zu lang sind (Breite 94)
            for (FormattedCharSequence splitLine : this.font.split(line, 94)) {
                this.squishedStats.add(splitLine);
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
        // 1. Atmospheric Moving Backdrop (Parallax)
        this.renderNebulaParallax(gui, mouseX, mouseY);

        // 2. 2D UI Partikelsystem rendern
        this.renderUIParticles(gui, mouseX, mouseY);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // 3. Dynamic Glow Screen-Titel zeichnen
        this.renderDynamicTitle(gui, centerX, centerY);

        // 4. ASYMMETRIC TRIPTYCH LAYOUT RENDERING
        this.renderTriptychBorders(gui, centerX, centerY);

        // LEFT FLANK: Holographic 3D Orbiting Model
        this.renderHolographicArtifact(gui, centerX, centerY);

        // MIDDLE PANEL: Scissor Scrolled List with Frustum Culling
        this.renderMiddlePaneList(gui, centerX);

        // RIGHT FLANK: Summary & Cumulative Stats (Squished)
        this.renderRightPaneSummary(gui, centerX, centerY);

        // Footer Search Field Decoration / Outline
        gui.fill(centerX - 87, centerY + 51, centerX + 87, centerY + 65, 0xEE0A0A0A);
        gui.renderOutline(centerX - 87, centerY + 51, 174, 14, isCorrupted ? COL_ACCENT_RED : COL_DARK_BROWN);

        // Let standard screen elements draw (Buttons, search field itself)
        super.render(gui, mouseX, mouseY, partialTicks);
    }

    private void renderNebulaParallax(GuiGraphics gui, int mouseX, int mouseY) {
        long time = System.currentTimeMillis();
        PoseStack poseStack = gui.pose();
        float cx = this.width / 2.0f;
        float cy = this.height / 2.0f;

        poseStack.pushPose();
        float nebulaAngle = (time % 2400000L) / 2400000.0f * 360.0f;
        poseStack.translate(cx, cy, 0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(nebulaAngle));
        poseStack.translate(-cx, -cy, 0);

        float speed = 0.015f;
        int offX = (int) ((mouseX - width / 2) * speed);
        int offY = (int) ((mouseY - height / 2) * speed);
        int bgSize = Math.max(this.width, this.height) * 2;
        int bgX = (this.width - bgSize) / 2;
        int bgY = (this.height - bgSize) / 2;

        RenderSystem.setShaderColor(isCorrupted ? 0.8f : 0.4f, 0.2f, isCorrupted ? 0.2f : 0.6f, 0.8F);
        gui.blit(BG_NEBULA, bgX - offX, bgY - offY, 0, 0, bgSize, bgSize, 256, 256);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    private void renderUIParticles(GuiGraphics gui, int mouseX, int mouseY) {
        // Spawning ambient particles based on theme
        if (this.particles.size() < 100 && this.minecraft.level != null && this.minecraft.level.random.nextInt(4) == 0) {
            double px = this.minecraft.level.random.nextDouble() * this.width;
            double py = this.minecraft.level.random.nextDouble() * this.height;
            double pvx = (this.minecraft.level.random.nextDouble() - 0.5D) * 0.3D;
            double pvy = (this.minecraft.level.random.nextDouble() - 0.5D) * 0.3D;
            float psize = 1.0F + this.minecraft.level.random.nextFloat() * 1.5F;
            int pMaxAge = 60 + this.minecraft.level.random.nextInt(120);
            int pcolor = isCorrupted ? COL_ACCENT_RED : (this.minecraft.level.random.nextBoolean() ? COL_SICKLY_YELLOW : COL_CYAN_GLOW);
            this.particles.add(new UIParticle(px, py, pvx, pvy, psize, pMaxAge, pcolor));
        }

        // Processing / Drawing
        for (Iterator<UIParticle> it = this.particles.iterator(); it.hasNext(); ) {
            UIParticle p = it.next();
            p.tick(mouseX, mouseY);
            if (p.age >= p.maxAge) {
                it.remove();
                continue;
            }

            int alphaValue = (int)(p.alpha * 220.0F) & 0xFF;
            int finalCol = (alphaValue << 24) | (p.color & 0x00FFFFFF);
            gui.fill((int)p.x, (int)p.y, (int)(p.x + p.size), (int)(p.y + p.size), finalCol);
        }
    }

    private void renderDynamicTitle(GuiGraphics gui, int centerX, int centerY) {
        long time = System.currentTimeMillis();
        PoseStack poseStack = gui.pose();

        poseStack.pushPose();
        double hoverOffset = Math.sin((time % 3000) / 3000.0 * Math.PI * 2) * 2.0;
        poseStack.translate(0, hoverOffset, 0);

        // Backplate glow shadow
        gui.drawCenteredString(this.font, this.title, centerX + 1, centerY - 105, 0);
        gui.drawCenteredString(this.font, this.title, centerX - 1, centerY - 105, 0);

        int titleColor = isCorrupted ? COL_ACCENT_RED : COL_SICKLY_YELLOW;
        gui.drawCenteredString(this.font, this.title, centerX, centerY - 105, titleColor);

        poseStack.popPose();
    }

    private void renderTriptychBorders(GuiGraphics gui, int centerX, int centerY) {
        // Links: 3D Hologramm Box
        gui.fill(centerX - 210, paneY, centerX - 100, paneY + paneHeight, COL_DEEP_BLACK);
        gui.renderOutline(centerX - 210, paneY, 110, paneHeight, isCorrupted ? COL_ACCENT_RED : COL_DARK_BROWN);

        // Mitte: Scrollable List Panel
        gui.fill(centerX - 90, paneY, centerX + 90, paneY + paneHeight, COL_DEEP_BLACK);
        gui.renderOutline(centerX - 90, paneY, 180, paneHeight, isCorrupted ? COL_ACCENT_RED : COL_DARK_BROWN);

        // Rechts: Squished Character Stats
        gui.fill(centerX + 100, paneY, centerX + 210, paneY + paneHeight, COL_DEEP_BLACK);
        gui.renderOutline(centerX + 100, paneY, 110, paneHeight, isCorrupted ? COL_ACCENT_RED : COL_DARK_BROWN);
    }

    private void renderHolographicArtifact(GuiGraphics gui, int centerX, int centerY) {
        long time = System.currentTimeMillis();
        PoseStack poseStack = gui.pose();

        poseStack.pushPose();
        poseStack.translate(centerX - 155, centerY - 15, 150);
        float itemScale = 42.0F;
        poseStack.scale(itemScale, itemScale, itemScale);

        // Rotation & Breathing Animation
        float spinAngle = (time % 360000) / 32.0f;
        float pitchAngle = 15.0F + (float) Math.sin(time * 0.002) * 8.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(spinAngle));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitchAngle));

        Minecraft.getInstance().getItemRenderer().renderStatic(
                this.runeStack,
                ItemDisplayContext.GUI,
                15728880,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                gui.bufferSource(),
                this.minecraft.level,
                0
        );

        poseStack.popPose();

        // Holographic Platform / Pedestal below the floating artifact (Path of Exile Look)
        poseStack.pushPose();
        poseStack.translate(centerX - 155, centerY + 22, 0); 
        float floatPulse = (float) Math.sin(time * 0.004) * 3.0F;
        
        // Flatten the coordinate system to draw circular concentric discs
        poseStack.scale(1.0F, 0.3F, 1.0F);

        int baseRingCol = isCorrupted ? COL_ACCENT_RED : COL_CYAN_GLOW;
        // Concentric glowing rings with opacity fading outward
        int ringAlpha3 = 0x22000000 | (baseRingCol & 0x00FFFFFF);
        int ringAlpha2 = 0x55000000 | (baseRingCol & 0x00FFFFFF);
        int ringAlpha1 = 0xAA000000 | (baseRingCol & 0x00FFFFFF);

        // Soft outer glowing disc
        gui.fill(-32, (int) (-3 + floatPulse), 32, (int) (3 + floatPulse), ringAlpha3);
        // Medium glowing disc
        gui.fill(-24, (int) (-2 + floatPulse), 24, (int) (2 + floatPulse), ringAlpha2);
        // Bright inner core disc
        gui.fill(-16, (int) (-1 + floatPulse), 16, (int) (1 + floatPulse), ringAlpha1);

        // Solid energy boundaries representing decorative elements of the pedestal
        gui.fill(-26, (int) (-1 + floatPulse), -22, (int) (1 + floatPulse), baseRingCol);
        gui.fill(22, (int) (-1 + floatPulse), 26, (int) (1 + floatPulse), baseRingCol);

        poseStack.popPose();
    }

    private void renderMiddlePaneList(GuiGraphics gui, int centerX) {
        PoseStack poseStack = gui.pose();

        // Lerp scrolling position
        this.scrollCurrent = this.scrollCurrent + (this.scrollTarget - this.scrollCurrent) * 0.2F;
        if (Math.abs(this.scrollCurrent - this.scrollTarget) < 0.1F) {
            this.scrollCurrent = this.scrollTarget;
        }

        int clipX = centerX - 89;
        int clipY = paneY + 2;
        int clipW = 178;
        int clipH = paneHeight - 44; // Platz fuer Editbox & Margin freigeben

        // Hardware clipping aktivieren
        gui.enableScissor(clipX, clipY, clipX + clipW, clipY + clipH);

        poseStack.pushPose();
        poseStack.translate(0, -scrollCurrent, 0);

        int currentY = clipY + 4;
        for (RenderableRow row : this.visibleRows) {
            // Software-seitiges 2D Frustum Culling
            boolean isVisible = (currentY + row.height >= clipY + scrollCurrent) && (currentY <= clipY + scrollCurrent + clipH);

            if (isVisible) {
                if (row.isSeparator) {
                    drawOrnateSeparator(gui, clipX + 10, currentY + 3, clipW - 20);
                } else {
                    int txtColor = row.isHeader ? (isCorrupted ? COL_ACCENT_RED : COL_SICKLY_YELLOW) : COL_OFF_WHITE;
                    gui.drawString(this.font, row.component, clipX + 8, currentY, txtColor, false);
                }
            }
            currentY += row.height;
        }

        poseStack.popPose();
        gui.disableScissor();
    }

    private void renderRightPaneSummary(GuiGraphics gui, int centerX, int centerY) {
        gui.drawString(this.font, Component.translatable("gui.stones.rune_info.properties").withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE), centerX + 108, paneY + 10, COL_SICKLY_YELLOW, false);

        int statY = paneY + 28;
        for (FormattedCharSequence statComp : this.squishedStats) {
            if (statY + 12 > paneY + paneHeight) break; // Hard Cap fuer Panel-Ueberlauf
            gui.drawString(this.font, statComp, centerX + 108, statY, COL_OFF_WHITE, false);
            statY += 11;
        }
    }

    private void drawOrnateSeparator(GuiGraphics gui, int x, int y, int width) {
        int colorStart = isCorrupted ? COL_ACCENT_RED : COL_SICKLY_YELLOW;
        int colorEnd = 0x001E1914;

        // Left fading gradient
        gui.fillGradient(x, y, x + width / 2, y + 1, colorEnd, colorStart);
        // Right fading gradient
        gui.fillGradient(x + width / 2, y, x + width, y + 1, colorStart, colorEnd);
        // Little central ornament gem
        gui.fill(x + width / 2 - 2, y - 1, x + width / 2 + 2, y + 2, colorStart);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int clipH = paneHeight - 44;
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

    // --- IMMERSIVE SCREEN-SPACE LIGHTWEIGHT PARTICLE ENGINE ---
    private static class UIParticle {
        double x, y;
        double vx, vy;
        float size;
        int age;
        int maxAge;
        float alpha;
        int color;

        UIParticle(double x, double y, double vx, double vy, float size, int maxAge, int color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.maxAge = maxAge;
            this.color = color;
            this.age = 0;
            this.alpha = 1.0F;
        }

        void tick(double mouseX, double mouseY) {
            this.age++;
            this.x += this.vx;
            this.y += this.vy;

            // Fluid air resistance
            this.vx *= 0.97D;
            this.vy *= 0.97D;

            // Repulsive force from the mouse cursor
            double dx = this.x - mouseX;
            double dy = this.y - mouseY;
            double distSq = dx * dx + dy * dy;
            if (distSq < 1600.0D) { // Within 40 pixels
                double dist = Math.sqrt(distSq);
                double force = (40.0D - dist) / 40.0D;
                this.vx += (dx / dist) * force * 1.1D;
                this.vy += (dy / dist) * force * 1.1D;
            }

            // Alpha calculation
            if (this.age < 15) {
                this.alpha = (float) this.age / 15.0F;
            } else if (this.age > this.maxAge - 20) {
                this.alpha = (float) (this.maxAge - this.age) / 20.0F;
            } else {
                this.alpha = 1.0F;
            }
        }
    }

    // --- RETAINED ROW HOLDER CLASS ---
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