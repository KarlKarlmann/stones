package net.stones.visuals.client.gui;

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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.stones.enchantment.AmplifyEnchantment;
import net.stones.enchantment.RuneEnchantment;
import net.stones.enchantment.RuneStat;
import net.stones.item.StoneItem;
import net.stones.util.RuneCalculator;

import java.util.*;

public class VisualRuneInfoScreen extends Screen {

    private static final ResourceLocation BG_NEBULA = new ResourceLocation("stones", "textures/gui/shrine_nebula.png");

    private static final int COL_DEEP_BLACK    = 0xEE050505;
    private static final int COL_DARK_BROWN    = 0xFF1E1914;
    private static final int COL_SICKLY_YELLOW = 0xFFE4E595;
    private static final int COL_OFF_WHITE     = 0xFFF5F5E0;
    private static final int COL_ACCENT_RED    = 0xFFA01414;
    private static final int COL_CYAN_GLOW     = 0xFF00FFFF;
    
    private static final int COL_EMPTY_GRAY    = 0xFF888888;
    private static final int COL_EMPTY_DARK    = 0xFF444444;

    private final ItemStack runeStack;
    private final boolean isCorrupted;
    private final boolean isEmptyRune;
    private final double amplifyMultiplier;

    private final List<RenderableRow> allCompiledRows = new ArrayList<>();
    private final List<RenderableRow> visibleRows = new ArrayList<>();
    private final List<FormattedCharSequence> squishedStats = new ArrayList<>();

    private float scrollTarget = 0.0F;
    private float scrollCurrent = 0.0F;
    private int totalContentHeight = 0;

    private EditBox searchBox;
    private final List<UIParticle> particles = new ArrayList<>();

    private int paneY;
    private int paneHeight;
    private int paneWidth;

    private SimpleSoundInstance ambientSound;

    public VisualRuneInfoScreen(ItemStack stack) {
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
        this.particles.clear();
        
        if (this.minecraft != null) {
            this.minecraft.getMusicManager().stopPlaying();
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.paneY = centerY - 90;
        this.paneHeight = 165;
        this.paneWidth = 180;

        this.searchBox = new EditBox(this.font, centerX - 85, centerY + 53, 170, 12, Component.translatable("gui.stones.rune_info.search_narration"));
        this.searchBox.setHint(Component.translatable("gui.stones.rune_info.search_placeholder"));
        this.searchBox.setMaxLength(30);
        this.searchBox.setBordered(false);
        this.searchBox.setValue("");
        this.searchBox.setTextColor(COL_OFF_WHITE);
        this.searchBox.setResponder(this::onSearchQueryChanged);
        this.addRenderableWidget(this.searchBox);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.stones.leaderboard.back"), (btn) -> this.onClose())
                .bounds(centerX - 50, centerY + 82, 100, 18).build());

        this.compileData("");
        this.performStatSquishing();

        if (this.ambientSound == null && this.minecraft != null) {
            ResourceLocation soundLoc;
            
            if (this.isEmptyRune) {
                soundLoc = new ResourceLocation("stones", "music.rune_empty");
            } else if (this.isCorrupted) {
                soundLoc = new ResourceLocation("stones", "music.rune_cursed");
            } else {
                soundLoc = new ResourceLocation("stones", "music.rune_ambient");
            }

            this.ambientSound = new SimpleSoundInstance(
                    soundLoc,
                    SoundSource.MUSIC,
                    1.0f, 1.0f,
                    RandomSource.create(),
                    true, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true 
            );
            this.minecraft.getSoundManager().play(this.ambientSound);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.minecraft != null && this.minecraft.player != null && this.minecraft.player.tickCount % 20 == 0) {
            this.minecraft.getMusicManager().stopPlaying();
        }
    }

