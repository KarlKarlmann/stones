package net.stones.enchantment.behavior.reflection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Die finale Reflection-Engine.
 * Unterstützt Hierarchie-Lookup, Typ-Konvertierung und Aliase.
 */
public class ReflectionInvoker {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ClassMapping> MAPPING_DATA = new HashMap<>();

    public static class MemberData {
        public String srg;
        public String origin; 
    }

    public static class ClassMapping {
        public Map<String, List<MemberData>> methods = new HashMap<>();
        public Map<String, MemberData> fields = new HashMap<>();
    }

    public static void init(InputStream mappingStream) {
        try {
            Gson gson = new Gson();
            Map<String, ClassMapping> data = gson.fromJson(
                new InputStreamReader(mappingStream, StandardCharsets.UTF_8), 
                new TypeToken<Map<String, ClassMapping>>(){}.getType()
            );
            MAPPING_DATA.clear();
            METHOD_CACHE.clear();
            FIELD_CACHE.clear();
            if (data != null) MAPPING_DATA.putAll(data);
            LOGGER.info("[Stones] Reflection Engine: {} Mappings geladen.", MAPPING_DATA.size());
        } catch (Exception e) {
            LOGGER.error("[Stones] Fehler beim Initialisieren der Mappings!", e);
        }
    }

    public static Object execute(ServerPlayer player, Map<String, Object> vars, ReflectionCallParser.ParsedCall call, List<Object> resolvedValues) {
        Object current = resolveRoot(player, vars, call.root);
        if (current == null) return null;
        try {
            for (String p : call.path) {
                current = resolveMember(current, p);
                if (current == null) return null;
            }
            return invokeTypedMethod(current, call, resolvedValues);
        } catch (Exception e) { return null; }
    }

