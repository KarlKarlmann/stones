package net.stones.client.gui.editor.modal;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import net.stones.client.gui.editor.StonesStudioScreen;

/**
 * Die abstrakte Elternklasse für alle Modals (Dialogfenster) im Stones Studio.
 * Einheitliche Fokus- und Event-Verwaltung für alle Kind-Elemente.
 */
public abstract class AbstractStudioModal implements GuiEventListener {

    protected final StonesStudioScreen screen;
    protected final Font font;
    protected Component title; // Nicht mehr final, um dynamische Titel zu erlauben
    
    protected int width;  // Nicht mehr final, für dynamische Größen
    protected int height; // Nicht mehr final, für dynamische Größen
    protected int x;
    protected int y;

    // Verwaltet alle modalen Widgets (Textfelder, Buttons)
    protected final List<GuiEventListener> children = new ArrayList<>();
    private GuiEventListener focused;
    private boolean isDragging;

    public AbstractStudioModal(StonesStudioScreen screen, Component title, int width, int height) {
        this.screen = screen;
        this.font = screen.getFont();
        this.title = title;
        this.width = width;
        this.height = height;
        recenter();
    }

    public void recenter() {
        this.x = (screen.width - width) / 2;
        this.y = (screen.height - height) / 2;
    }

    public void init() {
        this.children.clear();
        recenter();
        initFields(x, y);
    }

    protected abstract void initFields(int x, int y);

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);

        graphics.fill(0, 0, screen.width, screen.height, 0xAA000000);
        graphics.fill(x, y, x + width, y + height, 0xFF18181B);
        graphics.renderOutline(x, y, width, height, 0xFF444449);

        graphics.drawString(font, title, x + 10, y + 10, 0xFFFFAA00);
        graphics.fill(x + 10, y + 22, x + width - 10, y + 23, 0xFF333333);

        renderContent(graphics, mouseX, mouseY, partialTick, x, y);

        for (GuiEventListener child : children) {
            if (child instanceof Renderable renderable) {
                renderable.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        graphics.pose().popPose();
    }

    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, int x, int y);

    protected <T extends GuiEventListener> T addModalWidget(T widget) {
        this.children.add(widget);
        return widget;
    }

    // DIE WICHTIGSTE ÄNDERUNG: Perfektes Fokus-Routing
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean childHandledClick = false;

        // 1. Klicks an Kinder (Textfelder, Buttons) weiterleiten
        for (GuiEventListener child : children) {
            if (child.mouseClicked(mouseX, mouseY, button)) {
                setFocused(child); // Setzt den Fokus auf das geklickte Element
                if (button == 0) {
                    this.setDragging(true);
                }
                childHandledClick = true;
                break;
            }
        }

        // 2. Klick ins Leere innerhalb des Fensters -> Fokus löschen!
        if (!childHandledClick && isMouseOver(mouseX, mouseY)) {
            setFocused(null);
            screen.setFocused(null); // Minecraft sagen: Nichts ist mehr fokussiert
            return true; // Fenster hat den Klick trotzdem geschluckt
        }

        return childHandledClick || isMouseOver(mouseX, mouseY);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.getFocused() != null) {
            return this.getFocused().charTyped(codePoint, modifiers);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            onCancel();
            return true;
        }
        if (this.getFocused() != null) {
            return this.getFocused().keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        for (GuiEventListener child : children) {
            if (child.mouseScrolled(mouseX, mouseY, delta)) {
                return true;
            }
        }
        return false;
    }

    public abstract void onCancel();

    public void setFocused(GuiEventListener listener) {
        if (this.focused != null) {
            this.focused.setFocused(false);
        }
        this.focused = listener;
        if (listener != null) {
            listener.setFocused(true);
            screen.setFocused(listener);
        }
    }

    public GuiEventListener getFocused() { return this.focused; }

    @Override
    public void setFocused(boolean focused) {
        if (!focused) setFocused(null);
    }

    @Override
    public boolean isFocused() { return this.focused != null; }
    public boolean isDragging() { return this.isDragging; }
    public void setDragging(boolean dragging) { this.isDragging = dragging; }
}