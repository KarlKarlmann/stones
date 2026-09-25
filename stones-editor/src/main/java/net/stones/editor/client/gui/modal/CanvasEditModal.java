package net.stones.editor.client.gui.modal;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Base64;
import java.util.function.Consumer;

/**
 * In-Game Pixelart-Editor für Runen- & Projektil-Sprites.
 * Unterstützt Malen, Radieren, Pipette, Farb-Palette, Laden bestehender Texturen
 * sowie echten OS-Dateidialog (TinyFileDialogs), Drag & Drop und Strg+V für Base64/Pfade.
 */
public class CanvasEditModal extends Screen {

    private final Screen parent;
    private final int gridSize;
    private final Consumer<String> onSaveCallback;
    private final int[][] pixels;

    private int activeColor = 0xFFFFFFFF; // Standardfarbe: Weiß (ARGB)
    private Tool currentTool = Tool.PENCIL;
    private GLFWDropCallback previousDropCallback = null;

    private final int[] PALETTE = new int[]{
        0xFFFFFFFF, 0xFF000000, 0xFFFF0000, 0xFF00FF00,
        0xFF0000FF, 0xFFFFFF00, 0xFFFF00FF, 0xFF00FFFF
    };

    private enum Tool { PENCIL, ERASER, PIPETTE }

    public CanvasEditModal(Screen parent, int gridSize, String initialBase64, Consumer<String> onSaveCallback) {
        super(Component.translatable("gui.stones.studio.canvas.title"));
        this.parent = parent;
        this.gridSize = gridSize;
        this.onSaveCallback = onSaveCallback;
        this.pixels = new int[gridSize][gridSize];

        loadInitialBase64(initialBase64);
    }

    public CanvasEditModal(Screen parent, int gridSize, Consumer<String> onSaveCallback) {
        this(parent, gridSize, "", onSaveCallback);
    }

