package net.stones.editor.client.gui.modal;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.stones.client.fx.BeamType;
import net.stones.editor.client.gui.StonesStudioScreen;
import net.stones.editor.client.gui.TreeNode;
import net.stones.editor.client.gui.StudioSerializer;
import net.stones.editor.client.gui.section.StudioContextMenu;
import net.stones.editor.client.gui.widget.StudioSuggestTextField;
import net.stones.editor.client.gui.widget.StudioTextField;

/**
 * Dedizierter Beam & Strahl Prototyper für das Stones Studio.
 * Ermöglicht volle Kontrolle über Start-/Endvektoren, BeamType, Kern- & Coronabreite,
 * Textur (inkl. Canvas-Anbindung), Farben, UV-Scrolling und Helix-Orbits.
 */
public class BeamEditModal extends AbstractStudioModal {

    private final TreeNode targetNode;

    // --- Eingabefelder ---
    private StudioSuggestTextField.UniversalSuggestField fldStart;
    private StudioSuggestTextField.UniversalSuggestField fldEnd;
    private StudioSuggestTextField.UniversalSuggestField fldTexture;

    private StudioTextField fldCoreWidth;
    private StudioTextField fldCoronaWidth;
    private StudioTextField fldLifetime;

    private StudioTextField fldHexColor;
    private StudioTextField fldAlpha;

    private StudioTextField fldUvScroll;
    private StudioTextField fldUvRepeat;

    private StudioTextField fldHelixRadius;
    private StudioTextField fldHelixFreq;
    private StudioTextField fldHelixSpeed;

    // --- Dropdowns ---
    private Button btnBeamType;
    private String selectedBeamType = "LASER";

    public BeamEditModal(StonesStudioScreen screen, TreeNode node) {
        super(screen, Component.literal("⚡ Beam & Strahl Prototyper"), 500, 275);
        this.targetNode = node;
        this.init();
    }

