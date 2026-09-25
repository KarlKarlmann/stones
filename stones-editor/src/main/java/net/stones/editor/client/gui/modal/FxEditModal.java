package net.stones.editor.client.gui.modal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.stones.editor.client.gui.StonesStudioScreen;
import net.stones.editor.client.gui.TreeNode;
import net.stones.editor.client.gui.StudioSerializer;
import net.stones.editor.client.gui.section.StudioContextMenu;
import net.stones.editor.client.gui.widget.StudioSuggestTextField;
import net.stones.editor.client.gui.widget.StudioTextField;

import java.util.*;

/**
 * Dedizierter VFX & Sprite Prototyper für das Stones Studio.
 * Enthält volle Kontrolle über Geometrie, Physik, Textur (mit Canvas-Anbindung)
 * sowie eine interaktive Gradient-Bar für N Farb- & Alpha-Keyframes.
 */
public class FxEditModal extends AbstractStudioModal {

    private final TreeNode targetNode;

    // --- Eingabefelder ---
    private StudioSuggestTextField.UniversalSuggestField fldPos;
    private StudioSuggestTextField.UniversalSuggestField fldTexture;
    private StudioTextField fldLifetime;
    private StudioTextField fldScale;
    private StudioTextField fldGrowth;
    private StudioTextField fldSpeed;
    private StudioTextField fldDrag;
    private StudioTextField fldGravity;
    private StudioTextField fldSpin;

    // --- Dropdowns / State Buttons ---
    private Button btnFacing;
    private String facingState = "BILLBOARD"; // BILLBOARD, GROUND_FLAT, VELOCITY_ALIGNED

    private Button btnBlendMode;
    private String blendState = "ADDITIVE"; // ADDITIVE, ALPHA

    // --- Keyframe Gradient Bar State ---
    private final List<Keyframe> keyframes = new ArrayList<>();
    private Keyframe selectedKeyframe = null;
    private boolean isDraggingHandle = false;
    private StudioTextField fldHexColor;
    private StudioTextField fldAlpha;

    private int barX, barY, barW, barH;

    public static class Keyframe {
        public float time; // 0.0f bis 1.0f
        public int color;  // ARGB (0xAARRGGBB)

        public Keyframe(float time, int color) {
            this.time = Mth.clamp(time, 0.0f, 1.0f);
            this.color = color;
        }
    }

    public FxEditModal(StonesStudioScreen screen, TreeNode node) {
        super(screen, Component.literal("✨ VFX & Sprite Prototyper"), 480, 260);
        this.targetNode = node;
        this.loadKeyframesFromNode();
        this.init();
    }