    @Override
    public void removed() {
        super.removed();
        if (this.ambientSound != null && this.minecraft != null) {
            this.minecraft.getSoundManager().stop(this.ambientSound);
            this.ambientSound = null;
        }
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
        int maxTextWidth = 162;
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
                        .append(milestoneName.copy().withStyle(ChatFormatting.WHITE));
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
        this.renderNebulaParallax(gui, mouseX, mouseY);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.renderUIParticles(gui, mouseX, mouseY, centerX, centerY);
        this.renderDynamicTitle(gui, centerX, centerY);
        this.renderTriptychBorders(gui, centerX, centerY);
        this.renderHolographicArtifact(gui, centerX, centerY);
        this.renderMiddlePaneList(gui, centerX);
        this.renderRightPaneSummary(gui, centerX, centerY);

        gui.fill(centerX - 87, centerY + 51, centerX + 87, centerY + 65, 0xEE0A0A0A);
        
        int outlineColor = this.isEmptyRune ? COL_EMPTY_DARK : (isCorrupted ? COL_ACCENT_RED : COL_DARK_BROWN);
        gui.renderOutline(centerX - 87, centerY + 51, 174, 14, outlineColor);

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

        if (this.isEmptyRune) {
            RenderSystem.setShaderColor(0.3f, 0.3f, 0.3f, 0.8F);
        } else {
            RenderSystem.setShaderColor(isCorrupted ? 0.8f : 0.4f, 0.2f, isCorrupted ? 0.2f : 0.6f, 0.8F);
        }
        
        gui.blit(BG_NEBULA, bgX - offX, bgY - offY, 0, 0, bgSize, bgSize, 256, 256);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    private void renderUIParticles(GuiGraphics gui, int mouseX, int mouseY, int centerX, int centerY) {
        if (this.minecraft.level == null) return;
        
        if (this.particles.size() < 150) {
            if (this.minecraft.level.random.nextInt(4) == 0) {
                double px = this.minecraft.level.random.nextDouble() * this.width;
                double py = this.minecraft.level.random.nextDouble() * this.height;
                double pvx = (this.minecraft.level.random.nextDouble() - 0.5D) * 0.3D;
                double pvy = (this.minecraft.level.random.nextDouble() - 0.5D) * 0.3D;
                float psize = 1.0F + this.minecraft.level.random.nextFloat() * 1.5F;
                int pMaxAge = 60 + this.minecraft.level.random.nextInt(120);
                
                int pcolor;
                if (this.isEmptyRune) {
                    pcolor = COL_EMPTY_GRAY;
                } else {
                    pcolor = isCorrupted ? COL_ACCENT_RED : (this.minecraft.level.random.nextBoolean() ? COL_SICKLY_YELLOW : COL_CYAN_GLOW);
                }
                
                this.particles.add(new UIParticle(px, py, pvx, pvy, psize, pMaxAge, pcolor));
            }
            
            if (!this.isEmptyRune && this.minecraft.level.random.nextInt(2) == 0) {
                int runeX = centerX - 155;
                int runeY = centerY - 15;
                double px = runeX + (this.minecraft.level.random.nextDouble() - 0.5) * 30.0;
                double py = runeY + (this.minecraft.level.random.nextDouble() - 0.5) * 40.0;
                double pvx = (px - runeX) * 0.015;
                double pvy = -0.3 - this.minecraft.level.random.nextDouble() * 0.8;
                float psize = 1.5F + this.minecraft.level.random.nextFloat() * 2.0F;
                int pMaxAge = 30 + this.minecraft.level.random.nextInt(30);
                int pcolor = isCorrupted ? COL_ACCENT_RED : COL_CYAN_GLOW;
                this.particles.add(new UIParticle(px, py, pvx, pvy, psize, pMaxAge, pcolor));
            }
        }

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

        gui.drawCenteredString(this.font, this.title, centerX + 1, centerY - 105, 0);
        gui.drawCenteredString(this.font, this.title, centerX - 1, centerY - 105, 0);

        int titleColor;
        if (this.isEmptyRune) {
            titleColor = COL_EMPTY_GRAY;
        } else {
            titleColor = isCorrupted ? COL_ACCENT_RED : COL_SICKLY_YELLOW;
        }
        
        gui.drawCenteredString(this.font, this.title, centerX, centerY - 105, titleColor);
        poseStack.popPose();
    }

    private void renderTriptychBorders(GuiGraphics gui, int centerX, int centerY) {
        int borderColor = this.isEmptyRune ? COL_EMPTY_DARK : (isCorrupted ? COL_ACCENT_RED : COL_DARK_BROWN);
        
        gui.fill(centerX - 210, paneY, centerX - 100, paneY + paneHeight, COL_DEEP_BLACK);
        gui.renderOutline(centerX - 210, paneY, 110, paneHeight, borderColor);

        gui.fill(centerX - 90, paneY, centerX + 90, paneY + paneHeight, COL_DEEP_BLACK);
        gui.renderOutline(centerX - 90, paneY, 180, paneHeight, borderColor);

        gui.fill(centerX + 100, paneY, centerX + 210, paneY + paneHeight, COL_DEEP_BLACK);
        gui.renderOutline(centerX + 100, paneY, 110, paneHeight, borderColor);
    }

    private void renderHolographicArtifact(GuiGraphics gui, int centerX, int centerY) {
        long time = System.currentTimeMillis();
        PoseStack poseStack = gui.pose();

        poseStack.pushPose();
        poseStack.translate(centerX - 155, centerY - 15, 250); 
        
        float itemScale = 42.0F;
        poseStack.scale(itemScale, -itemScale, itemScale); 

        float spinAngle = (time % 360000) / 32.0f;
        float pitchAngle = 15.0F + (float) Math.sin(time * 0.002) * 8.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(spinAngle));
        poseStack.mulPose(Axis.XP.rotationDegrees(pitchAngle));

        RenderSystem.enableDepthTest();
        com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();
        
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
        
        gui.flush(); 
        RenderSystem.disableDepthTest();
        poseStack.popPose();
    }

