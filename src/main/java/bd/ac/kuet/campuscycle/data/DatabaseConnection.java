package bd.ac.kuet.campuscycle.data;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages live PostgreSQL database connectivity for the KUET CampusCycle platform
 * backed by Supabase with connection timeouts and availability health-checks.
 */
public final class DatabaseConnection {

    private static final Logger LOGGER = Logger.getLogger(DatabaseConnection.class.getName());

    private static final String DEFAULT_HOST = "aws-0-ap-northeast-1.pooler.supabase.com";
    private static final int DEFAULT_PORT = 5432;
    private static final String DEFAULT_DATABASE = "postgres";
    private static final String DEFAULT_USER = "postgres.wqybuukgcwhcwiwfffda";
    private static final String DEFAULT_PASS = "ghostrider_campus_cycle";

    private static final String JDBC_URL = String.format(
            "jdbc:postgresql://%s:%d/%s?sslmode=require&prepareThreshold=0&loginTimeout=4&connectTimeout=4&socketTimeout=6",
            DEFAULT_HOST, DEFAULT_PORT, DEFAULT_DATABASE
    );

    private static volatile Boolean available = null;

    private DatabaseConnection() {}

    public static Connection getConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", DEFAULT_USER);
        props.setProperty("password", DEFAULT_PASS);
        props.setProperty("ssl", "true");
        props.setProperty("sslmode", "require");
        props.setProperty("loginTimeout", "4");
        props.setProperty("connectTimeout", "4");
        props.setProperty("socketTimeout", "6");
        return DriverManager.getConnection(JDBC_URL, props);
    }

    public static boolean isAvailable() {
        if (available != null && !available) {
            return false;
        }
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(3);
            stmt.execute("SELECT 1");
            available = true;
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Live Supabase PostgreSQL is unreachable, falling back to local repository: " + e.getMessage());
            available = false;
            return false;
        }
    }

    public static void resetAvailability() {
        available = null;
    }
}