    private void loadKeyframesFromNode() {
        keyframes.clear();
        JsonObject json = targetNode.jsonData;
        if (json.has("keyframes") && json.get("keyframes").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("keyframes");
            for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                    JsonObject k = el.getAsJsonObject();
                    float t = k.has("time") ? k.get("time").getAsFloat() : 0.0f;
                    String hex = k.has("color") ? k.get("color").getAsString() : "#FFFFFF";
                    float alpha = k.has("alpha") ? k.get("alpha").getAsFloat() : 1.0f;
                    keyframes.add(new Keyframe(t, parseArgb(hex, alpha)));
                }
            }
        }
        // Fallback: Mindestens 2 Keyframes
        if (keyframes.size() < 2) {
            keyframes.clear();
            keyframes.add(new Keyframe(0.0f, 0xFFFF0000)); // Start Rot
            keyframes.add(new Keyframe(1.0f, 0x00000000)); // Ende Unsichtbar
        }
        sortKeyframes();
        selectedKeyframe = keyframes.get(0);
    }

    private void sortKeyframes() {
        keyframes.sort(Comparator.comparingDouble(k -> k.time));
    }

    @Override
    protected void initFields(int x, int y) {
        JsonObject json = targetNode.jsonData;

        // --- ZEILE 1: Anker & Textur ---
        fldPos = addModalWidget(new StudioSuggestTextField.UniversalSuggestField(screen, font, x + 50, y + 30, 110, 14, Component.literal("")));
        fldPos.setContextNode(targetNode);
        fldPos.setValue(json.has("pos") ? json.get("pos").getAsString() : "$hitPos");

        fldTexture = addModalWidget(new StudioSuggestTextField.UniversalSuggestField(screen, font, x + 215, y + 30, 150, 14, Component.literal("")));
        fldTexture.setContextNode(targetNode);
        fldTexture.setValue(json.has("texture") ? json.get("texture").getAsString() : "stones:textures/fx/smoke.png");

        // Canvas Button
        addModalWidget(Button.builder(Component.literal("🎨 Canvas"), b -> {
			screen.serializeActiveTree();
            net.minecraft.client.Minecraft.getInstance().setScreen(new CanvasEditModal(screen, 16, fldTexture.getValue(), base64 -> {
                fldTexture.setValue(base64);
            }));
        }).bounds(x + 370, y + 30, 60, 14).build());

        // --- ZEILE 2: Ausrichtung & Rendermodus ---
        facingState = json.has("facing") ? json.get("facing").getAsString() : "BILLBOARD";
        btnFacing = addModalWidget(Button.builder(Component.literal(facingState), b -> {
            facingState = switch (facingState) {
                case "BILLBOARD" -> "GROUND_FLAT";
                case "GROUND_FLAT" -> "VELOCITY_ALIGNED";
                default -> "BILLBOARD";
            };
            btnFacing.setMessage(Component.literal(facingState));
        }).bounds(x + 60, y + 52, 100, 14).build());

        blendState = json.has("blend_mode") ? json.get("blend_mode").getAsString() : "ADDITIVE";
        btnBlendMode = addModalWidget(Button.builder(Component.literal(blendState), b -> {
            blendState = blendState.equals("ADDITIVE") ? "ALPHA" : "ADDITIVE";
            btnBlendMode.setMessage(Component.literal(blendState));
        }).bounds(x + 235, y + 52, 80, 14).build());

        fldLifetime = addModalWidget(new StudioTextField(screen, font, x + 380, y + 52, 35, 14, Component.literal(""), Component.literal("Dauer in Ticks")));
        fldLifetime.setValue(json.has("lifetime") ? json.get("lifetime").getAsString() : "30");

        // --- ZEILE 3: Skalierung & Rotation ---
        fldScale = addModalWidget(new StudioTextField(screen, font, x + 60, y + 74, 40, 14, Component.literal(""), Component.literal("Start-Größe")));
        fldScale.setValue(json.has("initial_scale") ? json.get("initial_scale").getAsString() : "1.0");

        fldGrowth = addModalWidget(new StudioTextField(screen, font, x + 160, y + 74, 40, 14, Component.literal(""), Component.literal("Wachstum pro Tick")));
        fldGrowth.setValue(json.has("growth_rate") ? json.get("growth_rate").getAsString() : "0.0");

        fldSpin = addModalWidget(new StudioTextField(screen, font, x + 280, y + 74, 40, 14, Component.literal(""), Component.literal("Drehtempo")));
        fldSpin.setValue(json.has("spin_speed") ? json.get("spin_speed").getAsString() : "0.0");

        // --- ZEILE 4: Bewegung & Physik ---
        fldSpeed = addModalWidget(new StudioTextField(screen, font, x + 60, y + 96, 40, 14, Component.literal(""), Component.literal("Tempo")));
        fldSpeed.setValue(json.has("speed") ? json.get("speed").getAsString() : "0.5");

        fldDrag = addModalWidget(new StudioTextField(screen, font, x + 160, y + 96, 40, 14, Component.literal(""), Component.literal("Luftwiderstand (0.0-1.0)")));
        fldDrag.setValue(json.has("drag") ? json.get("drag").getAsString() : "0.92");

        fldGravity = addModalWidget(new StudioTextField(screen, font, x + 280, y + 96, 40, 14, Component.literal(""), Component.literal("Auftrieb/Schwerkraft")));
        fldGravity.setValue(json.has("gravity") ? json.get("gravity").getAsString() : "0.0");

        // --- ZEILE 5 & 6: Interactive Gradient Timeline Bar ---
        this.barX = x + 15;
        this.barY = y + 140;
        this.barW = width - 30;
        this.barH = 18;

        fldHexColor = addModalWidget(new StudioTextField(screen, font, x + 15, y + 185, 70, 14, Component.literal(""), Component.literal("Hex Farbe")));
        fldAlpha = addModalWidget(new StudioTextField(screen, font, x + 95, y + 185, 40, 14, Component.literal(""), Component.literal("Alpha (0.0-1.0)")));

        updateSelectedKeyframeInputs();

        fldHexColor.setResponder(s -> {
            if (selectedKeyframe != null && s.startsWith("#") && s.length() == 7) {
                float a = (selectedKeyframe.color >>> 24) / 255.0f;
                selectedKeyframe.color = parseArgb(s, a);
            }
        });

        fldAlpha.setResponder(s -> {
            if (selectedKeyframe != null) {
                try {
                    float a = Float.parseFloat(s);
                    int rgb = selectedKeyframe.color & 0x00FFFFFF;
                    selectedKeyframe.color = (((int) (Mth.clamp(a, 0f, 1f) * 255)) << 24) | rgb;
                } catch (Exception ignored) {}
            }
        });

        // --- OK / ABBRECHEN BUTTONS ---
        addModalWidget(Button.builder(Component.literal("Übernehmen"), b -> {
            saveToNode();
            screen.closeModal();
        }).bounds(x + (width / 2) - 105, y + height - 25, 100, 20).build());

        addModalWidget(Button.builder(Component.literal("Abbrechen"), b -> screen.closeModal())
                .bounds(x + (width / 2) + 5, y + height - 25, 100, 20).build());
    }

    private void updateSelectedKeyframeInputs() {
        if (selectedKeyframe != null) {
            int rgb = selectedKeyframe.color & 0x00FFFFFF;
            float a = (selectedKeyframe.color >>> 24) / 255.0f;
            fldHexColor.setValue(String.format("#%06X", rgb));
            fldAlpha.setValue(String.format(Locale.ROOT, "%.2f", a));
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick, int x, int y) {
        // Labels
        g.drawString(font, "Pos:", x + 15, y + 33, 0xFFAAAAAA);
        g.drawString(font, "Textur:", x + 170, y + 33, 0xFFAAAAAA);

        g.drawString(font, "Facing:", x + 15, y + 55, 0xFFAAAAAA);
        g.drawString(font, "Blend:", x + 175, y + 55, 0xFFAAAAAA);
        g.drawString(font, "Ticks:", x + 330, y + 55, 0xFFAAAAAA);

        g.drawString(font, "Größe:", x + 15, y + 77, 0xFFAAAAAA);
        g.drawString(font, "Growth:", x + 110, y + 77, 0xFFAAAAAA);
        g.drawString(font, "Spin:", x + 235, y + 77, 0xFFAAAAAA);

        g.drawString(font, "Speed:", x + 15, y + 99, 0xFFAAAAAA);
        g.drawString(font, "Drag:", x + 110, y + 99, 0xFFAAAAAA);
        g.drawString(font, "Gravity:", x + 225, y + 99, 0xFFAAAAAA);

        // Timeline Header
        g.drawString(font, "🎨 Farbdynamik (Klick = Punkt setzen, Drag = Verschieben, Rechtsklick = Löschen):", x + 15, y + 125, 0xFFFFAA00);

        // --- GRADIENT BAR RENDERING ---
        renderGradientBar(g);

        // Selected Color Swatch
        if (selectedKeyframe != null) {
            g.fill(x + 145, y + 185, x + 175, y + 199, selectedKeyframe.color);
            g.renderOutline(x + 145, y + 185, 30, 14, 0xFFFFFFFF);
        }
    }

    private void renderGradientBar(GuiGraphics g) {
        // 1. Schachbrett-Hintergrund für Transparenz (Alpha)
        g.fill(barX, barY, barX + barW, barY + barH, 0xFF222222);

        // 2. Pixelweiser/Sliced Farbverlauf über N Keyframes
        for (int i = 0; i < barW; i++) {
            float progress = (float) i / (float) barW;
            int interpolatedColor = evaluateGradient(progress);
            g.fill(barX + i, barY, barX + i + 1, barY + barH, interpolatedColor);
        }
        g.renderOutline(barX - 1, barY - 1, barW + 2, barH + 2, 0xFF555555);

        // 3. Handles (Anfasser) zeichnen
        for (Keyframe k : keyframes) {
            int handleX = barX + (int) (k.time * barW);
            boolean isSelected = (k == selectedKeyframe);

            int outlineColor = isSelected ? 0xFFFFAA00 : 0xFFFFFFFF;
            g.fill(handleX - 3, barY - 3, handleX + 3, barY + barH + 3, k.color | 0xFF000000);
            g.renderOutline(handleX - 4, barY - 4, 8, barH + 8, outlineColor);
        }
    }

    private int evaluateGradient(float time) {
        if (keyframes.isEmpty()) return 0xFFFFFFFF;
        if (keyframes.size() == 1) return keyframes.get(0).color;

        if (time <= keyframes.get(0).time) return keyframes.get(0).color;
        if (time >= keyframes.get(keyframes.size() - 1).time) return keyframes.get(keyframes.size() - 1).color;

        for (int i = 0; i < keyframes.size() - 1; i++) {
            Keyframe k1 = keyframes.get(i);
            Keyframe k2 = keyframes.get(i + 1);
            if (time >= k1.time && time <= k2.time) {
                float factor = (time - k1.time) / (k2.time - k1.time);
                return interpolateColor(k1.color, k2.color, factor);
            }
        }
        return keyframes.get(0).color;
    }

    private int interpolateColor(int c1, int c2, float factor) {
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * factor);
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Prüfe Klick auf Gradient-Bar
        if (mouseX >= barX - 5 && mouseX <= barX + barW + 5 && mouseY >= barY - 5 && mouseY <= barY + barH + 5) {
            float clickTime = Mth.clamp((float) (mouseX - barX) / (float) barW, 0.0f, 1.0f);

            // Rechter Mausklick = Handle löschen
            if (button == 1) {
                Keyframe clickedHandle = findHandleAt(mouseX);
                if (clickedHandle != null && keyframes.size() > 2) {
                    keyframes.remove(clickedHandle);
                    selectedKeyframe = keyframes.get(0);
                    updateSelectedKeyframeInputs();
                    return true;
                }
            }

            // Linker Mausklick
            if (button == 0) {
                Keyframe clickedHandle = findHandleAt(mouseX);
                if (clickedHandle != null) {
                    selectedKeyframe = clickedHandle;
                    isDraggingHandle = true;
                } else {
                    // Punkt auf leere Stelle setzen -> neuen Keyframe erzeugen!
                    Keyframe newKeyframe = new Keyframe(clickTime, evaluateGradient(clickTime));
                    keyframes.add(newKeyframe);
                    sortKeyframes();
                    selectedKeyframe = newKeyframe;
                    isDraggingHandle = true;
                }
                updateSelectedKeyframeInputs();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingHandle && selectedKeyframe != null) {
            selectedKeyframe.time = Mth.clamp((float) (mouseX - barX) / (float) barW, 0.0f, 1.0f);
            sortKeyframes();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDraggingHandle = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private Keyframe findHandleAt(double mouseX) {
        for (Keyframe k : keyframes) {
            int handleX = barX + (int) (k.time * barW);
            if (Math.abs(mouseX - handleX) <= 6) {
                return k;
            }
        }
        return null;
    }

    private int parseArgb(String hex, float alpha) {
        try {
            int rgb = Integer.parseInt(hex.replace("#", ""), 16);
            int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255);
            return (a << 24) | rgb;
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }

    private void saveToNode() {
        StudioContextMenu.saveState();
        JsonObject root = targetNode.jsonData;

        root.addProperty("type", "stones:spawn_sprite");
        root.addProperty("pos", fldPos.getValue().trim());
        root.addProperty("texture", fldTexture.getValue().trim());
        root.addProperty("facing", facingState);
        root.addProperty("blend_mode", blendState);

        try { root.addProperty("lifetime", Integer.parseInt(fldLifetime.getValue().trim())); } catch (Exception ignored) {}
        try { root.addProperty("initial_scale", Float.parseFloat(fldScale.getValue().trim())); } catch (Exception ignored) {}
        try { root.addProperty("growth_rate", Float.parseFloat(fldGrowth.getValue().trim())); } catch (Exception ignored) {}
        try { root.addProperty("spin_speed", Float.parseFloat(fldSpin.getValue().trim())); } catch (Exception ignored) {}

        try { root.addProperty("speed", Float.parseFloat(fldSpeed.getValue().trim())); } catch (Exception ignored) {}
        try { root.addProperty("drag", Float.parseFloat(fldDrag.getValue().trim())); } catch (Exception ignored) {}
        try { root.addProperty("gravity", Float.parseFloat(fldGravity.getValue().trim())); } catch (Exception ignored) {}

        // Keyframes als JSON Array speichern
        JsonArray arr = new JsonArray();
        sortKeyframes();
        for (Keyframe k : keyframes) {
            JsonObject kObj = new JsonObject();
            kObj.addProperty("time", Float.parseFloat(String.format(Locale.ROOT, "%.3f", k.time)));
            int rgb = k.color & 0x00FFFFFF;
            float a = (k.color >>> 24) / 255.0f;
            kObj.addProperty("color", String.format("#%06X", rgb));
            kObj.addProperty("alpha", Float.parseFloat(String.format(Locale.ROOT, "%.2f", a)));
            arr.add(kObj);
        }
        root.add("keyframes", arr);

        targetNode.readableText = StudioSerializer.getReadableText(root, targetNode.type);
    }

    @Override
    public void onCancel() {
        screen.closeModal();
    }
}