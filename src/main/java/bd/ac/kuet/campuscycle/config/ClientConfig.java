package bd.ac.kuet.campuscycle.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Loads public desktop-client configuration without exposing a secret in source control. */
public final class ClientConfig {
    private static final Map<String, String> LOCAL_VALUES = loadLocalValues();

    private ClientConfig() { }

    public static String googleMapsApiKey() {
        return value("GOOGLE_MAPS_API_KEY");
    }

    public static String supabaseUrl() {
        return required("SUPABASE_URL");
    }

    public static String supabasePublishableKey() {
        return required("SUPABASE_PUBLISHABLE_KEY");
    }

    public static String freeRouteApiKey() {
        return required("FREEROUTE_API_KEY");
    }

    public static boolean isFreeRouteConfigured() {
        return !value("FREEROUTE_API_KEY").isBlank() && !value("FREEROUTE_API_KEY").contains("PLACEHOLDER");
    }

    public static boolean isSupabaseConfigured() {
        return !value("SUPABASE_URL").isBlank() && !value("SUPABASE_PUBLISHABLE_KEY").isBlank();
    }

    private static String value(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = LOCAL_VALUES.get(name);
        return value == null ? "" : value.trim();
    }

    private static String required(String name) {
        String configured = value(name);
        if (configured.isBlank()) throw new IllegalStateException("CampusCycle is missing required configuration.");
        return configured;
    }

    private static Map<String, String> loadLocalValues() {
        Map<String, String> values = new HashMap<>();
        Path file = Path.of(".env");
        if (!Files.isRegularFile(file)) return values;
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int separator = trimmed.indexOf('=');
                if (separator <= 0) continue;
                String key = trimmed.substring(0, separator).trim();
                if (key.equals("GOOGLE_MAPS_API_KEY") || key.equals("SUPABASE_URL") || key.equals("SUPABASE_PUBLISHABLE_KEY") || key.equals("FREEROUTE_API_KEY")) {
                    values.put(key, trimmed.substring(separator + 1).trim());
                }
            }
        } catch (IOException ignored) {
            // The app will display the map configuration state instead of a filesystem error.
        }
        return values;
    }
}
