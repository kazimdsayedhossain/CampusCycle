package bd.ac.kuet.campuscycle.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Loads public desktop-client configuration without exposing a secret in source control.
 *  Maps are Bing-only (Leaflet + Esri/Bing tiles); no Google Maps key is used.
 *
 *  <p>.env resolution precedence (P-158, highest first):
 *  <ol>
 *    <li>{@code $HOME/.campuscycle/.env} (per-user, stable regardless of launch directory)</li>
 *    <li>CWD {@code .env} (project-local fallback)</li>
 *  </ol>
 *  Environment variables always win over both files. Filesystem read failures are logged,
 *  never swallowed.
 */
public final class ClientConfig {
    private static final Logger LOGGER = Logger.getLogger(ClientConfig.class.getName());

    private static final Map<String, String> LOCAL_VALUES = loadLocalValues();

    private ClientConfig() { }

    public static String supabaseUrl() {
        String url = required("SUPABASE_URL");
        if (!url.startsWith("https://")) {
            throw new IllegalStateException("CampusCycle is misconfigured: SUPABASE_URL must start with https://");
        }
        return url;
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
        // required() would throw; here a boolean probe is intended, so validate without throwing.
        String url = value("SUPABASE_URL");
        if (url.isBlank() || !url.startsWith("https://")) {
            return false;
        }
        return !value("SUPABASE_PUBLISHABLE_KEY").isBlank();
    }

    private static String value(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) value = LOCAL_VALUES.get(name);
        return value == null ? "" : value.trim();
    }

    private static String required(String name) {
        String configured = value(name);
        if (configured.isBlank()) throw new IllegalStateException("CampusCycle is missing required configuration: " + name);
        return configured;
    }

    public static String get(String name) {
        return value(name);
    }

    private static Map<String, String> loadLocalValues() {
        Map<String, String> values = new HashMap<>();
        // Lower precedence first so the higher-precedence file overlays it.
        loadFile(Path.of(System.getProperty("user.dir", "."), ".env"), values);
        loadFile(Path.of(System.getProperty("user.home", "."), ".campuscycle", ".env"), values);
        return values;
    }

    private static void loadFile(Path file, Map<String, String> into) {
        if (!Files.isRegularFile(file)) return;
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int separator = trimmed.indexOf('=');
                if (separator <= 0) continue;
                String key = trimmed.substring(0, separator).trim();
                String val = trimmed.substring(separator + 1).trim();
                into.put(key, val);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to read configuration file " + file + ": " + e.getMessage(), e);
        } catch (SecurityException e) {
            LOGGER.log(Level.WARNING, "Denied access to configuration file " + file + ": " + e.getMessage(), e);
        }
    }
}