    private void renderMiddlePaneList(GuiGraphics gui, int centerX) {
        PoseStack poseStack = gui.pose();

        this.scrollCurrent = this.scrollCurrent + (this.scrollTarget - this.scrollCurrent) * 0.2F;
        if (Math.abs(this.scrollCurrent - this.scrollTarget) < 0.1F) {
            this.scrollCurrent = this.scrollTarget;
        }

        int clipX = centerX - 89;
        int clipY = paneY + 2;
        int clipW = 178;
        int clipH = paneHeight - 44;

        gui.enableScissor(clipX, clipY, clipX + clipW, clipY + clipH);

        poseStack.pushPose();
        poseStack.translate(0, -scrollCurrent, 0);

        int currentY = clipY + 4;
        for (RenderableRow row : this.visibleRows) {
            boolean isVisible = (currentY + row.height >= clipY + scrollCurrent) && (currentY <= clipY + scrollCurrent + clipH);

            if (isVisible) {
                if (row.isSeparator) {
                    drawOrnateSeparator(gui, clipX + 10, currentY + 3, clipW - 20);
                } else {
                    int txtColor = COL_OFF_WHITE;
                    gui.drawString(this.font, row.component, clipX + 8, currentY, txtColor, false);
                }
            }
            currentY += row.height;
        }

        poseStack.popPose();
        gui.disableScissor();
    }

    private void renderRightPaneSummary(GuiGraphics gui, int centerX, int centerY) {
        int titleColor = this.isEmptyRune ? COL_EMPTY_GRAY : COL_SICKLY_YELLOW;
        gui.drawString(this.font, Component.translatable("gui.stones.rune_info.properties").withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE), centerX + 108, paneY + 10, titleColor, false);

        int statY = paneY + 28;
        for (FormattedCharSequence statComp : this.squishedStats) {
            if (statY + 12 > paneY + paneHeight) break;
            gui.drawString(this.font, statComp, centerX + 108, statY, COL_OFF_WHITE, false);
            statY += 11;
        }
    }

    private void drawOrnateSeparator(GuiGraphics gui, int x, int y, int width) {
        int colorStart;
        if (this.isEmptyRune) {
            colorStart = COL_EMPTY_GRAY;
        } else {
            colorStart = isCorrupted ? COL_ACCENT_RED : COL_SICKLY_YELLOW;
        }
        int colorEnd = 0x001E1914;

        gui.fillGradient(x, y, x + width / 2, y + 1, colorEnd, colorStart);
        gui.fillGradient(x + width / 2, y, x + width, y + 1, colorStart, colorEnd);
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

            this.vx *= 0.97D;
            this.vy *= 0.97D;

            double dx = this.x - mouseX;
            double dy = this.y - mouseY;
            double distSq = dx * dx + dy * dy;
            if (distSq < 1600.0D) {
                double dist = Math.sqrt(distSq);
                double force = (40.0D - dist) / 40.0D;
                this.vx += (dx / dist) * force * 1.1D;
                this.vy += (dy / dist) * force * 1.1D;
            }

            if (this.age < 15) {
                this.alpha = (float) this.age / 15.0F;
            } else if (this.age > this.maxAge - 20) {
                this.alpha = (float) (this.maxAge - this.age) / 20.0F;
            } else {
                this.alpha = 1.0F;
            }
        }
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