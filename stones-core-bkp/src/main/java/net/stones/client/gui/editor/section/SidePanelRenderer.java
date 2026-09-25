package net.stones.client.gui.editor.section;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.stones.network.StudioNetwork;

import java.util.ArrayList;
import java.util.List;
import net.stones.client.gui.editor.StonesStudioScreen;
import net.stones.client.gui.editor.section.StudioMenuBar;
import net.stones.client.gui.editor.widget.StudioUIHelper;

/**
 * Verwaltet das Sidepanel des Stones Studio Editors.
 * * Behebt den Hitbox-Offset beim Neuladen-Button [↻].
 */
public class SidePanelRenderer {

    public static final int WIDTH = 180;
    
    private final StonesStudioScreen screen;

    private boolean isMinorExpanded = true;
    private boolean isMajorExpanded = true;
    private boolean isMilestoneExpanded = true;
    private boolean isOtherExpanded = true;
    
    private double fileScrollY = 0;

    public SidePanelRenderer(StonesStudioScreen screen) {
        this.screen = screen;
    }

    private static class SidePanelItem {
        final boolean isCategory;
        final String categoryId;
        final String label;
        final String fileName;
        final int y;
        final boolean expanded;

        public SidePanelItem(boolean isCategory, String categoryId, String label, String fileName, int y, boolean expanded) {
            this.isCategory = isCategory;
            this.categoryId = categoryId;
            this.label = label;
            this.fileName = fileName;
            this.y = y;
            this.expanded = expanded;
        }
    }

    private List<SidePanelItem> getSidePanelLayout() {
        List<String> minorFiles = new ArrayList<>();
        List<String> majorFiles = new ArrayList<>();
        List<String> milestoneFiles = new ArrayList<>();
        List<String> otherFiles = new ArrayList<>();

        for (String file : StonesStudioScreen.activePackFiles) {
            String lower = file.toLowerCase();
            if (lower.contains("minor")) {
                minorFiles.add(file);
            } else if (lower.contains("major")) {
                majorFiles.add(file);
            } else if (lower.contains("milestone")) {
                milestoneFiles.add(file);
            } else {
                otherFiles.add(file);
            }
        }

        List<SidePanelItem> panelItems = new ArrayList<>();
        int layoutY = 0;
        
        panelItems.add(new SidePanelItem(true, "minor", "Minor", null, layoutY, isMinorExpanded));
        layoutY += 15;
        if (isMinorExpanded) {
            for (String file : minorFiles) {
                panelItems.add(new SidePanelItem(false, null, "    📄 " + file, file, layoutY, false));
                layoutY += 15;
            }
        }

        panelItems.add(new SidePanelItem(true, "major", "Major", null, layoutY, isMajorExpanded));
        layoutY += 15;
        if (isMajorExpanded) {
            for (String file : majorFiles) {
                panelItems.add(new SidePanelItem(false, null, "    📄 " + file, file, layoutY, false));
                layoutY += 15;
            }
        }

        panelItems.add(new SidePanelItem(true, "milestone", "Milestone", null, layoutY, isMilestoneExpanded));
        layoutY += 15;
        if (isMilestoneExpanded) {
            for (String file : milestoneFiles) {
                panelItems.add(new SidePanelItem(false, null, "    📄 " + file, file, layoutY, false));
                layoutY += 15;
            }
        }

        if (!otherFiles.isEmpty()) {
            panelItems.add(new SidePanelItem(true, "other", "Andere", null, layoutY, isOtherExpanded));
            layoutY += 15;
            if (isOtherExpanded) {
                for (String file : otherFiles) {
                    panelItems.add(new SidePanelItem(false, null, "    📄 " + file, file, layoutY, false));
                    layoutY += 15;
                }
            }
        }
        
        return panelItems;
    }

