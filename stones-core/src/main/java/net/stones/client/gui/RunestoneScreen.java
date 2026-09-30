package net.stones.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.stones.cap.PlayerShrineCapProvider;
import net.stones.data.ShrineInstance.SlotConfig;
import net.stones.data.ShrineInstance.SlotType;
import net.stones.gui.RunestoneMenu;
import net.stones.item.ClusterJewelItem;
import net.stones.item.StoneItem;
import net.stones.util.RuneCalculator;
import net.stones.util.ClusterTooltipHandler;

import java.util.*;

/* 
+-----------------------------------------------------------------+--------------------------+
|  RUNENSTEIN-PROGRESSION                                         | BONI-ZUSAMMENFASSUNG     |
+------------------------------------+----------------------------+--------------------------+
| Level 1  | [Major Slot]            |  ||  (Scrollbar)           | +15% Angriffsschaden     |
| Level 5  | [Minor] [Minor]         |  ||                        | +10 Max. Lebenspunkte    |
| Level 12 | [Minor] [Major]         |  ||                        | +5% Laufgeschwindigkeit  |
| Level 18 | [Milestone Slot]        |  ||                        |                          |
| Level 25 | [Minor] [Minor] [Minor] |  ||                        |                          |
| ...      | ...                     |  ||                        |                          |
+------------------------------------+----------------------------+--------------------------+
|                    ============[  45  ]============ (XP-Bar)                               |
|  SPIELER-INVENTAR (Zentriert)                                                              |
|  [ Slot ] [ Slot ] [ Slot ] ...                                                            |
+--------------------------------------------------------------------------------------------+ 
*/

public class RunestoneScreen extends AbstractContainerScreen<RunestoneMenu> {

    private static final ResourceLocation XP_BAR_LOCATION = new ResourceLocation("textures/gui/icons.png");

    private static final int VISIBLE_ROWS = 5;
    private static final int ROW_HEIGHT = 22;
    private static final int TABLE_VIEWPORT_Y = 22;
    
    // Spalten-Offsets für das 2-Spalten-Layout
    private static final int COL_1_TEXT_X = 12;
    private static final int COL_1_SLOT_X = 52;
    private static final int COL_2_TEXT_X = 89;
    private static final int COL_2_SLOT_X = 132;
    
    // Maximale Breite für Textschnitte im 134px Zusammenfassungs-Panel
    private static final int SUMMARY_MAX_WIDTH = 118;
    private static final int MAX_SUMMARY_VISIBLE_LINES = 10;

    private float scrollOffs = 0.0F;
    private boolean isScrolling = false;
    private int startIndex = 0;
    private boolean isBound = false;

    private int summaryScroll = 0;
    private int totalSummaryLines = 0;

