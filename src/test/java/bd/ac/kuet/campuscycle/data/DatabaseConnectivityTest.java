package bd.ac.kuet.campuscycle.data;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatabaseConnectivityTest {

    @Test
    void testConnectionAndTables() {
        System.out.println("Checking DatabaseConnection availability...");
        boolean avail = DatabaseConnection.isAvailable();
        System.out.println("Database is available: " + avail);

        if (!avail) {
            System.out.println("Skipping live query test because database is not reachable from this network.");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            System.out.println("Connected to PostgreSQL successfully!");
            
            // Query tables
            try (ResultSet rs = stmt.executeQuery("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")) {
                System.out.println("Public tables in database:");
                while (rs.next()) {
                    System.out.println(" - " + rs.getString("table_name"));
                }
            }

            // Check rate_cards
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM public.rate_cards")) {
                if (rs.next()) {
                    System.out.println("rate_cards count: " + rs.getInt(1));
                }
            } catch (Exception e) {
                System.out.println("Error querying rate_cards: " + e.getMessage());
            }

            // Check profiles and auth users
            try (ResultSet rs = stmt.executeQuery("SELECT p.id, p.display_name, p.role, u.email FROM public.profiles p LEFT JOIN auth.users u ON u.id = p.id")) {
                System.out.println("Profiles + Auth sample:");
                while (rs.next()) {
                    System.out.println(" - " + rs.getString("id") + " : " + rs.getString("display_name") + " (" + rs.getString("role") + ") email=" + rs.getString("email"));
                }
            }

            // Check cycles
            try (ResultSet rs = stmt.executeQuery("SELECT id, label, pickup_point, review_status, availability_status FROM public.cycles LIMIT 10")) {
                System.out.println("Cycles sample:");
                while (rs.next()) {
                    System.out.println(" - " + rs.getString("id") + " : " + rs.getString("label") + " at " + rs.getString("pickup_point") + " [" + rs.getString("review_status") + " / " + rs.getString("availability_status") + "]");
                }
            }

        } catch (Exception e) {
            System.out.println("Connection exception: " + e.getMessage());
        }
    }
}
