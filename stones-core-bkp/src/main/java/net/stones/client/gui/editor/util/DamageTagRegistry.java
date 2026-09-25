package net.stones.client.gui.editor.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class DamageTagRegistry {

    /**
     * Liest dynamisch alle verfuegbaren DamageType-Tags aus der aktuellen Welt (inkl. Mods)
     * oder faellt auf die Vanilla-Tags per Reflection zurück, falls keine Welt geladen ist.
     */
    public static List<String> getAvailableDamageTags() {
        TreeSet<String> tags = new TreeSet<>();

        // 1. DYNAMISCH: Aus der geladenen Welt/Registry lesen (erfasst alle Mod-Tags)
        try {
            if (Minecraft.getInstance().level != null) {
                Registry<DamageType> damageTypeRegistry = Minecraft.getInstance().level.registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE);

                damageTypeRegistry.getTagNames().forEach(tagKey -> {
                    tags.add(tagKey.location().toString());
                });
            }
        } catch (Exception ignored) {
            // Fallback greift, falls Registry in diesem Kontext nicht verfuegbar ist
        }

        // 2. FALLBACK: Vanilla-Tags per Reflection aus DamageTypeTags laden
        if (tags.isEmpty()) {
            for (Field field : DamageTypeTags.class.getDeclaredFields()) {
                if (TagKey.class.isAssignableFrom(field.getType())) {
                    try {
                        @SuppressWarnings("unchecked")
                        TagKey<DamageType> tag = (TagKey<DamageType>) field.get(null);
                        if (tag != null) {
                            tags.add(tag.location().toString());
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        return new ArrayList<>(tags);
    }
}