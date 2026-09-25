package net.stones.client.gui.editor.section;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.stones.network.StudioNetwork;

import java.io.File;
import net.stones.client.gui.editor.StonesStudioScreen;

public class StudioMenuBar {
    private final StonesStudioScreen screen;
    private boolean packOpen = false;
    private boolean openSelectOpen = false;
    
    private final int menuPackX = 10;
    private final int packWidth = 45;
    
    private final int menuSaveX = 65;
    private final int saveWidth = 80;

    private final int menuReloadX = 155; 
    private final int reloadWidth = 100;
    
    public static final int HEIGHT = 34;

    public StudioMenuBar(StonesStudioScreen screen) {
        this.screen = screen;
    }

    public boolean isOpen() { return packOpen || openSelectOpen; }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, screen.width, HEIGHT, 0xF20F0F12); 
        graphics.fill(0, HEIGHT - 1, screen.width, HEIGHT, 0x44FFFFFF); 
        
        StonesStudioScreen.PackInfo activePack = StonesStudioScreen.discoveredPacks.isEmpty() ? new StonesStudioScreen.PackInfo("Empty", false) : StonesStudioScreen.discoveredPacks.get(StonesStudioScreen.activePackIndex);
        boolean isActiveInConfig = activePack.name().equals(StonesStudioScreen.serverActivePackName);
        
        // Dynamisch den aktiven Status holen und als Literal an den Namen anfügen
        String activeStatus = isActiveInConfig ? " " + Component.translatable("gui.stones.studio.studiomenubar.active_in_game").getString() : "";
        Component text = Component.literal("Studio: " + activePack.name() + activeStatus);
        graphics.drawString(screen.getFont(), text, (screen.width / 2) - (screen.getFont().width(text) / 2), 4, isActiveInConfig ? 0xFF55FF55 : 0xFFBBBBBB);

        boolean hoverPack = packOpen || (mouseX >= menuPackX && mouseX < menuPackX + packWidth && mouseY >= 16 && mouseY < 32);
        graphics.drawString(screen.getFont(), Component.translatable("gui.stones.studio.studiomenubar.button.project").getString(), menuPackX, 18, hoverPack ? 0xFFFFFFFF : 0xFFAAAAAA);
        
        boolean hoverSave = mouseX >= menuSaveX && mouseX < menuSaveX + saveWidth && mouseY >= 16 && mouseY < 32;
        int saveColor = StonesStudioScreen.currentFileName.isEmpty() ? 0xFF555555 : (hoverSave ? 0xFF55FF55 : 0xFF00AA00);
        graphics.drawString(screen.getFont(), Component.translatable("gui.stones.studio.studiomenubar.button.save").getString(), menuSaveX, 18, saveColor);

        boolean hoverReload = mouseX >= menuReloadX && mouseX < menuReloadX + reloadWidth && mouseY >= 16 && mouseY < 32;
        graphics.drawString(screen.getFont(), Component.translatable("gui.stones.studio.studiomenubar.button.apply").getString(), menuReloadX, 18, hoverReload ? 0xFFFFAA00 : 0xFFAAAAAA);

        boolean hoverExit = mouseX >= screen.width - 60 && mouseX < screen.width - 10 && mouseY >= 16 && mouseY < 32;
        graphics.drawString(screen.getFont(), Component.translatable("gui.stones.studio.studiomenubar.button.exit").getString(), screen.width - 60, 18, hoverExit ? 0xFFFF5555 : 0xFFAAAAAA);
    }

    public void renderDropdowns(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!isOpen()) return;
        
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 450); 
        
        int dropdownY = HEIGHT; 
        int itemH = 18;

        if (packOpen) {
            StonesStudioScreen.PackInfo activeInfo = StonesStudioScreen.discoveredPacks.isEmpty() ? new StonesStudioScreen.PackInfo("Empty", false) : StonesStudioScreen.discoveredPacks.get(StonesStudioScreen.activePackIndex);
            boolean isActiveInConfig = activeInfo.name().equals(StonesStudioScreen.serverActivePackName);

            String actionStr = isActiveInConfig ? Component.translatable("gui.stones.studio.studiomenubar.dropdown.deactivate").getString() : Component.translatable("gui.stones.studio.studiomenubar.dropdown.activate").getString();
            boolean isDedicated = Minecraft.getInstance().getSingleplayerServer() == null;
            String folderOptionName = isDedicated ? Component.translatable("gui.stones.studio.studiomenubar.dropdown.folder_local").getString() : Component.translatable("gui.stones.studio.studiomenubar.dropdown.folder_server").getString();

            String[] items = { 
                Component.translatable("gui.stones.studio.studiomenubar.dropdown.new_project").getString(), 
                Component.translatable("gui.stones.studio.studiomenubar.dropdown.open_project").getString(), 
                Component.translatable("gui.stones.studio.studiomenubar.dropdown.load_active").getString(), 
                actionStr, 
                folderOptionName 
            };
            int dropW = 150;

            graphics.fill(menuPackX, dropdownY, menuPackX + dropW, dropdownY + (items.length * itemH), 0xF20B0B0C);
            graphics.renderOutline(menuPackX, dropdownY, dropW, items.length * itemH, 0x44FFFFFF);

            for (int i = 0; i < items.length; i++) {
                int itemY = dropdownY + (i * itemH);
                boolean hover = mouseX >= menuPackX && mouseX < menuPackX + dropW && mouseY >= itemY && mouseY < itemY + itemH;
                
                boolean disabled = ((i == 2) && isActiveInConfig) || ((i == 4) && isDedicated);
                int color = disabled ? 0xFF555555 : (hover ? 0xFFFFFFFF : 0xFFCCCCCC);
                
                if (!disabled && i == 3) {
                    color = isActiveInConfig ? (hover ? 0xFFFF5555 : 0xFFAA0000) : (hover ? 0xFF55FF55 : 0xFF00AA00);
                }

                if (hover && !disabled) {
                    graphics.fill(menuPackX + 1, itemY, menuPackX + dropW - 1, itemY + itemH, 0x22FFFFFF);
                }
                graphics.drawString(screen.getFont(), items[i], menuPackX + 6, itemY + 5, color);
            }
        }

        if (openSelectOpen) {
            int openX = menuPackX + 145; int openY = dropdownY + itemH; 
            int dropW = 180;

            graphics.fill(openX, openY, openX + dropW, openY + (StonesStudioScreen.discoveredPacks.size() * itemH), 0xF20B0B0C);
            graphics.renderOutline(openX, openY, dropW, StonesStudioScreen.discoveredPacks.size() * itemH, 0x44FFFFFF);

            for (int i = 0; i < StonesStudioScreen.discoveredPacks.size(); i++) {
                int itemY = openY + (i * itemH);
                boolean hover = mouseX >= openX && mouseX < openX + dropW && mouseY >= itemY && mouseY < itemY + itemH;
                boolean active = (i == StonesStudioScreen.activePackIndex);

                if (hover) graphics.fill(openX + 1, itemY, openX + dropW - 1, itemY + itemH, 0x22FFFFFF);
                
                StonesStudioScreen.PackInfo info = StonesStudioScreen.discoveredPacks.get(i);
                String icon = info.isZip() ? "📦 " : "📁 ";
                int color = active ? 0xFFFFAA00 : (hover ? 0xFFFFFFFF : 0xFFDDDDDD);
                graphics.drawString(screen.getFont(), icon + info.name(), openX + 6, itemY + 5, color);
            }
        }
        
        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int dropdownY = HEIGHT; 
        int itemH = 18;

        if (mouseX >= menuPackX && mouseX < menuPackX + packWidth && mouseY >= 16 && mouseY < 32) {
            packOpen = !packOpen; 
            openSelectOpen = false; 
            return true;
        }

        if (mouseX >= menuSaveX && mouseX < menuSaveX + saveWidth && mouseY >= 16 && mouseY < 32) {
            if (!StonesStudioScreen.currentFileName.isEmpty()) {
                JsonObject savedJson = screen.serializeActiveTree();
                
                // --- NEU: Nach dem Speichern ist das unser neuer, sicherer Baseline-Snapshot ---
                screen.setLastSavedJson(savedJson.toString()); 
                
                StudioNetwork.CHANNEL.sendToServer(new StudioNetwork.C2SSaveRuneFile(StonesStudioScreen.currentFileName, savedJson.toString()));
            }
            return true;
        }

        if (mouseX >= menuReloadX && mouseX < menuReloadX + reloadWidth && mouseY >= 16 && mouseY < 32) {
            // --- NEU: Das zerschießt ebenfalls ungespeicherte Änderungen! Abgesichert. ---
            screen.requestActionWithUnsavedWarning(() -> {
                StudioNetwork.CHANNEL.sendToServer(new StudioNetwork.C2STriggerReload());
            });
            return true;
        }

        if (mouseX >= screen.width - 60 && mouseX < screen.width - 10 && mouseY >= 16 && mouseY < 32) {
            screen.onClose();
            return true;
        }

        if (packOpen) {
            int dropW = 150;
            if (mouseX >= menuPackX && mouseX < menuPackX + dropW && mouseY >= dropdownY && mouseY < dropdownY + (5 * itemH)) {
                int clickedIdx = (int) ((mouseY - dropdownY) / itemH);
                boolean isDedicated = Minecraft.getInstance().getSingleplayerServer() == null;

                if (clickedIdx == 0) {
                    screen.requestActionWithUnsavedWarning(() -> {
                        screen.openNewProjectDialog();
                    });
                } 
                else if (clickedIdx == 1) { 
                    openSelectOpen = !openSelectOpen; 
                    return true; 
                } 
                else if (clickedIdx == 2) {
                    screen.requestActionWithUnsavedWarning(() -> {
                        for (int i = 0; i < StonesStudioScreen.discoveredPacks.size(); i++) {
                            if (StonesStudioScreen.discoveredPacks.get(i).name().equals(StonesStudioScreen.serverActivePackName)) {
                                StonesStudioScreen.activePackIndex = i;
                                StudioNetwork.CHANNEL.sendToServer(new StudioNetwork.C2SRequestPackList());
                                break;
                            }
                        }
                    });
                } 
                else if (clickedIdx == 3) {
                    screen.requestActionWithUnsavedWarning(() -> {
                        StonesStudioScreen.PackInfo activeInfo = StonesStudioScreen.discoveredPacks.isEmpty() ? new StonesStudioScreen.PackInfo("Empty", false) : StonesStudioScreen.discoveredPacks.get(StonesStudioScreen.activePackIndex);
                        boolean isActiveInConfig = activeInfo.name().equals(StonesStudioScreen.serverActivePackName);
                        
                        if (isActiveInConfig) {
                            StudioNetwork.CHANNEL.sendToServer(new StudioNetwork.C2SProjectAction("DEACTIVATE", activeInfo.name()));
                        } else {
                            StudioNetwork.CHANNEL.sendToServer(new StudioNetwork.C2SProjectAction("ACTIVATE", activeInfo.name()));
                        }
                        StonesStudioScreen.isWaitingForServer = true; 
                    });
                } 
                else if (clickedIdx == 4 && !isDedicated) {
                    File dpDir = new File(Minecraft.getInstance().gameDirectory, "datapacks");
                    File specificPack = new File(dpDir, StonesStudioScreen.discoveredPacks.get(StonesStudioScreen.activePackIndex).name());
                    net.minecraft.Util.getPlatform().openFile(specificPack.exists() ? specificPack : dpDir);
                }
                
                if (clickedIdx != 1) {
                    packOpen = false; openSelectOpen = false; 
                }
                return true;
            }
        }

        if (openSelectOpen) {
            int openX = menuPackX + 145; int openY = dropdownY + itemH; 
            if (mouseX >= openX && mouseX < openX + 180 && mouseY >= openY && mouseY < openY + (StonesStudioScreen.discoveredPacks.size() * itemH)) {
                int selectedIdx = (int) ((mouseY - openY) / itemH);
                
                screen.requestActionWithUnsavedWarning(() -> {
                    StonesStudioScreen.activePackIndex = selectedIdx;
                    String selected = StonesStudioScreen.discoveredPacks.get(selectedIdx).name();
                    StudioNetwork.CHANNEL.sendToServer(new StudioNetwork.C2SProjectAction("ACTIVATE", selected));
                    StonesStudioScreen.isWaitingForServer = true;
                });

                packOpen = false; openSelectOpen = false; 
                return true;
            }
        }

        if (packOpen || openSelectOpen) { packOpen = false; openSelectOpen = false; return true; }

        return false;
    }
}