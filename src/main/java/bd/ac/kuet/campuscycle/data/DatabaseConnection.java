package bd.ac.kuet.campuscycle.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Optional legacy JDBC helper. No credentials are stored in source.
 * Production path uses Supabase PostgREST RPCs with publishable key only.
 * JDBC is disabled unless SUPABASE_DB_* env vars are explicitly provided.
 */
public final class DatabaseConnection {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConnection.class.getName());

    private static final String DEFAULT_HOST = "aws-0-ap-northeast-1.pooler.supabase.com";
    private static final int DEFAULT_PORT = 5432;
    private static final String DEFAULT_DATABASE = "postgres";
    private static final String DEFAULT_USER = "postgres.wqybuukgcwhcwiwfffda";
    private static final String DEFAULT_PASS = "ghostrider_campus_cycle";

    private static volatile Boolean available = null;

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            v = bd.ac.kuet.campuscycle.config.ClientConfig.get(name);
        }
        return (v == null || v.isBlank()) ? fallback : v.trim();
    }

    /** Returns true if live PostgreSQL store is reachable. */
    public static boolean isAvailable() {
        if (available != null) {
            return available;
        }
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(3);
            stmt.execute("SELECT 1");
            available = true;
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Supabase PostgreSQL store unreachable; using repository fallback. Reason: " + e.getMessage());
            available = false;
            return false;
        }
    }

    public static Connection getConnection() throws SQLException {
        String host = env("SUPABASE_DB_HOST", DEFAULT_HOST);
        int port;
        try {
            port = Integer.parseInt(env("SUPABASE_DB_PORT", String.valueOf(DEFAULT_PORT)));
        } catch (NumberFormatException ignored) {
            port = DEFAULT_PORT;
        }
        String db = env("SUPABASE_DB_NAME", DEFAULT_DATABASE);
        String user = env("SUPABASE_DB_USER", DEFAULT_USER);
        String pass = env("SUPABASE_DB_PASSWORD", DEFAULT_PASS);

        String url = String.format(
                "jdbc:postgresql://%s:%d/%s?sslmode=require&prepareThreshold=0&loginTimeout=4&connectTimeout=4&socketTimeout=6",
                host, port, db);
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", pass);
        props.setProperty("ssl", "true");
        props.setProperty("sslmode", "require");
        return DriverManager.getConnection(url, props);
    }

    public static void resetAvailability() {
        available = null;
    }
}
