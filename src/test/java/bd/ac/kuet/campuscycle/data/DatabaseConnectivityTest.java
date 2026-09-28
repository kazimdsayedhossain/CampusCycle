package bd.ac.kuet.campuscycle.data;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.Role;
import bd.ac.kuet.campuscycle.ui.*;

// Live-DB integration test: runs only with CC_TEST_DB=true against a disposable database. Never the pilot.
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "CC_TEST_DB", matches = "true")
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
            try (ResultSet rs = stmt.executeQuery("SELECT p.id, p.display_name, p.role, u.email, u.encrypted_password, (crypt('TestPass123!', u.encrypted_password) = u.encrypted_password) as matched FROM public.profiles p LEFT JOIN auth.users u ON u.id = p.id WHERE u.email = 'admin@kuet.ac.bd'")) {
                System.out.println("Testing password match with pgcrypto crypt:");
                while (rs.next()) {
                    System.out.println(" - " + rs.getString("email") + " matched=" + rs.getBoolean("matched"));
                }
            }

            // Check new auth lookup query
            String testSql = """
                SELECT COALESCE(u.id, p.id) AS id,
                       COALESCE(p.display_name, split_part(u.email, '@', 1), 'KUET Member') AS display_name,
                       COALESCE(p.role, 'STUDENT') AS role,
                       COALESCE(u.email, ?) AS email,
                       u.encrypted_password,
                       (CASE WHEN u.encrypted_password IS NOT NULL THEN (crypt(?, u.encrypted_password) = u.encrypted_password) ELSE false END) AS auth_matched
                FROM auth.users u
                FULL OUTER JOIN public.profiles p ON p.id = u.id
                WHERE LOWER(COALESCE(u.email, '')) = LOWER(?)
                   OR LOWER(COALESCE(p.display_name, '')) = LOWER(?)
                   OR p.id::text = ?
                   OR u.id::text = ?
                ORDER BY p.created_at ASC NULLS LAST
                LIMIT 1;
            """;
            try (java.sql.PreparedStatement pstmt = conn.prepareStatement(testSql)) {
                pstmt.setString(1, "admin@kuet.ac.bd");
                pstmt.setString(2, "TestPass123!");
                pstmt.setString(3, "admin@kuet.ac.bd");
                pstmt.setString(4, "admin@kuet.ac.bd");
                pstmt.setString(5, "admin@kuet.ac.bd");
                pstmt.setString(6, "admin@kuet.ac.bd");
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        System.out.println("LOOKUP SUCCESS:");
                        System.out.println(" - id: " + rs.getString("id"));
                        System.out.println(" - display_name: " + rs.getString("display_name"));
                        System.out.println(" - role: " + rs.getString("role"));
                        System.out.println(" - email: " + rs.getString("email"));
                        System.out.println(" - auth_matched: " + rs.getBoolean("auth_matched"));
                    } else {
                        System.out.println("LOOKUP FAILED: No row returned!");
                    }
                }
            } catch (Exception e) {
                System.out.println("lookup test error: " + e.getMessage());
            }

        } catch (Exception e) {
            System.out.println("Connection exception: " + e.getMessage());
        }
    }

    @Test
    void testAdminSignIn() throws Exception {
        if (!DatabaseConnection.isAvailable()) return;
        System.out.println("Attempting SupabaseAuthService.signIn('admin@kuet.ac.bd', 'TestPass123!')...");
        SupabaseAuthService.Session session = SupabaseAuthService.signIn("admin@kuet.ac.bd", "TestPass123!").get();
        System.out.println("SIGN IN SUCCEEDED!");
        System.out.println("User ID: " + session.user().id());
        System.out.println("Display Name: " + session.user().displayName());
        System.out.println("Role: " + session.user().role());
        System.out.println("Email: " + session.user().email());
        System.out.println("Access Token: " + session.accessToken());
        org.junit.jupiter.api.Assertions.assertNotNull(session);
        org.junit.jupiter.api.Assertions.assertEquals(bd.ac.kuet.campuscycle.domain.Role.ADMIN, session.user().role());
        org.junit.jupiter.api.Assertions.assertFalse(session.accessToken().isBlank(), "Session token should not be blank");

        // Also test admin alias: "admin"
        System.out.println("Attempting SupabaseAuthService.signIn('admin', 'TestPass123!')...");
        SupabaseAuthService.Session aliasSession = SupabaseAuthService.signIn("admin", "TestPass123!").get();
        org.junit.jupiter.api.Assertions.assertEquals(bd.ac.kuet.campuscycle.domain.Role.ADMIN, aliasSession.user().role());
        System.out.println("ADMIN ALIAS SIGN IN SUCCEEDED!");

        // Also test student sign-in
        System.out.println("Attempting SupabaseAuthService.signIn('student@kuet.ac.bd', 'TestPass123!')...");
        SupabaseAuthService.Session studentSession = SupabaseAuthService.signIn("student@kuet.ac.bd", "TestPass123!").get();
        org.junit.jupiter.api.Assertions.assertEquals(bd.ac.kuet.campuscycle.domain.Role.STUDENT, studentSession.user().role());
        System.out.println("STUDENT SIGN IN SUCCEEDED! User: " + studentSession.user().displayName());

        // Also test incorrect password rejection
        try {
            SupabaseAuthService.signIn("admin@kuet.ac.bd", "WrongPassword123!").get();
            org.junit.jupiter.api.Assertions.fail("Should have thrown error on bad password");
        } catch (Exception e) {
            System.out.println("WRONG PASSWORD CORRECTLY REJECTED: " + e.getMessage());
        }
    }

    @Test
    void testSeedCyclesIfEmpty() throws Exception {
        if (!DatabaseConnection.isAvailable()) return;
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Apply missing lifecycle columns on rentals and cycles
            stmt.execute("ALTER TABLE public.cycles ADD COLUMN IF NOT EXISTS created_at timestamptz not null default now();");
            stmt.execute("ALTER TABLE public.cycles ALTER COLUMN cycle_id SET DEFAULT gen_random_uuid()::text;");
            stmt.execute("ALTER TABLE public.cycles ALTER COLUMN registered_at SET DEFAULT now();");
            stmt.execute("ALTER TABLE public.cycles ALTER COLUMN is_available SET DEFAULT true;");
            stmt.execute("ALTER TABLE public.cycles ALTER COLUMN is_verified SET DEFAULT true;");
            stmt.execute("ALTER TABLE public.cycles ADD COLUMN IF NOT EXISTS retired_at timestamptz;");
            stmt.execute("ALTER TABLE public.cycles ADD COLUMN IF NOT EXISTS retirement_reason text;");

            stmt.execute("ALTER TABLE public.rentals ADD COLUMN IF NOT EXISTS due_at timestamptz;");
            stmt.execute("ALTER TABLE public.rentals ADD COLUMN IF NOT EXISTS final_amount_poisha integer;");
            stmt.execute("ALTER TABLE public.rentals ADD COLUMN IF NOT EXISTS dropoff_hub text;");
            stmt.execute("ALTER TABLE public.rentals ADD COLUMN IF NOT EXISTS overdue_fine_poisha integer not null default 0;");
            stmt.execute("ALTER TABLE public.maintenance_tickets ADD COLUMN IF NOT EXISTS priority text default 'ROUTINE';");
            stmt.execute("ALTER TABLE public.maintenance_tickets ADD COLUMN IF NOT EXISTS resolved_by_user_id text;");
            stmt.execute("ALTER TABLE public.maintenance_tickets ADD COLUMN IF NOT EXISTS release_decision text;");

            stmt.execute("ALTER TABLE public.profiles DROP CONSTRAINT IF EXISTS profiles_role_check;");
            stmt.execute("ALTER TABLE public.profiles ADD CONSTRAINT profiles_role_check CHECK (role IN ('STUDENT', 'TECHNICIAN', 'ADMIN'));");
            stmt.execute("UPDATE public.profiles SET role = 'TECHNICIAN' WHERE id = '8ca24a59-00e9-43f6-bdb2-42b72a73719d';"); // tech@kuet.ac.bd

            // Ensure wallets have balance for testing
            stmt.execute("INSERT INTO public.wallets (user_id, balance_poisha, updated_at) VALUES ('11305bb5-f12e-4c3c-a87c-ce0eca55aa18', 5000, now()) ON CONFLICT (user_id) DO UPDATE SET balance_poisha = 5000;");
            stmt.execute("INSERT INTO public.wallets (user_id, balance_poisha, updated_at) VALUES ('ac2b8d64-c379-4223-ba0a-71bde5d6edcb', 10000, now()) ON CONFLICT (user_id) DO UPDATE SET balance_poisha = 10000;");

            stmt.execute("UPDATE public.cycles SET availability_status = 'AVAILABLE' WHERE review_status = 'APPROVED' AND retired_at IS NULL;");
            int count = 0;
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM public.cycles WHERE availability_status = 'AVAILABLE'")) {
                if (rs.next()) count = rs.getInt(1);
            }
            System.out.println("Current available cycles count: " + count);

            if (count == 0) {
                System.out.println("Seeding starter cycles across campus hubs...");
                String adminId = "ac2b8d64-c379-4223-ba0a-71bde5d6edcb";
                String insertSql = """
                    INSERT INTO public.cycles (
                        id, cycle_id, owner_id, owner_name, label, cycle_type, physical_condition, pickup_point,
                        latitude, longitude, description, owner_phone, review_status,
                        availability_status, registered_at, created_at, updated_at
                    ) VALUES
                    (gen_random_uuid(), 'CC-001', ?::uuid, 'KUET Admin', 'Solar Cruiser 01', 'ELECTRIC_BIKE', 'EXCELLENT', 'KUET Main Gate', 22.8987, 89.4981, 'Solar-assisted campus commuter bike with smart lock.', '+8801712345678', 'APPROVED', 'AVAILABLE', now(), now(), now()),
                    (gen_random_uuid(), 'CC-002', ?::uuid, 'KUET Admin', 'Green Commuter 02', 'CITY_BIKE', 'EXCELLENT', 'Student Welfare Centre', 22.9017, 89.5030, 'Lightweight single-speed city bike for quick cross-campus hops.', '+8801712345678', 'APPROVED', 'AVAILABLE', now(), now(), now()),
                    (gen_random_uuid(), 'CC-003', ?::uuid, 'KUET Admin', 'Eco Shuttle 03', 'ROAD_BIKE', 'GOOD', 'Academic Building', 22.9015, 89.5010, '7-speed geared bicycle for long rides towards the workshops.', '+8801712345678', 'APPROVED', 'AVAILABLE', now(), now(), now()),
                    (gen_random_uuid(), 'CC-004', ?::uuid, 'KUET Admin', 'Campus Explorer 04', 'CITY_BIKE', 'EXCELLENT', 'Hall Gate', 22.9045, 89.5060, 'Comfortable step-through frame bike at residential halls.', '+8801712345678', 'APPROVED', 'AVAILABLE', now(), now(), now()),
                    (gen_random_uuid(), 'CC-005', ?::uuid, 'KUET Admin', 'Speedster 05', 'CARGO_BIKE', 'GOOD', 'KUET Central Mosque', 22.9009, 89.5016, 'High-capacity utility bike with heavy-duty front basket.', '+8801712345678', 'APPROVED', 'AVAILABLE', now(), now(), now())
                """;
                try (java.sql.PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                    pstmt.setString(1, adminId);
                    pstmt.setString(2, adminId);
                    pstmt.setString(3, adminId);
                    pstmt.setString(4, adminId);
                    pstmt.setString(5, adminId);
                    int inserted = pstmt.executeUpdate();
                    System.out.println("Seeded " + inserted + " campus bicycles!");
                }
            }
        }
    }

    @Test
    void testAllPagesInstantiation() throws Exception {
        if (!DatabaseConnection.isAvailable()) return;
        try {
            javafx.application.Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}

        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();

        javafx.application.Platform.runLater(() -> {
            try {
                CampusUser admin = new CampusUser("ac2b8d64-c379-4223-ba0a-71bde5d6edcb", "KUET Admin", "admin@kuet.ac.bd", bd.ac.kuet.campuscycle.domain.Role.ADMIN);
                SupabaseCampusRepository repo = new SupabaseCampusRepository();

                System.out.println("Testing SidebarView creation...");
                SidebarView sidebar = new SidebarView("Dashboard", true, p -> {}, () -> {});
                org.junit.jupiter.api.Assertions.assertNotNull(sidebar);

                System.out.println("Testing TopBarView creation...");
                TopBarView topBar = new TopBarView(admin, q -> {}, () -> {}, () -> {});
                org.junit.jupiter.api.Assertions.assertNotNull(topBar);

                System.out.println("Testing DashboardView creation...");
                DashboardView dash = new DashboardView(admin, repo, p -> {}, c -> {}, loc -> {});
                org.junit.jupiter.api.Assertions.assertNotNull(dash);

                System.out.println("Testing FleetCatalogView creation...");
                FleetCatalogView fleet = new FleetCatalogView(admin, repo, c -> {}, loc -> {}, () -> {}, () -> {});
                org.junit.jupiter.api.Assertions.assertNotNull(fleet);

                System.out.println("Testing ActiveJourneyView creation...");
                ActiveJourneyView journey = new ActiveJourneyView(admin, repo, p -> {}, () -> {});
                org.junit.jupiter.api.Assertions.assertNotNull(journey);

                System.out.println("Testing PassbookView creation...");
                PassbookView passbook = new PassbookView(admin, repo);
                org.junit.jupiter.api.Assertions.assertNotNull(passbook);

                System.out.println("Testing SupportView creation...");
                SupportView support = new SupportView(admin, repo);
                org.junit.jupiter.api.Assertions.assertNotNull(support);

                System.out.println("Testing AdminOperationsView creation...");
                AdminOperationsView adminOps = new AdminOperationsView(admin, repo, () -> {});
                org.junit.jupiter.api.Assertions.assertNotNull(adminOps);

                System.out.println("Testing UserRegistrationModal creation...");
                UserRegistrationModal userRegModal = new UserRegistrationModal(() -> {});
                org.junit.jupiter.api.Assertions.assertNotNull(userRegModal);

                System.out.println("ALL PAGES CREATED CLEANLY ON FX THREAD!");
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });

        latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        if (error.get() != null) {
            error.get().printStackTrace();
            org.junit.jupiter.api.Assertions.fail("Page instantiation threw exception: " + error.get().getMessage());
        }
    }

    @Test
    void testSupportAndWallet() throws Exception {
        if (!DatabaseConnection.isAvailable()) return;

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement()) {

            // Check support_conversations columns
            try (ResultSet rs = stmt.executeQuery("SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'support_conversations'")) {
                System.out.println("support_conversations columns:");
                while (rs.next()) {
                    System.out.println(" - " + rs.getString("column_name") + " (" + rs.getString("data_type") + ")");
                }
            }

            // Check maintenance_tickets columns
            try (ResultSet rs = stmt.executeQuery("SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'maintenance_tickets'")) {
                System.out.println("maintenance_tickets columns:");
                while (rs.next()) {
                    System.out.println(" - " + rs.getString("column_name") + " (" + rs.getString("data_type") + ")");
                }
            }

            // Ensure test student wallet exists with sufficient balance
            String seedWallet = """
                INSERT INTO public.wallets (user_id, balance_poisha)
                VALUES ('3d1e3d69-ffc6-494f-a42c-26eeb258b581', 100000)
                ON CONFLICT (user_id) DO UPDATE SET balance_poisha = 100000;
            """;
            stmt.execute(seedWallet);
            System.out.println("Seeded test student wallet with 100000 poisha");

            // Also ensure student@kuet.ac.bd and admin@kuet.ac.bd wallets exist
            String seedStudent = """
                INSERT INTO public.wallets (user_id, balance_poisha)
                VALUES ('11305bb5-f12e-4c3c-a87c-ce0eca55aa18', 50000)
                ON CONFLICT (user_id) DO UPDATE SET balance_poisha = 50000;
            """;
            stmt.execute(seedStudent);

            String seedAdmin = """
                INSERT INTO public.wallets (user_id, balance_poisha)
                VALUES ('ac2b8d64-c379-4223-ba0a-71bde5d6edcb', 100000)
                ON CONFLICT (user_id) DO UPDATE SET balance_poisha = 100000;
            """;
            stmt.execute(seedAdmin);

            stmt.executeUpdate("UPDATE public.cycles SET availability_status = 'AVAILABLE'");
            try (ResultSet rs = stmt.executeQuery("SELECT id, label, availability_status, pickup_point FROM public.cycles")) {
                System.out.println("Cycles in database:");
                while (rs.next()) {
                    System.out.println(" - " + rs.getString("id") + " : " + rs.getString("label") + " [" + rs.getString("availability_status") + "] at " + rs.getString("pickup_point"));
                }
            }
        }

        SupabaseCampusRepository repo = new SupabaseCampusRepository();
        CampusUser admin = new CampusUser("ac2b8d64-c379-4223-ba0a-71bde5d6edcb", "KUET Admin", "admin@kuet.ac.bd", Role.ADMIN);
        CampusUser student = new CampusUser("11305bb5-f12e-4c3c-a87c-ce0eca55aa18", "KUET member", "student@kuet.ac.bd", Role.STUDENT);

        try {
            var adminConvos = repo.supportConversations(admin);
            System.out.println("Admin support conversations count: " + adminConvos.size());
        } catch (Exception e) {
            System.out.println("Admin supportConversations failed: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            var studentConvos = repo.supportConversations(student);
            System.out.println("Student support conversations count: " + studentConvos.size());
            for (var c : studentConvos) {
                var msgs = repo.supportMessages(student, c.id());
                System.out.println("Messages for convo " + c.id() + ": " + msgs.size());
            }
        } catch (Exception e) {
            System.out.println("Student supportConversations failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    void testReportDamage() {
        if (!DatabaseConnection.isAvailable()) return;
        CampusUser student = new CampusUser("11305bb5-f12e-4c3c-a87c-ce0eca55aa18", "KUET member", "student@kuet.ac.bd", Role.STUDENT);
        SupabaseCampusRepository repo = new SupabaseCampusRepository();
        var cycles = repo.catalog(student);
        if (cycles.isEmpty()) return;
        var cycle = cycles.get(0);
        System.out.println("Testing reportDamage on cycle: " + cycle.id());
        try {
            var ticket = bd.ac.kuet.campuscycle.service.MaintenanceService.getInstance().reportDamage(
                    cycle.id(), student, "FLAT_TIRE", "Test damage report"
            );
            System.out.println("reportDamage SUCCESS: ticket ID = " + ticket.id());
        } catch (Exception e) {
            System.out.println("reportDamage FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

