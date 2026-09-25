package net.stones.client.gui.editor.section;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.stones.network.StudioNetwork;
import net.stones.client.gui.editor.StonesStudioScreen;

public class StudioProjectDialog {
    private final StonesStudioScreen screen;
    private boolean open = false;
    private EditBox inputField;
    private Button btnOk, btnCancel;

    public StudioProjectDialog(StonesStudioScreen screen) {
        this.screen = screen;
    }

    public boolean isOpen() { return open; }

    public void initDialog() {
        int dialogW = 200; int dialogH = 90;
        int x = (screen.width / 2) - (dialogW / 2); 
        int y = (screen.height / 2) - (dialogH / 2);

        // Text: "Pack Name"
        this.inputField = new EditBox(screen.getFont(), x + 15, y + 25, dialogW - 30, 20, net.minecraft.network.chat.Component.translatable("gui.stones.studio.studioprojectdialog.text_01"));
        this.inputField.setMaxLength(32);

        // Text: "Erstellen"
        this.btnOk = Button.builder(net.minecraft.network.chat.Component.translatable("gui.stones.studio.studioprojectdialog.text_02"), b -> { 
            String name = inputField.getValue().trim();
            if(!name.isEmpty()) {
                StudioNetwork.CHANNEL.sendToServer(new StudioNetwork.C2SProjectAction("CREATE", name));
                StonesStudioScreen.isWaitingForServer = true; 
            }
            this.open = false; 
        }).bounds(x + 15, y + 60, 80, 18).build();

        // Text: "Abbrechen"
        this.btnCancel = Button.builder(net.minecraft.network.chat.Component.translatable("gui.stones.studio.studioprojectdialog.text_03"), b -> { this.open = false; }).bounds(x + dialogW - 95, y + 60, 80, 18).build();
    }

    public void open() {
        this.open = true; 
        this.inputField.setValue("Neues_Projekt"); 
        this.inputField.setFocused(true);
        
        int x = (screen.width / 2) - 100; 
        int y = (screen.height / 2) - 45;
        this.inputField.setX(x + 15); this.inputField.setY(y + 25);
        this.btnOk.setX(x + 15); this.btnOk.setY(y + 60);
        this.btnCancel.setX(x + 105); this.btnCancel.setY(y + 60);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!open) return;
        
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 450); 
        
        graphics.fill(0, 0, screen.width, screen.height, 0x88000000); 
        int x = (screen.width / 2) - 100; int y = (screen.height / 2) - 45;

        graphics.fill(x, y, x + 200, y + 90, 0xFF18181B);
        graphics.renderOutline(x, y, 200, 90, 0xFF444449);
        // Text: "Neues Projekt anlegen"
        graphics.drawString(screen.getFont(), net.minecraft.network.chat.Component.translatable("gui.stones.studio.studioprojectdialog.text_04").getString(), x + 15, y + 10, 0xFFE4E4E7);

        this.inputField.render(graphics, mouseX, mouseY, partialTick);
        this.btnOk.render(graphics, mouseX, mouseY, partialTick);
        this.btnCancel.render(graphics, mouseX, mouseY, partialTick);
        
        graphics.pose().popPose();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (this.inputField.mouseClicked(mouseX, mouseY, button)) return true;
        if (this.btnOk.mouseClicked(mouseX, mouseY, button)) return true;
        if (this.btnCancel.mouseClicked(mouseX, mouseY, button)) return true;
        return true; 
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!open) return false;
        if (keyCode == 256) { this.open = false; return true; } 
        return this.inputField.keyPressed(keyCode, scanCode, modifiers);
    }

    public boolean charTyped(char codePoint, int modifiers) { 
        if (!open) return false;
        return this.inputField.charTyped(codePoint, modifiers); 
    }
}