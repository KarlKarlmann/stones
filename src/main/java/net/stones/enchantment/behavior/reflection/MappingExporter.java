package net.stones.enchantment.behavior.reflection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Utility für den Einmal-Gebrauch.
 * Erzeugt die "Ultimative Liste", die für jeden Member auch die deklarierende Klasse (Origin) speichert.
 */
public class MappingExporter {
    private static final Logger LOGGER = LogManager.getLogger();

    // Temporäre Struktur für den Import der einfachen Python-JSON
    private static class RawMapping {
        public Map<String, List<String>> methods = new HashMap<>();
        public Map<String, String> fields = new HashMap<>();
    }

    public static void exportUltimateMappings(InputStream inputSource, File outputFile) {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
            
            // 1. Rohe Pyscript-Mappings laden
            Map<String, RawMapping> raw = gson.fromJson(
                new InputStreamReader(inputSource, StandardCharsets.UTF_8),
                new TypeToken<Map<String, RawMapping>>(){}.getType()
            );

            Map<String, ReflectionInvoker.ClassMapping> ultimate = new HashMap<>();

            // 2. Für jede Klasse die Hierarchie auflösen und Herkunft markieren
            for (String className : raw.keySet()) {
                try {
                    Class<?> clazz = Class.forName(className);
                    ReflectionInvoker.ClassMapping ultimateMapping = new ReflectionInvoker.ClassMapping();
                    
                    // Wir wandern von der Klasse selbst hoch bis zu Object
                    Class<?> current = clazz;
                    while (current != null && current != Object.class) {
                        RawMapping parentRaw = raw.get(current.getName());
                        if (parentRaw != null) {
                            // Felder mergen und Herkunft setzen
                            for (Map.Entry<String, String> f : parentRaw.fields.entrySet()) {
                                if (!ultimateMapping.fields.containsKey(f.getKey())) {
                                    ReflectionInvoker.MemberData data = new ReflectionInvoker.MemberData();
                                    data.srg = f.getValue();
                                    data.origin = current.getName(); // Hier wurde es deklariert!
                                    ultimateMapping.fields.put(f.getKey(), data);
                                }
                            }
                            // Methoden mergen
                            for (Map.Entry<String, List<String>> m : parentRaw.methods.entrySet()) {
                                if (!ultimateMapping.methods.containsKey(m.getKey())) {
                                    List<ReflectionInvoker.MemberData> dataList = new ArrayList<>();
                                    for (String srg : m.getValue()) {
                                        ReflectionInvoker.MemberData data = new ReflectionInvoker.MemberData();
                                        data.srg = srg;
                                        data.origin = current.getName();
                                        dataList.add(data);
                                    }
                                    ultimateMapping.methods.put(m.getKey(), dataList);
                                }
                            }
                        }
                        current = current.getSuperclass();
                    }
                    ultimate.put(className, ultimateMapping);
                } catch (ClassNotFoundException e) {
                    // Fallback für nicht ladbare Klassen
                    ultimate.put(className, convertRawToUltimate(className, raw.get(className)));
                }
            }

            // 3. Export
            try (FileWriter writer = new FileWriter(outputFile)) {
                gson.toJson(ultimate, writer);
            }

            LOGGER.info("[Stones] ULTIMATIVE LISTE MIT HERKUNFTSDATEN EXPORTIERT: {}", outputFile.getAbsolutePath());

        } catch (Exception e) {
            LOGGER.error("[Stones] Export-Fehler!", e);
        }
    }

    private static ReflectionInvoker.ClassMapping convertRawToUltimate(String cls, RawMapping raw) {
        ReflectionInvoker.ClassMapping cm = new ReflectionInvoker.ClassMapping();
        raw.fields.forEach((k, v) -> {
            ReflectionInvoker.MemberData d = new ReflectionInvoker.MemberData();
            d.srg = v; d.origin = cls;
            cm.fields.put(k, d);
        });
        raw.methods.forEach((k, v) -> {
            List<ReflectionInvoker.MemberData> list = new ArrayList<>();
            for (String s : v) {
                ReflectionInvoker.MemberData d = new ReflectionInvoker.MemberData();
                d.srg = s; d.origin = cls;
                list.add(d);
            }
            cm.methods.put(k, list);
        });
        return cm;
    }
}