    public RunestoneScreen(RunestoneMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 320;
        this.imageHeight = 236; // Erhöht für angenehmen Abstand zur XP-Bar und Inventar
        this.inventoryLabelY = -10000; // Entfernt das standardmäßige "Inventory"-Label
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Nur den Fenstertitel zeichnen, "Inventory"-Text wird bewusst ausgelassen
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 12;
        this.titleLabelY = 6;

        this.isBound = false;
        UUID viewedShrineId = this.menu.getShrineId();
        
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.getCapability(PlayerShrineCapProvider.SHRINE_LINK).ifPresent(cap -> {
                UUID linkedId = cap.getLinkedShrine();
                if (linkedId != null && viewedShrineId != null && linkedId.equals(viewedShrineId)) {
                    this.isBound = true;
                }
            });
        }
    }

    private int getTotalRows() {
        return (int) Math.ceil(this.menu.layoutData.size() / 2.0);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!this.isBound) return false;

        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        // Wenn der Cursor über dem rechten Zusammenfassungs-Panel liegt -> Boni scrollen
        if (mouseX >= relX + 178 && mouseX <= relX + 312 && mouseY >= relY + 18 && mouseY <= relY + 136) {
            int maxSummaryScroll = Math.max(0, this.totalSummaryLines - MAX_SUMMARY_VISIBLE_LINES);
            if (maxSummaryScroll > 0) {
                this.summaryScroll = Mth.clamp(this.summaryScroll - (int) Math.signum(delta), 0, maxSummaryScroll);
                return true;
            }
            return false;
        }

        // Standard: Linke Runen-Tabelle scrollen
        int maxRows = getTotalRows() - VISIBLE_ROWS;
        if (maxRows > 0) {
            this.scrollOffs = (float) (this.scrollOffs - (delta / (double) maxRows));
            this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0F, 1.0F);
            this.startIndex = (int) ((double) (this.scrollOffs * maxRows) + 0.5D);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isBound) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int rowIdx = this.startIndex + i;
            int rowY = relY + TABLE_VIEWPORT_Y + (i * ROW_HEIGHT) + 2;

            for (int col = 0; col < 2; col++) {
                int layoutIdx = (rowIdx * 2) + col;
                if (layoutIdx >= this.menu.layoutData.size()) break;

                int slotX = relX + (col == 0 ? COL_1_SLOT_X : COL_2_SLOT_X);
                if (mouseX >= slotX && mouseX <= slotX + 18 && mouseY >= rowY && mouseY <= rowY + 18) {
                    if (layoutIdx < this.menu.slots.size()) {
                        Slot slot = this.menu.slots.get(layoutIdx);
                        this.slotClicked(slot, slot.index, button, ClickType.PICKUP);
                        return true;
                    }
                }
            }
        }

        int scrollbarX = relX + 158;
        int scrollbarY = relY + TABLE_VIEWPORT_Y;
        if (mouseX >= scrollbarX && mouseX <= scrollbarX + 10 && mouseY >= scrollbarY && mouseY <= scrollbarY + 112) {
            this.isScrolling = true;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.isScrolling = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isScrolling && this.isBound) {
            int relY = (this.height - this.imageHeight) / 2;
            int barMinY = relY + TABLE_VIEWPORT_Y;
            int barMaxY = barMinY + 95;
            this.scrollOffs = ((float) mouseY - (float) barMinY - 7.5F) / ((float) (barMaxY - barMinY) - 15.0F);
            this.scrollOffs = Mth.clamp(this.scrollOffs, 0.0F, 1.0F);
            int maxRows = getTotalRows() - VISIBLE_ROWS;
            if (maxRows > 0) {
                this.startIndex = (int) ((double) (this.scrollOffs * maxRows) + 0.5D);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        
        if (this.isBound) {
            this.renderTableSlotsAndItems(guiGraphics, mouseX, mouseY);
            this.renderExperienceBar(guiGraphics);
            this.renderCustomTableTooltips(guiGraphics, mouseX, mouseY);
        } else {
            int relX = (this.width - this.imageWidth) / 2;
            int relY = (this.height - this.imageHeight) / 2;
            
            guiGraphics.fill(relX + 8, relY + 18, relX + 312, relY + 136, 0xAA222222);
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.stones.not_bound").withStyle(ChatFormatting.GRAY), relX + 160, relY + 74, 0xAAAAAA);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderExperienceBar(GuiGraphics guiGraphics) {
        if (this.minecraft == null || this.minecraft.player == null) return;

        int experience = this.minecraft.player.experienceLevel;
        float progress = this.minecraft.player.experienceProgress;
        
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        // Sauber positioniert zwischen den Fenstern und dem Inventar
        int barX = relX + (this.imageWidth - 182) / 2;
        int barY = relY + 142;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0.0F, 0.0F, 200.0F); 

        RenderSystem.setShaderTexture(0, XP_BAR_LOCATION);
        
        // Hintergrund der XP-Bar (182x5)
        guiGraphics.blit(XP_BAR_LOCATION, barX, barY, 0, 64, 182, 5);
        
        // Gefüllter Teil basierend auf Fortschritt
        if (progress > 0) {
            int progressWidth = (int)(progress * 182.0F);
            guiGraphics.blit(XP_BAR_LOCATION, barX, barY, 0, 69, progressWidth, 5);
        }

        // Level-Zahl mit typischer 4-Wege-Schattierung rendern
        if (experience > 0) {
            String levelStr = String.valueOf(experience);
            int strX = relX + (this.imageWidth - this.font.width(levelStr)) / 2;
            int strY = barY - 6; 
            
            guiGraphics.drawString(this.font, levelStr, strX + 1, strY, 0, false);
            guiGraphics.drawString(this.font, levelStr, strX - 1, strY, 0, false);
            guiGraphics.drawString(this.font, levelStr, strX, strY + 1, 0, false);
            guiGraphics.drawString(this.font, levelStr, strX, strY - 1, 0, false);
            guiGraphics.drawString(this.font, levelStr, strX, strY, 8453920, false);
        }

        guiGraphics.pose().popPose();
    }

    private void renderTableSlotsAndItems(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        // Scissor-Maske verhindert strikt jegliches Überlappen des Tabellenbereichs
        guiGraphics.enableScissor(relX + 9, relY + 19, relX + 159, relY + 135);

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int rowIdx = this.startIndex + i;
            int rowY = relY + TABLE_VIEWPORT_Y + (i * ROW_HEIGHT) + 2;

            for (int col = 0; col < 2; col++) {
                int layoutIdx = (rowIdx * 2) + col;
                if (layoutIdx >= this.menu.layoutData.size()) break;

                int slotX = relX + (col == 0 ? COL_1_SLOT_X : COL_2_SLOT_X);

                if (layoutIdx < this.menu.slots.size()) {
                    Slot slot = this.menu.slots.get(layoutIdx);
                    SlotConfig cfg = this.menu.layoutData.get(layoutIdx);

                    drawTypedSlotBackground(guiGraphics, slotX, rowY, cfg.type);

                    if (!slot.hasItem()) {
                        String watermark = switch (cfg.type) {
                            case MINOR -> "mi";
                            case MAJOR -> "MA";
                            case MILESTONE -> "ML";
                        };
                        int textW = this.font.width(watermark);
                        guiGraphics.drawString(this.font, watermark, slotX + (18 - textW) / 2, rowY + 5, 0xFF666666, false);
                    } else {
                        guiGraphics.renderItem(slot.getItem(), slotX + 1, rowY + 1);
                        guiGraphics.renderItemDecorations(this.font, slot.getItem(), slotX + 1, rowY + 1);
                    }

                    if (mouseX >= slotX && mouseX <= slotX + 18 && mouseY >= rowY && mouseY <= rowY + 18) {
                        guiGraphics.fill(slotX + 1, rowY + 1, slotX + 17, rowY + 17, 0x80FFFFFF);
                    }
                }
            }
        }

        guiGraphics.disableScissor();
    }

    private void renderCustomTableTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int rowIdx = this.startIndex + i;
            int rowY = relY + TABLE_VIEWPORT_Y + (i * ROW_HEIGHT) + 2;

            for (int col = 0; col < 2; col++) {
                int layoutIdx = (rowIdx * 2) + col;
                if (layoutIdx >= this.menu.layoutData.size()) break;

                int slotX = relX + (col == 0 ? COL_1_SLOT_X : COL_2_SLOT_X);
                if (mouseX >= slotX && mouseX <= slotX + 18 && mouseY >= rowY && mouseY <= rowY + 18) {
                    SlotConfig cfg = this.menu.layoutData.get(layoutIdx);
                    Slot slot = this.menu.slots.get(layoutIdx);

                    List<Component> tooltip = new ArrayList<>();
                    if (slot.hasItem()) {
                        ItemStack stack = slot.getItem();
                        if (stack.getItem() instanceof ClusterJewelItem) {
                            ClusterTooltipHandler.appendClusterInfo(stack, tooltip);
                        } else {
                            StoneItem.addFullRuneTooltip(stack, tooltip, cfg.requiredLevel);
                        }
                    }

                    tooltip.add(Component.literal("Slot: ").withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(cfg.type.name()).withStyle(ChatFormatting.GOLD))
                            .append(Component.literal(" (Lvl " + cfg.requiredLevel + ")").withStyle(ChatFormatting.AQUA)));

                    guiGraphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
                    return;
                }
            }
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        // Basis-Fenster (auf 236px Höhe angepasst)
        guiGraphics.fill(relX, relY, relX + this.imageWidth, relY + this.imageHeight, 0xFFC6C6C6);
        
        guiGraphics.fill(relX, relY, relX + this.imageWidth, relY + 2, 0xFFFFFFFF);
        guiGraphics.fill(relX, relY, relX + 2, relY + this.imageHeight, 0xFFFFFFFF);
        guiGraphics.fill(relX, relY + this.imageHeight - 2, relX + this.imageWidth, relY + this.imageHeight, 0xFF555555);
        guiGraphics.fill(relX + this.imageWidth - 2, relY, relX + this.imageWidth, relY + this.imageHeight, 0xFF555555);

        // Linkes Panel (Tabelle): 158px breit, 118px hoch (schließt bündig bei relY + 136 ab)
        drawRecessedPanel(guiGraphics, relX + 8, relY + 18, 158, 118);
        
        // Rechtes Panel (Zusammenfassung): 134px breit, 118px hoch
        drawRecessedPanel(guiGraphics, relX + 178, relY + 18, 134, 118);

        // Vertikale Trennlinie zwischen Spalte 1 und Spalte 2
        guiGraphics.fill(relX + 83, relY + 19, relX + 84, relY + 135, 0xFF373737);
        guiGraphics.fill(relX + 84, relY + 19, relX + 85, relY + 135, 0xFFFFFFFF);

        // Scrollbar für die Tabelle
        int scrollbarX = relX + 160;
        guiGraphics.fill(scrollbarX, relY + 19, scrollbarX + 6, relY + 135, 0xFF373737);
        if (this.isBound) {
            int scrollbarY = relY + TABLE_VIEWPORT_Y + (int) (this.scrollOffs * 95);
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 6, scrollbarY + 15, 0xFF8B8B8B);
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 5, scrollbarY + 14, 0xFFE0E0E0);
        }

        // Spieler-Inventar Slots nach unten verlagert
        int invStartX = relX + 79;
        int invStartY = relY + 153;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawRecessedPanel(guiGraphics, invStartX + (col * 18) - 1, invStartY + (row * 18) - 1, 18, 18);
            }
        }
        int hotbarY = relY + 211;
        for (int col = 0; col < 9; col++) {
            drawRecessedPanel(guiGraphics, invStartX + (col * 18) - 1, hotbarY - 1, 18, 18);
        }

        boolean bound = this.isBound;
        int currentXp = bound ? this.menu.getBoundPlayerLevel() : 0;

        // Scissoring für den Zeilenhintergrund der Tabelle
        guiGraphics.enableScissor(relX + 9, relY + 19, relX + 159, relY + 135);

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int rowIdx = this.startIndex + i;
            int rowY = relY + TABLE_VIEWPORT_Y + (i * ROW_HEIGHT);

            for (int col = 0; col < 2; col++) {
                int layoutIdx = (rowIdx * 2) + col;
                if (layoutIdx >= this.menu.layoutData.size()) break;

                SlotConfig cfg = this.menu.layoutData.get(layoutIdx);
                int reqLevel = cfg.requiredLevel;
                boolean unlocked = bound && (currentXp >= reqLevel);

                int textX = relX + (col == 0 ? COL_1_TEXT_X : COL_2_TEXT_X);
                String lvlText = "L" + reqLevel;
                int color = bound ? (unlocked ? 0x228822 : 0xAA2222) : 0x777777;
                guiGraphics.drawString(this.font, lvlText, textX, rowY + 6, color, false);

                int bgX1 = textX - 2;
                int bgX2 = bgX1 + 72;
                int rowBgColor = bound ? (unlocked ? 0x2000FF00 : 0x20FF0000) : 0x10000000;
                guiGraphics.fill(bgX1, rowY, bgX2, rowY + ROW_HEIGHT - 2, rowBgColor);
            }
        }

        guiGraphics.disableScissor();

        if (bound) {
            this.renderStatsSummary(guiGraphics, relX + 182, relY + 22);
        }
    }

    private void drawTypedSlotBackground(GuiGraphics g, int x, int y, SlotType type) {
        drawRecessedPanel(g, x, y, 18, 18);

        if (type == SlotType.MAJOR) {
            g.fill(x + 1, y + 1, x + 17, y + 2, 0xFF555555);
            g.fill(x + 1, y + 1, x + 2, y + 17, 0xFF555555);
        } else if (type == SlotType.MILESTONE) {
            g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF7A7A7A);
            g.fill(x + 1, y + 1, x + 17, y + 2, 0xFF222222);
            g.fill(x + 1, y + 1, x + 2, y + 17, 0xFF222222);
            g.fill(x + 1, y + 16, x + 17, y + 17, 0xFFDDDDDD);
            g.fill(x + 16, y + 1, x + 17, y + 17, 0xFFDDDDDD);
        }
    }

    private void drawRecessedPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF8B8B8B);
        g.fill(x, y, x + w, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + h, 0xFF373737);
        g.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y, x + w, y + h, 0xFFFFFFFF);
    }

    private void renderStatsSummary(GuiGraphics gui, int startX, int startY) {
        gui.drawString(this.font, Component.translatable("gui.stones.active_bonuses"), startX, startY, 0x333333, false);

        int playerLevel = this.menu.getBoundPlayerLevel();
        Map<Attribute, Double> totals = new HashMap<>();
        Map<Attribute, Boolean> isPercentage = new HashMap<>();
        List<Component> activeMilestones = new ArrayList<>();

        IItemHandler menuWrapper = new IItemHandler() {
            @Override public int getSlots() { return menu.slots.size(); }
            @Override public ItemStack getStackInSlot(int slot) { 
                if (slot < menu.layoutData.size()) return menu.slots.get(slot).getItem();
                return ItemStack.EMPTY;
            }
            @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
            @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
            @Override public int getSlotLimit(int slot) { return 64; }
            @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
        };

        RuneCalculator.collectActiveRunes(menuWrapper, this.menu.layoutData, playerLevel,
            (runeEnch, runeLevel, socketLevel, mult, mainSlot, subSlot) -> {
                if (runeEnch.targetAttribute != null) {
                    try {
                        double val = RuneCalculator.calculateAttributeBonus(runeEnch, runeLevel, playerLevel, socketLevel, mult);
                        totals.put(runeEnch.targetAttribute, totals.getOrDefault(runeEnch.targetAttribute, 0.0) + val);
                        if (runeEnch.operation != AttributeModifier.Operation.ADDITION) isPercentage.put(runeEnch.targetAttribute, true);
                    } catch (Exception ignored) {}
                } else {
                    activeMilestones.add(runeEnch.getFullname(runeLevel));
                }
            }
        );

        int y = startY + 12;
        if (totals.isEmpty() && activeMilestones.isEmpty()) {
            gui.drawString(this.font, Component.translatable("gui.stones.no_runes").withStyle(ChatFormatting.DARK_GRAY), startX, y, 0x666666, false);
            this.totalSummaryLines = 0;
            this.summaryScroll = 0;
            return;
        }

        // Sammle alle Zeilen formatiert
        List<FormattedCharSequence> allLines = new ArrayList<>();

        for (Map.Entry<Attribute, Double> entry : totals.entrySet()) {
            double val = entry.getValue();
            boolean percent = isPercentage.getOrDefault(entry.getKey(), false);
            String valStr = percent ? String.format("+%.0f%%", val * 100) : String.format("+%.1f", val);
            Component name = Component.translatable(entry.getKey().getDescriptionId());

            MutableComponent fullLine = Component.literal(valStr + " ").withStyle(ChatFormatting.DARK_GREEN)
                    .append(name.copy().withStyle(ChatFormatting.DARK_GRAY));

            allLines.addAll(this.font.split(fullLine, SUMMARY_MAX_WIDTH));
        }

        for (Component milestoneName : activeMilestones) {
            MutableComponent milestoneLine = Component.literal("✦ ").withStyle(ChatFormatting.DARK_PURPLE)
                    .append(milestoneName.copy().withStyle(ChatFormatting.DARK_GRAY));

            allLines.addAll(this.font.split(milestoneLine, SUMMARY_MAX_WIDTH));
        }

        this.totalSummaryLines = allLines.size();
        int maxScroll = Math.max(0, this.totalSummaryLines - MAX_SUMMARY_VISIBLE_LINES);
        this.summaryScroll = Mth.clamp(this.summaryScroll, 0, maxScroll);

        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

        // Scissoring: Verhindert jegliches Auslaufen nach oben oder unten
        gui.enableScissor(relX + 179, relY + 19, relX + 311, relY + 135);

        // Zeige die Zeilen entsprechend dem Scroll-Offset an
        int endIdx = Math.min(allLines.size(), this.summaryScroll + MAX_SUMMARY_VISIBLE_LINES);
        for (int i = this.summaryScroll; i < endIdx; i++) {
            gui.drawString(this.font, allLines.get(i), startX, y, 0x333333, false);
            y += 9;
        }

        gui.disableScissor();

        // Subtile Scroll-Leiste anzeigen, falls mehr Zeilen da sind als hineinpassen
        if (maxScroll > 0) {
            int scrollTrackX = startX + 124;
            int scrollTrackY = startY + 12;
            int trackH = 88;
            gui.fill(scrollTrackX, scrollTrackY, scrollTrackX + 3, scrollTrackY + trackH, 0xFF373737);

            float scrollRatio = (float) this.summaryScroll / (float) maxScroll;
            int thumbH = Math.max(8, trackH * MAX_SUMMARY_VISIBLE_LINES / this.totalSummaryLines);
            int thumbY = scrollTrackY + (int) ((trackH - thumbH) * scrollRatio);
            gui.fill(scrollTrackX, thumbY, scrollTrackX + 3, thumbY + thumbH, 0xFF8B8B8B);
        }
    }
}