    @Override
    protected void initFields(int x, int y) {
        JsonObject json = targetNode.jsonData;

        // --- ZEILE 1: Start & Ziel Vektoren ---
        fldStart = addModalWidget(new StudioSuggestTextField.UniversalSuggestField(screen, font, x + 50, y + 30, 170, 14, Component.literal("")));
        fldStart.setContextNode(targetNode);
        fldStart.setValue(json.has("start") ? json.get("start").getAsString() : "$player.eye_pos");

        fldEnd = addModalWidget(new StudioSuggestTextField.UniversalSuggestField(screen, font, x + 265, y + 30, 170, 14, Component.literal("")));
        fldEnd.setContextNode(targetNode);
        fldEnd.setValue(json.has("end") ? json.get("end").getAsString() : "$hitPos");

        // --- ZEILE 2: Beam-Typ & Textur ---
        selectedBeamType = json.has("beam_type") ? json.get("beam_type").getAsString() : "LASER";
        btnBeamType = addModalWidget(Button.builder(Component.literal(selectedBeamType), b -> {
            BeamType[] types = BeamType.values();
            int curIdx = 0;
            for (int i = 0; i < types.length; i++) {
                if (types[i].name().equalsIgnoreCase(selectedBeamType)) {
                    curIdx = i;
                    break;
                }
            }
            selectedBeamType = types[(curIdx + 1) % types.length].name();
            btnBeamType.setMessage(Component.literal(selectedBeamType));
        }).bounds(x + 50, y + 52, 110, 14).build());

        fldTexture = addModalWidget(new StudioSuggestTextField.UniversalSuggestField(screen, font, x + 215, y + 52, 160, 14, Component.literal("")));
        fldTexture.setContextNode(targetNode);
        fldTexture.setValue(json.has("texture") ? json.get("texture").getAsString() : "minecraft:textures/entity/beacon_beam.png");

        // Canvas Button
        addModalWidget(Button.builder(Component.literal("🎨 Canvas"), b -> {
            screen.serializeActiveTree();
            net.minecraft.client.Minecraft.getInstance().setScreen(new CanvasEditModal(screen, 16, fldTexture.getValue(), base64 -> {
                fldTexture.setValue(base64);
            }));
        }).bounds(x + 380, y + 52, 60, 14).build());

        // --- ZEILE 3: Geometrie & Dauer ---
        fldCoreWidth = addModalWidget(new StudioTextField(screen, font, x + 65, y + 74, 45, 14, Component.literal(""), Component.literal("Kernbreite")));
        fldCoreWidth.setValue(json.has("core_width") ? json.get("core_width").getAsString() : "0.2");

        fldCoronaWidth = addModalWidget(new StudioTextField(screen, font, x + 185, y + 74, 45, 14, Component.literal(""), Component.literal("Mantelbreite (Corona)")));
        fldCoronaWidth.setValue(json.has("corona_width") ? json.get("corona_width").getAsString() : "0.8");

        fldLifetime = addModalWidget(new StudioTextField(screen, font, x + 310, y + 74, 45, 14, Component.literal(""), Component.literal("Dauer in Ticks")));
        fldLifetime.setValue(json.has("lifetime") ? json.get("lifetime").getAsString() : "40");

        // --- ZEILE 4: Farbe & Transparenz ---
        fldHexColor = addModalWidget(new StudioTextField(screen, font, x + 65, y + 96, 70, 14, Component.literal(""), Component.literal("Hex Farbe")));
        fldHexColor.setValue(json.has("color") ? json.get("color").getAsString() : "#FFFFFF");

        fldAlpha = addModalWidget(new StudioTextField(screen, font, x + 185, y + 96, 45, 14, Component.literal(""), Component.literal("Alpha (0.0-1.0)")));
        fldAlpha.setValue(json.has("alpha") ? json.get("alpha").getAsString() : "1.0");

        // --- ZEILE 5: Textur-Scrolling & Repeat ---
        fldUvScroll = addModalWidget(new StudioTextField(screen, font, x + 85, y + 118, 50, 14, Component.literal(""), Component.literal("UV Scroll Speed")));
        fldUvScroll.setValue(json.has("uv_scroll_speed") ? json.get("uv_scroll_speed").getAsString() : "0.2");

        fldUvRepeat = addModalWidget(new StudioTextField(screen, font, x + 225, y + 118, 50, 14, Component.literal(""), Component.literal("UV Repeat")));
        fldUvRepeat.setValue(json.has("uv_repeat") ? json.get("uv_repeat").getAsString() : "1.0");

        // --- ZEILE 6: Helix Orbit Effekte ---
        fldHelixRadius = addModalWidget(new StudioTextField(screen, font, x + 85, y + 140, 45, 14, Component.literal(""), Component.literal("Helix Radius")));
        fldHelixRadius.setValue(json.has("helix_radius") ? json.get("helix_radius").getAsString() : "0.0");

        fldHelixFreq = addModalWidget(new StudioTextField(screen, font, x + 225, y + 140, 45, 14, Component.literal(""), Component.literal("Helix Frequenz")));
        fldHelixFreq.setValue(json.has("helix_frequency") ? json.get("helix_frequency").getAsString() : "0.0");

        fldHelixSpeed = addModalWidget(new StudioTextField(screen, font, x + 355, y + 140, 45, 14, Component.literal(""), Component.literal("Helix Speed")));
        fldHelixSpeed.setValue(json.has("helix_speed") ? json.get("helix_speed").getAsString() : "0.0");

        // --- OK / ABBRECHEN BUTTONS ---
        addModalWidget(Button.builder(Component.literal("Übernehmen"), b -> {
            saveToNode();
            screen.closeModal();
        }).bounds(x + (width / 2) - 105, y + height - 28, 100, 20).build());

        addModalWidget(Button.builder(Component.literal("Abbrechen"), b -> screen.closeModal())
                .bounds(x + (width / 2) + 5, y + height - 28, 100, 20).build());
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick, int x, int y) {
        // Labels
        g.drawString(font, "Start:", x + 15, y + 33, 0xFFAAAAAA);
        g.drawString(font, "Ende:", x + 230, y + 33, 0xFFAAAAAA);

        g.drawString(font, "Typ:", x + 15, y + 55, 0xFFAAAAAA);
        g.drawString(font, "Textur:", x + 170, y + 55, 0xFFAAAAAA);

        g.drawString(font, "Core-W:", x + 15, y + 77, 0xFFAAAAAA);
        g.drawString(font, "Corona-W:", x + 120, y + 77, 0xFFAAAAAA);
        g.drawString(font, "Lifetime:", x + 250, y + 77, 0xFFAAAAAA);

        g.drawString(font, "Farbe:", x + 15, y + 99, 0xFFAAAAAA);
        g.drawString(font, "Alpha:", x + 145, y + 99, 0xFFAAAAAA);

        // Color Swatch Preview
        int colorInt = parseColorWithAlpha(fldHexColor.getValue(), fldAlpha.getValue());
        g.fill(x + 245, y + 96, x + 275, y + 110, colorInt);
        g.renderOutline(x + 245, y + 96, 30, 14, 0xFFFFFFFF);

        g.drawString(font, "UV-Speed:", x + 15, y + 121, 0xFFAAAAAA);
        g.drawString(font, "UV-Repeat:", x + 150, y + 121, 0xFFAAAAAA);

        g.drawString(font, "Helix Rad:", x + 15, y + 143, 0xFFFFAA00);
        g.drawString(font, "Helix Freq:", x + 145, y + 143, 0xFFFFAA00);
        g.drawString(font, "Helix Speed:", x + 280, y + 143, 0xFFFFAA00);
    }