    private void loadInitialBase64(String base64Data) {
        if (base64Data == null || base64Data.trim().isEmpty()) return;

        try {
            String clean = base64Data.trim();
            if (clean.contains("base64,")) {
                clean = clean.substring(clean.indexOf("base64,") + 7).trim();
            }
            byte[] bytes = Base64.getDecoder().decode(clean);
            BufferedImage bimg = ImageIO.read(new ByteArrayInputStream(bytes));
            if (bimg != null) loadBufferedImageToCanvas(bimg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int bottomY = this.height - 30;

        // 1. Standard-Werkzeug-Buttons
        Component pencilText = Component.literal("✏️ ").append(Component.translatable("gui.stones.studio.canvas.tool.pencil"));
        this.addRenderableWidget(Button.builder(pencilText, b -> currentTool = Tool.PENCIL)
                .bounds(centerX - 170, bottomY, 55, 20).build());

        Component eraserText = Component.literal("🧹 ").append(Component.translatable("gui.stones.studio.canvas.tool.eraser"));
        this.addRenderableWidget(Button.builder(eraserText, b -> currentTool = Tool.ERASER)
                .bounds(centerX - 110, bottomY, 55, 20).build());

        // 2. "Datei öffnen"-Button via TinyFileDialogs
        this.addRenderableWidget(Button.builder(Component.literal("📁 Datei..."), b -> openNativeFileDialog())
                .bounds(centerX - 50, bottomY, 65, 20).build());

        Component applyText = Component.literal("💾 ").append(Component.translatable("gui.stones.studio.canvas.btn.apply"));
        this.addRenderableWidget(Button.builder(applyText, b -> saveAndClose())
                .bounds(centerX + 20, bottomY, 65, 20).build());

        Component cancelText = Component.literal("❌ ").append(Component.translatable("gui.stones.studio.canvas.btn.cancel"));
        this.addRenderableWidget(Button.builder(cancelText, b -> onCancel())
                .bounds(centerX + 90, bottomY, 55, 20).build());

        // 3. Native Drag & Drop Callback für LWJGL/GLFW aktivieren
        long windowHandle = Minecraft.getInstance().getWindow().getWindow();
        previousDropCallback = GLFW.glfwSetDropCallback(windowHandle, (window, count, names) -> {
            if (count > 0) {
                String filePath = GLFWDropCallback.getName(names, 0);
                loadFileFromPath(filePath);
            }
        });
    }

    @Override
    public void removed() {
        // Drag & Drop Callback beim Schließen des Screens zurücksetzen
        long windowHandle = Minecraft.getInstance().getWindow().getWindow();
        GLFW.glfwSetDropCallback(windowHandle, previousDropCallback);
        super.removed();
    }

    /**
     * Öffnet das native OS-Dateiauswahlfenster über TinyFileDialogs.
     */
    private void openNativeFileDialog() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(3);
            filters.put(stack.UTF8("*.png"));
            filters.put(stack.UTF8("*.jpg"));
            filters.put(stack.UTF8("*.bmp"));
            filters.flip();

            String filePath = TinyFileDialogs.tinyfd_openFileDialog(
                    "Pixelart-Bild auswählen",
                    "",
                    filters,
                    "Bilder (*.png, *.jpg, *.bmp)",
                    false
            );

            if (filePath != null) {
                loadFileFromPath(filePath);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadFileFromPath(String filePath) {
        try {
            File file = new File(filePath);
            if (file.exists() && file.isFile()) {
                BufferedImage bimg = ImageIO.read(file);
                if (bimg != null) {
                    loadBufferedImageToCanvas(bimg);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int cellSize = 12;
        int canvasWidth = gridSize * cellSize;
        int canvasHeight = gridSize * cellSize;
        int startX = (this.width - canvasWidth) / 2;
        int startY = (this.height - canvasHeight) / 2 - 10;

        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                int pxX = startX + (c * cellSize);
                int pxY = startY + (r * cellSize);

                int bgTile = ((r + c) % 2 == 0) ? 0xFF888888 : 0xFFAAAAAA;
                guiGraphics.fill(pxX, pxY, pxX + cellSize, pxY + cellSize, bgTile);

                int pxColor = pixels[r][c];
                if ((pxColor & 0xFF000000) != 0) {
                    guiGraphics.fill(pxX, pxY, pxX + cellSize, pxY + cellSize, pxColor);
                }

                guiGraphics.fill(pxX, pxY, pxX + cellSize, pxY + 1, 0x44000000);
                guiGraphics.fill(pxX, pxY, pxX + 1, pxY + cellSize, 0x44000000);
            }
        }
        guiGraphics.renderOutline(startX - 1, startY - 1, canvasWidth + 2, canvasHeight + 2, 0xFFFFFFFF);

        // Palette
        int paletteX = startX + canvasWidth + 15;
        int paletteY = startY;
        int swatchSize = 16;

        guiGraphics.drawString(this.font, Component.translatable("gui.stones.studio.canvas.tool.picker").getString() + ":", paletteX, paletteY - 12, 0xFFFFFFFF);

        for (int i = 0; i < PALETTE.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int sX = paletteX + (col * (swatchSize + 4));
            int sY = paletteY + (row * (swatchSize + 4));

            int color = PALETTE[i];
            guiGraphics.fill(sX, sY, sX + swatchSize, sY + swatchSize, color);

            if (color == activeColor && currentTool == Tool.PENCIL) {
                guiGraphics.renderOutline(sX - 1, sY - 1, swatchSize + 2, swatchSize + 2, 0xFFFFFFFF);
            } else {
                guiGraphics.renderOutline(sX, sY, swatchSize, swatchSize, 0xFF000000);
            }
        }

        // Hinweiszeile über dem Canvas
        guiGraphics.drawString(this.font, "Datei reinziehen (Drag & Drop) oder Strg+V für Base64/Pfad", startX, startY - 15, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (handleCanvasInteraction(mouseX, mouseY)) return true;
            if (handlePaletteClick(mouseX, mouseY)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            if (handleCanvasInteraction(mouseX, mouseY)) return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean handleCanvasInteraction(double mouseX, double mouseY) {
        int cellSize = 12;
        int canvasWidth = gridSize * cellSize;
        int canvasHeight = gridSize * cellSize;
        int startX = (this.width - canvasWidth) / 2;
        int startY = (this.height - canvasHeight) / 2 - 10;

        if (mouseX >= startX && mouseX < startX + canvasWidth && mouseY >= startY && mouseY < startY + canvasHeight) {
            int c = (int) ((mouseX - startX) / cellSize);
            int r = (int) ((mouseY - startY) / cellSize);

            if (r >= 0 && r < gridSize && c >= 0 && c < gridSize) {
                if (currentTool == Tool.PENCIL) {
                    pixels[r][c] = activeColor;
                } else if (currentTool == Tool.ERASER) {
                    pixels[r][c] = 0x00000000;
                } else if (currentTool == Tool.PIPETTE) {
                    activeColor = pixels[r][c];
                    currentTool = Tool.PENCIL;
                }
                return true;
            }
        }
        return false;
    }

    private boolean handlePaletteClick(double mouseX, double mouseY) {
        int cellSize = 12;
        int canvasWidth = gridSize * cellSize;
        int canvasHeight = gridSize * cellSize;
        int startX = (this.width - canvasWidth) / 2;
        int startY = (this.height - canvasHeight) / 2 - 10;
        int paletteX = startX + canvasWidth + 15;
        int paletteY = startY;
        int swatchSize = 16;

        for (int i = 0; i < PALETTE.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int sX = paletteX + (col * (swatchSize + 4));
            int sY = paletteY + (row * (swatchSize + 4));

            if (mouseX >= sX && mouseX < sX + swatchSize && mouseY >= sY && mouseY < sY + swatchSize) {
                this.activeColor = PALETTE[i];
                this.currentTool = Tool.PENCIL;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 86 && Screen.hasControlDown()) { // Strg + V
            pasteFromClipboard();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Strg+V Import für Base64-Strings und kopierte Dateipfade via Minecraft GLFW.
     */
    private void pasteFromClipboard() {
        try {
            if (this.minecraft != null && this.minecraft.keyboardHandler != null) {
                String clipboardText = this.minecraft.keyboardHandler.getClipboard();
                if (clipboardText != null && !clipboardText.trim().isEmpty()) {
                    String text = clipboardText.trim();

                    if (text.contains("base64,")) {
                        text = text.substring(text.indexOf("base64,") + 7).trim();
                    }
                    try {
                        byte[] bytes = Base64.getDecoder().decode(text);
                        BufferedImage bimg = ImageIO.read(new ByteArrayInputStream(bytes));
                        if (bimg != null) {
                            loadBufferedImageToCanvas(bimg);
                            return;
                        }
                    } catch (Exception ignored) {}

                    loadFileFromPath(text);
                }
            }
        } catch (Exception ignored) {}
    }

    private void loadBufferedImageToCanvas(BufferedImage img) {
        BufferedImage resized = new BufferedImage(gridSize, gridSize, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = resized.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(img, 0, 0, gridSize, gridSize, null);
        g.dispose();

        for (int r = 0; r < gridSize; r++) {
            for (int c = 0; c < gridSize; c++) {
                pixels[r][c] = resized.getRGB(c, r);
            }
        }
    }

    private void saveAndClose() {
        try (NativeImage image = new NativeImage(gridSize, gridSize, false)) {
            for (int r = 0; r < gridSize; r++) {
                for (int c = 0; c < gridSize; c++) {
                    int argb = pixels[r][c];
                    int a = (argb >> 24) & 0xFF;
                    int rC = (argb >> 16) & 0xFF;
                    int gC = (argb >> 8) & 0xFF;
                    int bC = argb & 0xFF;
                    image.setPixelRGBA(c, r, (a << 24) | (bC << 16) | (gC << 8) | rC);
                }
            }
            byte[] pngBytes = image.asByteArray();
            String base64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes);

            onSaveCallback.accept(base64);
            onCancel();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onCancel() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}