package net.stones.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class TemplateHashHelper {

    public static final String HASH_KEY = "_template_hash";

    public enum Status {
        UP_TO_DATE,           // Fall 1: Hashes stimmen überein
        SILENT_UPDATE,        // Fall 2: JAR neu, aber Spieler hat nie editiert -> Auto-Update!
        MODIFIED_CONFLICT     // Fall 3: JAR neu UND Spieler hat editiert -> Popup zeigen!
    }

    // KORREKTUR: jarJson hinzugefügt!
    public record CheckResult(Status status, JsonObject processedJson, JsonObject jarJson, String newJarHash) {}

    /**
     * ZENTRALE SERVER-PRÜFUNG:
     * Lädt das Template aus der Server-JAR und vergleicht es mit der bereitgestellten Spieler-Datei.
     */
    public static CheckResult verifyServerFile(String fileName, String fileContent) {
        try {
            JsonObject playerJson = JsonParser.parseString(fileContent).getAsJsonObject();
            String cleanName = fileName.replaceAll("\\.json$", "");
            
            // Helper lädt sich das Template selbst
            try (InputStream is = TemplateHashHelper.class.getResourceAsStream("/data/stones/enchantments/" + cleanName + ".json")) {
                if (is != null) {
                    try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        JsonObject jarJson = JsonParser.parseReader(reader).getAsJsonObject();
                        return checkAndMigrate(playerJson, jarJson);
                    }
                }
            }
        } catch (Exception ignored) {}
        
        // Wenn keine JAR-Vorlage existiert (z.B. Custom Rune) oder Fehler auftreten, ist alles "UP_TO_DATE"
        return new CheckResult(Status.UP_TO_DATE, null, null, ""); 
    }

    /**
     * Prüft eine geladene Spieler-JSON gegen die Vorlage aus der Mod-JAR.
     */
    public static CheckResult checkAndMigrate(JsonObject playerJson, JsonObject jarJson) {
        String newJarHash = calculateHash(jarJson);
        String storedHash = playerJson.has(HASH_KEY) ? playerJson.get(HASH_KEY).getAsString() : "";

        // Fall 1: Datei ist auf dem neuesten Stand
        if (storedHash.equals(newJarHash)) {
            return new CheckResult(Status.UP_TO_DATE, playerJson, jarJson, newJarHash);
        }

        String currentPlayerContentHash = calculateHash(playerJson);

        // Fall 2: Mod wurde geupdated, aber Spieler hat die Datei NIE angefasst
        if (currentPlayerContentHash.equals(storedHash) || (storedHash.isEmpty() && currentPlayerContentHash.equals(newJarHash))) {
            JsonObject updatedJson = jarJson.deepCopy();
            updatedJson.addProperty(HASH_KEY, newJarHash);
            return new CheckResult(Status.SILENT_UPDATE, updatedJson, jarJson, newJarHash);
        }

        // Fall 3: Mod wurde geupdated UND der Spieler hat eigene Änderungen vorgenommen
        return new CheckResult(Status.MODIFIED_CONFLICT, playerJson, jarJson, newJarHash);
    }

    /**
     * Stellt sicher, dass ein Template-Hash existiert, bevor die Datei gespeichert wird.
     * Nutzt die Original-JAR als Referenz, falls der Hash noch fehlt.
     */
    public static void ensureHashExists(JsonElement parsed, String fileName) {
        if (parsed != null && parsed.isJsonObject()) {
            JsonObject obj = parsed.getAsJsonObject();
            
            // Nur generieren, wenn noch kein Hash da ist und es kein Backup (.bak) ist
            if (!obj.has(HASH_KEY) && !fileName.endsWith(".bak")) {
                String cleanName = fileName.replaceAll("\\.json$", "");
                try (InputStream is = TemplateHashHelper.class.getResourceAsStream("/data/stones/enchantments/" + cleanName + ".json")) {
                    if (is != null) {
                        try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                            JsonObject jarJson = JsonParser.parseReader(reader).getAsJsonObject();
                            obj.addProperty(HASH_KEY, calculateHash(jarJson));
                        }
                    }
                } catch (Exception ignored) {
                    // Wenn es keine Vorlage gibt (z.B. Custom Rune), generieren wir den Hash aus sich selbst
                    obj.addProperty(HASH_KEY, calculateHash(obj));
                }
            }
        }
    }

    /**
     * Berechnet den SHA-256 Hash des JSON-Inhalts (ohne das _template_hash Feld).
     */
    public static String calculateHash(JsonObject json) {
        if (json == null) return "";
        JsonObject copy = json.deepCopy();
        copy.remove(HASH_KEY);
        return sha256(copy.toString());
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }
}