    private static Object invokeTypedMethod(Object target, ReflectionCallParser.ParsedCall call, List<Object> resolvedValues) {
        final Class<?> targetClass = target.getClass();
        final Class<?>[] paramTypes = getParamTypes(call.args, resolvedValues);
        String cacheKey = targetClass.getName() + "." + call.method + serializeTypes(paramTypes);
        
        Method method = METHOD_CACHE.computeIfAbsent(cacheKey, k -> findMethodHierarchical(targetClass, call.method, paramTypes));

        if (method != null) {
            try {
                Object[] matched = match(resolvedValues, method.getParameterTypes());
                if (matched != null) return method.invoke(target, matched);
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static @Nullable Method findMethodHierarchical(Class<?> clazz, String name, Class<?>[] types) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            ClassMapping cm = MAPPING_DATA.get(current.getName());
            if (cm != null && cm.methods.containsKey(name)) {
                for (MemberData data : cm.methods.get(name)) {
                    try {
                        Class<?> originClass = Class.forName(data.origin);
                        try {
                            Method m = originClass.getDeclaredMethod(data.srg, types);
                            m.setAccessible(true);
                            return m;
                        } catch (NoSuchMethodException e) {
                            Method m = originClass.getDeclaredMethod(name, types);
                            m.setAccessible(true);
                            return m;
                        }
                    } catch (Exception ignored) {}
                }
            }
            try {
                Method m = current.getDeclaredMethod(name, types);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {}
            current = current.getSuperclass();
        }
        return null;
    }

    public static void setField(Object target, String fieldName, Object value) {
        try {
            Class<?> targetClass = target.getClass();
            String cacheKey = targetClass.getName() + "." + fieldName;
            Field f = FIELD_CACHE.computeIfAbsent(cacheKey, k -> findFieldHierarchical(targetClass, fieldName));
            if (f != null) {
                f.set(target, convertValue(value, f.getType()));
            }
        } catch (Exception ignored) {}
    }

    private static @Nullable Field findFieldHierarchical(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            ClassMapping cm = MAPPING_DATA.get(current.getName());
            if (cm != null && cm.fields.containsKey(name)) {
                MemberData data = cm.fields.get(name);
                try {
                    Class<?> originClass = Class.forName(data.origin);
                    try {
                        Field f = originClass.getDeclaredField(data.srg);
                        f.setAccessible(true);
                        return f;
                    } catch (NoSuchFieldException e) {
                        Field f = originClass.getDeclaredField(name);
                        f.setAccessible(true);
                        return f;
                    }
                } catch (Exception ignored) {}
            }
            try {
                Field f = current.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
            current = current.getSuperclass();
        }
        return null;
    }

    public static Object resolveMember(Object target, String name) {
        try {
            Field f = findFieldHierarchical(target.getClass(), name);
            if (f != null) return f.get(target);
            throw new NoSuchFieldException(name);
        } catch (Exception e) {
            return invokeTypedMethod(target, new ReflectionCallParser.ParsedCall("root", List.of(), name, List.of(), false), List.of());
        }
    }

    @Nullable
    public static Object instantiate(String className, List<ReflectionCallParser.Argument> args, List<Object> resolvedValues) {
        try {
            Class<?> clazz = Class.forName(className);
            Class<?>[] paramTypes = getParamTypes(args, resolvedValues);
            for (Constructor<?> c : clazz.getDeclaredConstructors()) {
                if (c.getParameterCount() == args.size() && isAssignable(paramTypes, c.getParameterTypes())) {
                    c.setAccessible(true);
                    return c.newInstance(match(resolvedValues, c.getParameterTypes()));
                }
            }
        } catch (Exception ignored) {}
        return null;
    }



    private static boolean isNumeric(Class<?> n) {
        return Number.class.isAssignableFrom(n) || n == double.class || n == float.class || n == int.class || n == long.class;
    }

    private static Object[] match(List<Object> values, Class<?>[] targets) {
        if (values.size() != targets.length) return null;
        Object[] result = new Object[targets.length];
        for (int i = 0; i < targets.length; i++) result[i] = convertValue(values.get(i), targets[i]);
        return result;
    }

    private static Class<?>[] getParamTypes(List<ReflectionCallParser.Argument> args, List<Object> values) {
        Class<?>[] types = new Class<?>[args.size()];
        for (int i = 0; i < args.size(); i++) {
            ReflectionCallParser.Argument arg = args.get(i);
            if (arg.explicitType != null) types[i] = resolveClass(arg.explicitType);
            else types[i] = (values.get(i) == null) ? Object.class : values.get(i).getClass();
        }
        return types;
    }

    public static Class<?> resolveClass(String name) {
        return switch (name.trim().toLowerCase()) {
            case "int" -> int.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "boolean" -> boolean.class;
            case "long" -> long.class;
            case "rl", "resourcelocation" -> ResourceLocation.class;
            case "vec3" -> Vec3.class;
            default -> {
                try { yield Class.forName(name.trim()); } 
                catch (ClassNotFoundException e) { yield Object.class; }
            }
        };
    }

    private static boolean isAssignable(Class<?>[] source, Class<?>[] target) {
        if (source.length != target.length) return false;
        for (int i = 0; i < source.length; i++) {
            if (target[i].isAssignableFrom(source[i])) continue;
            
            // FIX: Strings aus JSON args als kompatibel für Primitivtypen markieren
            if (isNumeric(target[i]) && (isNumeric(source[i]) || source[i] == String.class)) continue;
            if (target[i].isPrimitive() && (Number.class.isAssignableFrom(source[i]) || source[i] == String.class)) continue;
            if ((target[i] == boolean.class || target[i] == Boolean.class) && (source[i] == Boolean.class || source[i] == String.class)) continue;
            if (source[i] == String.class && (target[i] == ResourceLocation.class || target[i] == SoundEvent.class || target[i].isEnum())) continue;
            return false;
        }
        return true;
    }
	
    private static Object convertValue(Object value, Class<?> targetType) {
        if (value == null) return null;
        
        // FIX: Strings aus JSON args automatisch in Primitivtypen parsen
        if (value instanceof String s) {
            if (targetType == boolean.class || targetType == Boolean.class) return Boolean.parseBoolean(s);
            if (targetType == double.class || targetType == Double.class) return Double.parseDouble(s);
            if (targetType == float.class || targetType == Float.class) return Float.parseFloat(s);
            if (targetType == int.class || targetType == Integer.class) return (int)Double.parseDouble(s);
            if (targetType == long.class || targetType == Long.class) return (long)Double.parseDouble(s);
            
            if (targetType == ResourceLocation.class) return new ResourceLocation(s);
            if (targetType == SoundEvent.class) return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(s));
            if (targetType == SoundSource.class) {
                for (SoundSource src : SoundSource.values()) if (src.name().equalsIgnoreCase(s)) return src;
            }
        }
        
        if (value instanceof Number n) {
            if (targetType == float.class || targetType == Float.class) return n.floatValue();
            if (targetType == int.class || targetType == Integer.class) return n.intValue();
            if (targetType == double.class || targetType == Double.class) return n.doubleValue();
            if (targetType == long.class || targetType == Long.class) return n.longValue();
        }
        return value;
    }
	
    private static String serializeTypes(Class<?>[] types) {
        StringBuilder sb = new StringBuilder();
        for (Class<?> c : types) sb.append(":").append(c.getName());
        return sb.toString();
    }

    private static Object resolveRoot(ServerPlayer p, Map<String, Object> vars, String root) {
        if (root.equalsIgnoreCase("player")) return p;
        if (root.equalsIgnoreCase("level")) return p.level();
        String key = root.startsWith("$") ? root.substring(1) : root;
        return vars.get(key);
    }
}