    private int parseColorWithAlpha(String hex, String alphaStr) {
        try {
            int rgb = Integer.parseInt(hex.replace("#", ""), 16);
            float a = Float.parseFloat(alphaStr);
            int alphaInt = (int) (Mth.clamp(a, 0.0f, 1.0f) * 255.0f);
            return (alphaInt << 24) | rgb;
        } catch (Exception e) {
            return 0xFFFFFFFF;
        }
    }

    private void saveToNode() {
        StudioContextMenu.saveState();
        JsonObject root = targetNode.jsonData;

        root.addProperty("type", "stones:spawn_beam");
        root.addProperty("start", fldStart.getValue().trim());
        root.addProperty("end", fldEnd.getValue().trim());
        root.addProperty("beam_type", selectedBeamType);
        root.addProperty("texture", fldTexture.getValue().trim());

        try { root.addProperty("core_width", Float.parseFloat(fldCoreWidth.getValue().trim())); } catch (Exception ignored) {}
        try { root.addProperty("corona_width", Float.parseFloat(fldCoronaWidth.getValue().trim())); } catch (Exception ignored) {}
        try { root.addProperty("lifetime", Integer.parseInt(fldLifetime.getValue().trim())); } catch (Exception ignored) {}

        root.addProperty("color", fldHexColor.getValue().trim());
        try { root.addProperty("alpha", Float.parseFloat(fldAlpha.getValue().trim())); } catch (Exception ignored) {}

        try { root.addProperty("uv_scroll_speed", Float.parseFloat(fldUvScroll.getValue().trim())); } catch (Exception ignored) {}
        try { root.addProperty("uv_repeat", Float.parseFloat(fldUvRepeat.getValue().trim())); } catch (Exception ignored) {}

        try { root.addProperty("helix_radius", Float.parseFloat(fldHelixRadius.getValue().trim())); } catch (Exception ignored) {}
        try { root.addProperty("helix_frequency", Float.parseFloat(fldHelixFreq.getValue().trim())); } catch (Exception ignored) {}
        try { root.addProperty("helix_speed", Float.parseFloat(fldHelixSpeed.getValue().trim())); } catch (Exception ignored) {}

        targetNode.readableText = StudioSerializer.getReadableText(root, targetNode.type);
    }

    @Override
    public void onCancel() {
        screen.closeModal();
    }
}