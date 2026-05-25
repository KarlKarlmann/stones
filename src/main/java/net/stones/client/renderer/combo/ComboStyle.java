package net.stones.client.renderer.combo;

import net.minecraft.resources.ResourceLocation;

public record ComboStyle(
    ResourceLocation texture, 
    float size, 
    float radius, 
    float speed, 
    float heightOffset,
    float r, float g, float b, float a
) {
    // Ein paar vorgefertigte Styles als Konstanten (Beispiele)
    public static final ComboStyle GLADIATOR_WRATH = new ComboStyle(
        new ResourceLocation("stones", "textures/gui/combo_fire.png"),
        0.4f,  // Größe
        1.2f,  // Radius der Umlaufbahn
        0.15f, // Rotationsgeschwindigkeit
        1.0f,  // Höhe (auf Bauch/Brust-Höhe)
        1.0f, 0.4f, 0.1f, 0.9f // RGBA (Leuchtendes Orange)
    );

    public static final ComboStyle VOID_SOULS = new ComboStyle(
        new ResourceLocation("stones", "textures/gui/combo_soul.png"),
        0.3f, 1.5f, 0.08f, 1.2f,
        0.2f, 0.0f, 0.8f, 0.8f // RGBA (Dunkles Lila)
    );
}