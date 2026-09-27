package net.stones.editor.client.gui.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class DamageTypeRegistry {

    public static List<String> getAvailableDamageTypes() {
        TreeSet<String> types = new TreeSet<>();

        // 1. DYNAMISCH: Aus der geladenen Welt lesen (erfasst alle Mod-DamageTypes!)
        try {
            if (Minecraft.getInstance().level != null) {
                Registry<DamageType> damageTypeRegistry = Minecraft.getInstance().level.registryAccess()
                        .registryOrThrow(Registries.DAMAGE_TYPE);

                damageTypeRegistry.keySet().forEach(key -> {
                    types.add(key.toString());
                });
            }
        } catch (Exception ignored) {}

        // 2. FALLBACK: Vanilla-Types per Reflection, falls Editor im Hauptmenü offen ist
        if (types.isEmpty()) {
            for (Field field : DamageTypes.class.getDeclaredFields()) {
                if (ResourceKey.class.isAssignableFrom(field.getType())) {
                    try {
                        @SuppressWarnings("unchecked")
                        ResourceKey<DamageType> key = (ResourceKey<DamageType>) field.get(null);
                        if (key != null) {
                            types.add(key.location().toString());
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        return new ArrayList<>(types);
    }
}