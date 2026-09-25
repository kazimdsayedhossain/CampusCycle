package bd.ac.kuet.campuscycle.data;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

public class DatabaseMigrationRunnerTest {

    @Test
    void applyDatabaseUpgradesAndSeed() {
        if (!DatabaseConnection.isAvailable()) {
            System.out.println("SKIP: Database is not available");
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            System.out.println("1. Adding missing columns to public.cycles...");
            stmt.execute("ALTER TABLE public.cycles ADD COLUMN IF NOT EXISTS latitude double precision;");
            stmt.execute("ALTER TABLE public.cycles ADD COLUMN IF NOT EXISTS longitude double precision;");
            System.out.println("   latitude and longitude columns verified in public.cycles.");

            System.out.println("2. Creating updated_at trigger function...");
            stmt.execute("""
                CREATE OR REPLACE FUNCTION public.update_updated_at_column()
                RETURNS TRIGGER AS $$
                BEGIN
                    NEW.updated_at = now();
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql;
            """);

            String[] triggerTables = {"profiles", "cycles", "rentals", "disputes", "support_conversations", "payment_records"};
            for (String tbl : triggerTables) {
                stmt.execute("DROP TRIGGER IF EXISTS trg_update_timestamp ON public." + tbl + ";");
                stmt.execute("CREATE TRIGGER trg_update_timestamp BEFORE UPDATE ON public." + tbl + " FOR EACH ROW EXECUTE PROCEDURE public.update_updated_at_column();");
            }
            System.out.println("   Automatic updated_at triggers created on all 6 tables.");

            System.out.println("3. Checking rate_cards version 1...");
            try (ResultSet rs = stmt.executeQuery("SELECT version FROM public.rate_cards WHERE version = 1")) {
                if (!rs.next()) {
                    stmt.execute("""
                        INSERT INTO public.rate_cards (version, currency, minimum_minutes, interval_minutes, base_amount_poisha, interval_amount_poisha, active_from)
                        VALUES (1, 'B', 15, 15, 2000, 1000, now())
                    """);
                    System.out.println("   Inserted default rate_card v1.");
                } else {
                    System.out.println("   Rate card v1 exists.");
                }
            }

            System.out.println("4. Ensuring test profiles exist for Demo Student and Demo Admin...");
            // Student ID: 3d1e3d69-ffc6-494f-a42c-26eeb258b581
            // Admin ID: 56d6f9dc-0ca7-4b49-9f9e-3c48a1b2089a
            stmt.execute("""
                INSERT INTO public.profiles (id, display_name, full_name, role)
                VALUES ('3d1e3d69-ffc6-494f-a42c-26eeb258b581', 'Arafat Rahman', 'Arafat Rahman (KUET CSE)', 'STUDENT')
                ON CONFLICT (id) DO UPDATE SET display_name = 'Arafat Rahman', full_name = 'Arafat Rahman (KUET CSE)', role = 'STUDENT';
            """);

            stmt.execute("""
                INSERT INTO public.profiles (id, display_name, full_name, role)
                VALUES ('56d6f9dc-0ca7-4b49-9f9e-3c48a1b2089a', 'KUET Cycle Office', 'KUET Smart Mobility Administrator', 'ADMIN')
                ON CONFLICT (id) DO UPDATE SET display_name = 'KUET Cycle Office', full_name = 'KUET Smart Mobility Administrator', role = 'ADMIN';
            """);
            System.out.println("   Demo student and admin profiles verified.");

            System.out.println("5. Seeding production-ready cycles across KUET campus...");
            // Array of 6 campus cycles
            String[][] seedCycles = {
                {"CC-LIB-01", "Blue Commuter 01", "Nusrat Jahan", "01711223344", "CITY_BIKE", "EXCELLENT", "KUET Central Library", "22.9009", "89.5016", "Reliable campus commuter with front cargo basket and bell.", "APPROVED", "AVAILABLE"},
                {"CC-SWC-02", "Road Runner Alloy", "Rakib Hasan", "01811223344", "ROAD_BIKE", "GOOD", "Student Welfare Centre", "22.9017", "89.5030", "Lightweight 21-speed alloy road bike, perfect for fast transit.", "APPROVED", "AVAILABLE"},
                {"CC-GATE-03", "Eco Assisted E-Bike", "Sadia Islam", "01911223344", "ELECTRIC_BIKE", "EXCELLENT", "KUET Main Gate", "22.8987", "89.4981", "Pedal-assisted smart electric cycle with quick battery dock.", "APPROVED", "AVAILABLE"},
                {"CC-HALL-04", "Campus Glide Commuter", "Tanvir Ahmed", "01611223344", "CITY_BIKE", "EXCELLENT", "Hall Gate", "22.9045", "89.5060", "Comfortable step-through commuter frame for residential hall riders.", "APPROVED", "AVAILABLE"},
                {"CC-ACAD-05", "Solar Cruiser E-02", "Mehedi Hasan", "01511223344", "ELECTRIC_BIKE", "GOOD", "Academic Building", "22.9015", "89.5010", "Smart throttle e-bike equipped with GPS tracker and solar charge lock.", "APPROVED", "AVAILABLE"},
                {"CC-PEND-06", "Student Peer Bike", "3d1e3d69-ffc6-494f-a42c-26eeb258b581", "01799887766", "CITY_BIKE", "GOOD", "Hall Gate", "22.9045", "89.5060", "Student P2P cycle listing currently queued for Cycle Office review.", "PENDING_REVIEW", "AVAILABLE"}
            };

            String insertCycleSql = """
                INSERT INTO public.cycles
                (id, cycle_id, label, owner_name, owner_phone, cycle_type, physical_condition, pickup_point, latitude, longitude, description, review_status, availability_status, is_available, is_verified)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, true)
                ON CONFLICT (cycle_id) DO UPDATE SET
                    label = EXCLUDED.label,
                    pickup_point = EXCLUDED.pickup_point,
                    latitude = EXCLUDED.latitude,
                    longitude = EXCLUDED.longitude,
                    review_status = EXCLUDED.review_status,
                    availability_status = EXCLUDED.availability_status;
            """;

            try (PreparedStatement pstmt = conn.prepareStatement(insertCycleSql)) {
                for (String[] c : seedCycles) {
                    pstmt.setObject(1, UUID.nameUUIDFromBytes(c[0].getBytes()));
                    pstmt.setString(2, c[0]); // cycle_id
                    pstmt.setString(3, c[1]); // label
                    pstmt.setString(4, c[2]); // owner_name
                    pstmt.setString(5, c[3]); // owner_phone
                    pstmt.setString(6, c[4]); // cycle_type
                    pstmt.setString(7, c[5]); // physical_condition
                    pstmt.setString(8, c[6]); // pickup_point
                    pstmt.setDouble(9, Double.parseDouble(c[7])); // lat
                    pstmt.setDouble(10, Double.parseDouble(c[8])); // lng
                    pstmt.setString(11, c[9]); // description
                    pstmt.setString(12, c[10]); // review_status
                    pstmt.setString(13, c[11]); // availability_status
                    pstmt.executeUpdate();
                }
            }
            System.out.println("   6 production-ready cycles seeded and updated successfully.");

            // 6. Verify total available cycles
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM public.cycles WHERE review_status = 'APPROVED' AND availability_status = 'AVAILABLE'")) {
                if (rs.next()) {
                    System.out.println("   Live approved & available cycles now in DB: " + rs.getInt(1));
                }
            }

        } catch (Exception e) {
            System.err.println("Migration runner error: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
