package net.stones.enchantment.behavior.reflection;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zerlegt einen String wie "player.setHealth((float)20.0)" in Bestandteile.
 * Unterstützt explizite Type-Casts und Variablen-Referenzen.
 */
public class ReflectionCallParser {

    private static final Pattern CAST_PATTERN = Pattern.compile("^\\(([^)]+)\\)(.+)$");

    public static class Argument {
        public final Object rawValue;
        public final String explicitType;

        public Argument(Object rawValue, String explicitType) {
            this.rawValue = rawValue;
            this.explicitType = explicitType;
        }
    }

    public static class ParsedCall {
        public final String root;
        public final List<String> path;
        public final String method;
        public final List<Argument> args;
        public final boolean isConstructor;

        public ParsedCall(String root, List<String> path, String method, List<Argument> args, boolean isConstructor) {
            this.root = root;
            this.path = path;
            this.method = method;
            this.args = args;
            this.isConstructor = isConstructor;
        }
    }

    public static ParsedCall parse(String callStr) {
        boolean isConstructor = callStr.startsWith("new ");
        String work = isConstructor ? callStr.substring(4).trim() : callStr;

        int firstParen = work.indexOf('(');
        String pathStr = firstParen == -1 ? work : work.substring(0, firstParen);
        String argsStr = firstParen == -1 ? "" : work.substring(firstParen + 1, work.lastIndexOf(')'));

        String[] pathParts = pathStr.split("\\.");
        String root = pathParts[0];
        List<String> path = new ArrayList<>();
        for (int i = 1; i < pathParts.length - 1; i++) path.add(pathParts[i]);
        String method = pathParts.length > 1 ? pathParts[pathParts.length - 1] : (isConstructor ? "<init>" : pathParts[0]);

        List<Argument> args = parseArguments(argsStr);
        return new ParsedCall(root, path, isConstructor ? "<init>" : method, args, isConstructor);
    }

    private static List<Argument> parseArguments(String argsStr) {
        List<Argument> result = new ArrayList<>();
        if (argsStr.trim().isEmpty()) return result;

        // Split an Kommas, ignoriert Kommas in Anführungszeichen
        String[] parts = argsStr.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String part : parts) {
            part = part.trim();
            
            // 1. Quotes entfernen BEVOR wir nach Casts suchen
            if (part.startsWith("\"") && part.endsWith("\"")) {
                part = part.substring(1, part.length() - 1);
            }

            String explicitType = null;
            Object val = null;

            // 2. Jetzt nach Casts suchen: (double)0.0
            Matcher matcher = CAST_PATTERN.matcher(part);
            if (matcher.matches()) {
                explicitType = matcher.group(1);
                part = matcher.group(2).trim();
            }

            // 3. Wert bestimmen
            if (part.equals("null")) val = null;
            else if (part.startsWith("$")) val = part; 
            else {
                try {
                    if (part.contains(".")) val = Double.parseDouble(part);
                    else val = Integer.parseInt(part);
                } catch (Exception e) { val = part; }
            }
            
            result.add(new Argument(val, explicitType));
        }
        return result;
    }
}