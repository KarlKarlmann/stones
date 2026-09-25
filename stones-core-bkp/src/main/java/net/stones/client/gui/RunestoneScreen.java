package net.stones.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
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
+-------------------------------------------------------+------------------------+
|  RUNENSTEIN-PROGRESSION                      [XP: 45] | BONI-ZUSAMMENFASSUNG   |
+------------------------------------+------------------+------------------------+
| Level 1  | [Major Slot]            |  ||  (Scrollbar) | +15% Angriffsschaden   |
| Level 5  | [Minor] [Minor]         |  ||              | +10 Max. Lebenspunkte  |
| Level 12 | [Minor] [Major]         |  ||              | +5% Laufgeschwindigkeit|
| Level 18 | [Milestone Slot]        |  ||              |                        |
| Level 25 | [Minor] [Minor] [Minor] |  ||              |                        |
| ...      | ...                     |  ||              |                        |
+------------------------------------+------------------+------------------------+
|  SPIELER-INVENTAR                                                              |
|  [ Slot ] [ Slot ] [ Slot ] ...                                                |
+--------------------------------------------------------------------------------+ 
*/
public class RunestoneScreen extends AbstractContainerScreen<RunestoneMenu> {

    private static final int VISIBLE_ROWS = 5;
    private static final int ROW_HEIGHT = 22;
    private static final int TABLE_VIEWPORT_Y = 24;
    
    // Spalten-Offsets für das 2-Spalten-Layout
    private static final int COL_1_TEXT_X = 12;
    private static final int COL_1_SLOT_X = 52;
    private static final int COL_2_TEXT_X = 89;
    private static final int COL_2_SLOT_X = 132;
    
    private float scrollOffs = 0.0F;
    private boolean isScrolling = false;
    private int startIndex = 0;
    private boolean isBound = false;

    public RunestoneScreen(RunestoneMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 276;
        this.imageHeight = 222;
        this.inventoryLabelY = this.imageHeight - 92;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = 12;

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

        int scrollbarX = relX + 168;
        int scrollbarY = relY + TABLE_VIEWPORT_Y;
        if (mouseX >= scrollbarX && mouseX <= scrollbarX + 8 && mouseY >= scrollbarY && mouseY <= scrollbarY + 110) {
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
            this.renderCustomTableTooltips(guiGraphics, mouseX, mouseY);
        } else {
            int relX = (this.width - this.imageWidth) / 2;
            int relY = (this.height - this.imageHeight) / 2;
            
            guiGraphics.fill(relX + 8, relY + 20, relX + 268, relY + 134, 0xAA222222);
            guiGraphics.drawCenteredString(this.font, "§7(Nicht gebunden)", relX + 138, relY + 72, 0xAAAAAA);
        }

        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderTableSlotsAndItems(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int relX = (this.width - this.imageWidth) / 2;
        int relY = (this.height - this.imageHeight) / 2;

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

        guiGraphics.fill(relX, relY, relX + this.imageWidth, relY + this.imageHeight, 0xFFC6C6C6);
        
        guiGraphics.fill(relX, relY, relX + this.imageWidth, relY + 2, 0xFFFFFFFF);
        guiGraphics.fill(relX, relY, relX + 2, relY + this.imageHeight, 0xFFFFFFFF);
        guiGraphics.fill(relX, relY + this.imageHeight - 2, relX + this.imageWidth, relY + this.imageHeight, 0xFF555555);
        guiGraphics.fill(relX + this.imageWidth - 2, relY, relX + this.imageWidth, relY + this.imageHeight, 0xFF555555);

        drawRecessedPanel(guiGraphics, relX + 8, relY + 20, 158, 114);
        drawRecessedPanel(guiGraphics, relX + 178, relY + 20, 90, 114);

        // Vertikale Trennlinie zwischen den beiden Spalten
        guiGraphics.fill(relX + 83, relY + 22, relX + 84, relY + 132, 0xFF373737);
        guiGraphics.fill(relX + 84, relY + 22, relX + 85, relY + 132, 0xFFFFFFFF);

        int scrollbarX = relX + 160;
        guiGraphics.fill(scrollbarX, relY + 20, scrollbarX + 6, relY + 134, 0xFF373737);
        if (this.isBound) {
            int scrollbarY = relY + TABLE_VIEWPORT_Y + (int) (this.scrollOffs * 95);
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 6, scrollbarY + 15, 0xFF8B8B8B);
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 5, scrollbarY + 14, 0xFFE0E0E0);
        }

        boolean bound = this.isBound;
        int currentXp = bound ? this.menu.getBoundPlayerLevel() : 0;

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

        if (bound) {
            this.renderStatsSummary(guiGraphics, relX + 182, relY + 24);
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
        gui.drawString(this.font, "Aktive Boni", startX, startY, 0x333333, false);

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
            gui.drawString(this.font, "§8Keine Runen", startX, y, 0x666666, false);
            return;
        }

        for (Map.Entry<Attribute, Double> entry : totals.entrySet()) {
            double val = entry.getValue();
            boolean percent = isPercentage.getOrDefault(entry.getKey(), false);
            String valStr = percent ? String.format("+%.0f%%", val * 100) : String.format("+%.1f", val);
            Component name = Component.translatable(entry.getKey().getDescriptionId());
            gui.drawString(this.font, "§2" + valStr + " §8" + name.getString(), startX, y, 0x333333, false);
            y += 10;
            if (y > startY + 100) break;
        }

        for (Component milestoneName : activeMilestones) {
            gui.drawString(this.font, "§5✦ " + milestoneName.getString(), startX, y, 0x333333, false);
            y += 10;
            if (y > startY + 100) break;
        }
    }
}