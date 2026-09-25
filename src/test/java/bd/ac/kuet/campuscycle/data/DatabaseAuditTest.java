package bd.ac.kuet.campuscycle.data;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Deep audit of live Supabase database — tables, columns, constraints, functions, triggers, data.
 */
public class DatabaseAuditTest {

    @Test
    void fullDatabaseAudit() {
        if (!DatabaseConnection.isAvailable()) {
            System.out.println("SKIP: Database not reachable");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. All tables
            System.out.println("=== TABLES ===");
            try (ResultSet rs = stmt.executeQuery(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name")) {
                while (rs.next()) System.out.println("  " + rs.getString(1));
            }

            // 2. Columns for key tables
            String[] tables = {"profiles", "cycles", "rentals", "rate_cards", "payment_records", "disputes", "support_conversations", "support_messages", "audit_events"};
            for (String t : tables) {
                System.out.println("\n=== COLUMNS: " + t + " ===");
                try (ResultSet rs = stmt.executeQuery(
                    "SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns WHERE table_schema = 'public' AND table_name = '" + t + "' ORDER BY ordinal_position")) {
                    while (rs.next()) {
                        System.out.printf("  %-25s %-20s null=%-3s default=%s%n",
                            rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
                    }
                }
            }

            // 3. All functions
            System.out.println("\n=== FUNCTIONS ===");
            try (ResultSet rs = stmt.executeQuery(
                "SELECT routine_name FROM information_schema.routines WHERE routine_schema = 'public' AND routine_type = 'FUNCTION' ORDER BY routine_name")) {
                while (rs.next()) System.out.println("  " + rs.getString(1));
            }

            // 4. All triggers
            System.out.println("\n=== TRIGGERS ===");
            try (ResultSet rs = stmt.executeQuery(
                "SELECT trigger_name, event_object_table, action_timing, event_manipulation FROM information_schema.triggers WHERE trigger_schema = 'public' ORDER BY trigger_name")) {
                while (rs.next()) {
                    System.out.printf("  %-30s on %-20s %s %s%n",
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
                }
            }

            // 5. Foreign keys
            System.out.println("\n=== FOREIGN KEYS ===");
            try (ResultSet rs = stmt.executeQuery(
                "SELECT tc.constraint_name, tc.table_name, kcu.column_name, ccu.table_name AS fk_table, ccu.column_name AS fk_column " +
                "FROM information_schema.table_constraints tc " +
                "JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name " +
                "JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name " +
                "WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema = 'public' ORDER BY tc.table_name")) {
                while (rs.next()) {
                    System.out.printf("  %s.%s -> %s.%s%n",
                        rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5));
                }
            }

            // 6. Row counts for all tables
            System.out.println("\n=== ROW COUNTS ===");
            for (String t : tables) {
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM public." + t)) {
                    if (rs.next()) System.out.printf("  %-25s %d rows%n", t, rs.getInt(1));
                }
            }
            // Also check bookings table (spotted earlier in test output)
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM public.bookings")) {
                if (rs.next()) System.out.printf("  %-25s %d rows%n", "bookings", rs.getInt(1));
            } catch (Exception e) {
                System.out.println("  bookings table: " + e.getMessage());
            }

            // 7. Rate card data
            System.out.println("\n=== RATE CARDS ===");
            try (ResultSet rs = stmt.executeQuery("SELECT version, base_minutes, base_charge_poisha, extra_block_minutes, extra_block_charge_poisha, maximum_minutes FROM public.rate_cards")) {
                while (rs.next()) {
                    System.out.printf("  v%d: base=%dm/%dp, extra=%dm/%dp, max=%dm%n",
                        rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6));
                }
            }

            // 8. Cycle data detail
            System.out.println("\n=== CYCLES (DETAIL) ===");
            try (ResultSet rs = stmt.executeQuery("SELECT id, label, cycle_type, physical_condition, pickup_point, latitude, longitude, review_status, availability_status FROM public.cycles")) {
                while (rs.next()) {
                    System.out.printf("  %s | %s | type=%s | cond=%s | at=%s | lat=%.4f lng=%.4f | review=%s | avail=%s%n",
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getDouble(6), rs.getDouble(7), rs.getString(8), rs.getString(9));
                }
            }

            // 9. Profile data
            System.out.println("\n=== PROFILES ===");
            try (ResultSet rs = stmt.executeQuery("SELECT id, display_name, role FROM public.profiles")) {
                while (rs.next()) {
                    System.out.printf("  %s | %s | %s%n", rs.getString(1), rs.getString(2), rs.getString(3));
                }
            }

            // 10. Indexes
            System.out.println("\n=== INDEXES ===");
            try (ResultSet rs = stmt.executeQuery(
                "SELECT indexname, tablename FROM pg_indexes WHERE schemaname = 'public' ORDER BY tablename, indexname")) {
                while (rs.next()) {
                    System.out.printf("  %-50s on %s%n", rs.getString(1), rs.getString(2));
                }
            }

            // 11. Check constraints
            System.out.println("\n=== CHECK CONSTRAINTS ===");
            try (ResultSet rs = stmt.executeQuery(
                "SELECT tc.table_name, tc.constraint_name, cc.check_clause FROM information_schema.table_constraints tc " +
                "JOIN information_schema.check_constraints cc ON tc.constraint_name = cc.constraint_name " +
                "WHERE tc.table_schema = 'public' AND tc.constraint_type = 'CHECK' ORDER BY tc.table_name LIMIT 30")) {
                while (rs.next()) {
                    System.out.printf("  %s: %s = %s%n", rs.getString(1), rs.getString(2), rs.getString(3));
                }
            }

        } catch (Exception e) {
            System.out.println("AUDIT ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
