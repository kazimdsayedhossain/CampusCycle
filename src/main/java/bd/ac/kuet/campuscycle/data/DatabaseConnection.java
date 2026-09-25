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

    private DatabaseConnection() {}

    private static String env(String name) {
        String v = System.getenv(name);
        return v == null ? "" : v.trim();
    }

    /** JDBC is opt-in only; returns false unless SUPABASE_DB_HOST/USER/PASSWORD are set. */
    public static boolean isAvailable() {
        if (env("SUPABASE_DB_HOST").isBlank() || env("SUPABASE_DB_USER").isBlank() || env("SUPABASE_DB_PASSWORD").isBlank()) {
            return false;
        }
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(3);
            stmt.execute("SELECT 1");
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Optional JDBC store unreachable; using repository fallback. [DB_UNAVAILABLE]");
            return false;
        }
    }

    public static Connection getConnection() throws SQLException {
        String host = env("SUPABASE_DB_HOST");
        String user = env("SUPABASE_DB_USER");
        String pass = env("SUPABASE_DB_PASSWORD");
        if (host.isBlank() || user.isBlank() || pass.isBlank()) {
            throw new SQLException("JDBC store is not configured.");
        }
        String url = String.format(
                "jdbc:postgresql://%s:%d/%s?sslmode=require&prepareThreshold=0&loginTimeout=4&connectTimeout=4&socketTimeout=6",
                host, 5432, "postgres");
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", pass);
        props.setProperty("ssl", "true");
        props.setProperty("sslmode", "require");
        return DriverManager.getConnection(url, props);
    }

    public static void resetAvailability() {
    }
}