    private int getSidePanelContentHeight() {
        List<SidePanelItem> layout = getSidePanelLayout();
        return layout.isEmpty() ? 0 : layout.get(layout.size() - 1).y + 15;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Font font = screen.getFont();
        if (font == null) return;

        graphics.fill(0, StudioMenuBar.HEIGHT, WIDTH, screen.height, 0xFF18181B); 
        graphics.fill(WIDTH, StudioMenuBar.HEIGHT, WIDTH + 1, screen.height, 0xFF444449); 
        
        StonesStudioScreen.PackInfo activePack = StonesStudioScreen.discoveredPacks.isEmpty() ? new StonesStudioScreen.PackInfo("Empty", false) : StonesStudioScreen.discoveredPacks.get(StonesStudioScreen.activePackIndex);
        
        StudioUIHelper.drawLabelWithTooltip(
            screen, graphics, font, 
            Component.translatable("gui.stones.studio.sidepanelrenderer.pack_label", activePack.name()), 
            10, StudioMenuBar.HEIGHT + 40, mouseX, mouseY, 
            Component.translatable("gui.stones.studio.sidepanelrenderer.pack_tooltip")
        );
        
        // --- BUTTON FÜR LIVE-RELOAD VOM SERVER ---
        // FIX: reloadBtnX/Y sauber berechnet und deklariert für identische Hitbox beim Rendern und Klicken
        int reloadBtnX = WIDTH - 25;
        int reloadBtnY = StudioMenuBar.HEIGHT + 40;
        int btnWidth = font.width("[↻]");
        int btnHeight = font.lineHeight;
        
        boolean hoverReload = mouseX >= reloadBtnX && mouseX < reloadBtnX + btnWidth && 
                              mouseY >= reloadBtnY && mouseY < reloadBtnY + btnHeight;
        
        graphics.drawString(font, Component.translatable("gui.stones.studio.sidepanelrenderer.reload_btn").getString(), reloadBtnX, reloadBtnY, hoverReload ? 0xFF55FF55 : 0xFFAAAAAA, false);
        
        if (hoverReload && screen.isBackgroundActive()) {
            screen.queueTooltip(Component.translatable("gui.stones.studio.sidepanelrenderer.reload_tooltip"), mouseX, mouseY);
        }
        
        int fileYStart = StudioMenuBar.HEIGHT + 55;
        
        graphics.enableScissor(0, fileYStart, WIDTH, screen.height - 10);
        graphics.pose().pushPose();
        graphics.pose().translate(0, -fileScrollY, 0);

        for (SidePanelItem item : getSidePanelLayout()) {
            int renderY = fileYStart + item.y;
            int absoluteMouseY = mouseY + (int)fileScrollY;

            if (item.isCategory) {
                String prefix = item.expanded ? "▼ 📂 " : "▶ 📁 ";
                
                Component categoryTooltip = switch (item.categoryId) {
                    case "minor" -> Component.translatable("gui.stones.studio.sidepanelrenderer.tooltip.minor");
                    case "major" -> Component.translatable("gui.stones.studio.sidepanelrenderer.tooltip.major");
                    case "milestone" -> Component.translatable("gui.stones.studio.sidepanelrenderer.tooltip.milestone");
                    default -> Component.translatable("gui.stones.studio.sidepanelrenderer.tooltip.other");
                };

                StudioUIHelper.drawLabelWithTooltip(screen, graphics, font, Component.literal(prefix + item.label), 10, renderY, mouseX, absoluteMouseY, categoryTooltip);
                
                if (mouseX >= 10 && mouseX < WIDTH - 10 && absoluteMouseY >= renderY && absoluteMouseY < renderY + 12) {
                    graphics.fill(10, renderY - 2, WIDTH - 10, renderY + 12, 0x1AFFFFFF);
                }
            } else {
                int color = item.fileName.equals(StonesStudioScreen.currentFileName) ? 0xFF55FF55 : 0xFFCCCCCC;
                graphics.drawString(font, item.label, 10, renderY, color, false);

                if (mouseX >= 10 && mouseX < WIDTH - 10 && absoluteMouseY >= renderY && absoluteMouseY < renderY + 12) {
                    graphics.fill(10, renderY - 2, WIDTH - 10, renderY + 12, 0x22FFFFFF);
                }
            }
        }

        graphics.pose().popPose();
        graphics.disableScissor();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // --- BUTTON KLICK-LOGIK FÜR LIVE-RELOAD ---
        // FIX: Verwendet exakt dieselben Hitbox-Koordinaten und Dimensionen wie beim Rendern
        int reloadBtnX = WIDTH - 25;
        int reloadBtnY = StudioMenuBar.HEIGHT + 40;
        int btnWidth = Minecraft.getInstance().font.width("[↻]");
        int btnHeight = Minecraft.getInstance().font.lineHeight;

        if (mouseX >= reloadBtnX && mouseX < reloadBtnX + btnWidth && 
            mouseY >= reloadBtnY && mouseY < reloadBtnY + btnHeight && button == 0) {
            
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );
            
            screen.requestActionWithUnsavedWarning(() -> {
                StonesStudioScreen.isWaitingForServer = true;
                StudioNetwork.CHANNEL.sendToServer(new StudioNetwork.C2SRequestPackList());
                
                if (!StonesStudioScreen.currentFileName.isEmpty()) {
                    StudioNetwork.CHANNEL.sendToServer(new StudioNetwork.C2SRequestRuneFile(StonesStudioScreen.currentFileName));
                }
                
                Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("gui.stones.studio.sidepanelrenderer.reload_success"), true
                );
            });
            return true;
        }

        if (mouseX < 10 || mouseX >= WIDTH - 10) return false;

        int fileYStart = StudioMenuBar.HEIGHT + 55;
        if (mouseY < fileYStart || mouseY >= screen.height - 10) return false;

        int absoluteMouseY = (int) (mouseY + fileScrollY);
        List<SidePanelItem> panelLayout = getSidePanelLayout();

        for (SidePanelItem item : panelLayout) {
            int absoluteItemY = fileYStart + item.y;
            if (absoluteMouseY >= absoluteItemY && absoluteMouseY < absoluteItemY + 15) {
                if (item.isCategory) {
                    if (item.categoryId.equals("minor")) isMinorExpanded = !isMinorExpanded;
                    else if (item.categoryId.equals("major")) isMajorExpanded = !isMajorExpanded;
                    else if (item.categoryId.equals("milestone")) isMilestoneExpanded = !isMilestoneExpanded;
                    else if (item.categoryId.equals("other")) isOtherExpanded = !isOtherExpanded;
                } else {
                    String selectedFile = item.fileName;
                    screen.requestActionWithUnsavedWarning(() -> {
                        StudioNetwork.CHANNEL.sendToServer(new StudioNetwork.C2SRequestRuneFile(selectedFile));
                        StonesStudioScreen.isWaitingForServer = true; 
                    });
                }
                return true;
            }
        }
        return false;
    }

    public void handleScroll(double delta) {
        int maxScroll = Math.max(0, getSidePanelContentHeight() - (screen.height - (StudioMenuBar.HEIGHT + 80)));
        fileScrollY = Math.max(0, Math.min(maxScroll, fileScrollY - (delta * 15)));
    }
}