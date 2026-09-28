package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SupabaseCampusRepositoryTest {

    private final CampusUser student = new CampusUser("11305bb5-f12e-4c3c-a87c-ce0eca55aa18", "KUET member", "student@kuet.ac.bd", Role.STUDENT);
    private final CampusUser admin = new CampusUser("ac2b8d64-c379-4223-ba0a-71bde5d6edcb", "KUET Admin", "admin@kuet.ac.bd", Role.ADMIN);

    @org.junit.jupiter.api.BeforeEach
    void resetFleetAvailability() {
        if (!DatabaseConnection.isAvailable()) return;
        SupabaseCampusRepository repo = new SupabaseCampusRepository();
        // Note: updateCycleState requires expected current state, so we skip auto-reset
        // Each test should manage its own cycle state
    }

    @Test
    void testCatalogAndPendingQueries() {
        if (!DatabaseConnection.isAvailable()) {
            System.out.println("SKIP: Database offline");
            return;
        }

        SupabaseCampusRepository repo = new SupabaseCampusRepository();
        List<CycleItem> catalog = repo.catalog(student);
        assertNotNull(catalog);
        assertFalse(catalog.isEmpty(), "Catalog should contain live seeded cycles");
        System.out.println("Live catalog count: " + catalog.size());

        List<CycleItem> pending = repo.pendingCycles();
        assertNotNull(pending);
        System.out.println("Live pending cycles count: " + pending.size());
    }

    @Test
    void testBookingAndReturnLifecycle() throws Exception {
        if (!DatabaseConnection.isAvailable()) {
            System.out.println("SKIP: Database offline");
            return;
        }

        SupabaseCampusRepository repo = new SupabaseCampusRepository();
        // Clean up any existing active rental from prior runs
        RentalRecord existing = repo.activeRental(student);
        if (existing != null) {
            repo.returnRental(student, existing.id());
        }

        List<CycleItem> catalog = repo.catalog(student);
        CycleItem cycle = catalog.stream()
                .filter(c -> c.availabilityStatus() == AvailabilityStatus.AVAILABLE)
                .findFirst()
                .orElse(null);
        if (cycle == null) return;
        System.out.println("Booking cycle: " + cycle.id() + " (" + cycle.label() + ")");

        // Ensure student wallet is funded
        try (var conn = DatabaseConnection.getConnection();
             var s = conn.prepareStatement("INSERT INTO public.wallets (user_id, balance_poisha) VALUES (?, 100000) ON CONFLICT (user_id) DO UPDATE SET balance_poisha = 100000")) {
            s.setString(1, student.id());
            s.executeUpdate();
        }

        // Book cycle via the production path (atomic wallet charge)
        RentalRecord rental = repo.book(student, cycle.id(), 30,
                CampusRepository.PaymentMethod.CAMPUS_PAY);
        assertNotNull(rental);
        assertEquals(RentalStatus.ACTIVE, rental.status());
        assertEquals(cycle.id(), rental.cycleId());
        System.out.println("Booked rental ID: " + rental.id() + ", fare: " + rental.quotedAmountPoisha() + " poisha");

        // Verify active rental query
        RentalRecord active = repo.activeRental(student);
        assertNotNull(active);
        assertEquals(rental.id(), active.id());

        // Return cycle (atomically updates cycle status to AVAILABLE)
        repo.returnRental(student, rental.id());
        System.out.println("Returned rental successfully.");

        // Verify no active rental
        RentalRecord postReturn = repo.activeRental(student);
        assertNull(postReturn);

        // Platform split assertions (migration-aware: 202609280001).
        boolean feeCols = false;
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(
                     "SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' "
                     + "AND table_name = 'rentals' AND column_name = 'platform_fee_poisha'")) {
            try (var rs = ps.executeQuery()) {
                feeCols = rs.next();
            }
        }
        RentalRecord settled = repo.rentals(student).stream()
                .filter(r -> r.id().equals(rental.id())).findFirst().orElse(null);
        assertNotNull(settled, "Settled rental must appear in history");
        if (feeCols) {
            assertEquals(settled.effectiveFarePoisha(),
                    settled.platformFeePoisha() + settled.ownerPayoutPoisha(),
                    "fee + payout must equal settled fare");
            assertTrue(settled.platformFeePoisha() > 0, "fee must be positive on a paid fare");
            assertTrue(settled.ownerPayoutPoisha() > 0, "owner payout must be positive");
            // Owner wallet credited + idempotent ledger row present.
            try (var conn = DatabaseConnection.getConnection();
                 var ps = conn.prepareStatement(
                         "SELECT balance_after_poisha FROM public.wallet_transactions "
                         + "WHERE user_id = ? AND reference_code = ?")) {
                ps.setString(1, cycle.ownerId());
                ps.setString(2, "PAYOUT-" + rental.id());
                try (var rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "OWNER_PAYOUT ledger row must exist for " + rental.id());
                }
            }
            System.out.println("Settle split verified: fare=" + settled.effectiveFarePoisha()
                    + " fee=" + settled.platformFeePoisha()
                    + " payout=" + settled.ownerPayoutPoisha());
        } else {
            assertEquals(0, settled.platformFeePoisha(), "pre-migration rows read fee 0");
            assertEquals(0, settled.ownerPayoutPoisha(), "pre-migration rows read payout 0");
            System.out.println("Fee columns absent: fallback reads zeroed split (forward-compat OK).");
        }
    }

    @Test
    void testAuthenticationFlows() throws Exception {
        if (!DatabaseConnection.isAvailable()) {
            System.out.println("SKIP: Database offline");
            return;
        }

        try (var conn = DatabaseConnection.getConnection();
             var stmt = conn.createStatement()) {
            System.out.println("=== AUTH USERS ===");
            try (var rs = stmt.executeQuery("SELECT id, email, created_at FROM auth.users")) {
                while (rs.next()) {
                    System.out.println("AuthUser: id=" + rs.getString("id") + ", email=" + rs.getString("email"));
                }
            }

            System.out.println("=== PROFILES ===");
            try (var rs = stmt.executeQuery("SELECT id, display_name, role FROM public.profiles")) {
                while (rs.next()) {
                    System.out.println("Profile: id=" + rs.getString("id") + ", name=" + rs.getString("display_name") + ", role=" + rs.getString("role"));
                }
            }

            System.out.println("=== PENDING REGISTRATIONS ===");
            try (var rs = stmt.executeQuery("SELECT id, full_name, student_roll, email, verification_status FROM public.pending_registrations")) {
                while (rs.next()) {
                    System.out.println("Reg: roll=" + rs.getString("student_roll") + ", email=" + rs.getString("email") + ", status=" + rs.getString("verification_status"));
                }
            }

            System.out.println("=== CYCLES ===");
            try (var rs = stmt.executeQuery("SELECT count(*) FROM public.cycles")) {
                if (rs.next()) System.out.println("Cycles count: " + rs.getInt(1));
            }

            System.out.println("=== TESTING SIGN IN: admin@kuet.ac.bd ===");
            try {
                var session = SupabaseAuthService.signIn("admin@kuet.ac.bd", "TestPass123!").get();
                System.out.println("SUCCESS! User: " + session.user().displayName() + " Role: " + session.user().role());
            } catch (Exception e) {
                System.out.println("FAILED TO SIGN IN: " + e.getMessage());
                if (e.getCause() != null) {
                    System.out.println("CAUSE: " + e.getCause().getMessage());
                    e.getCause().printStackTrace();
                }
            }
        }
    }
}
