package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.Role;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live schema contract for the money features.
 *
 * <p>These migrations are applied by hand in the Supabase SQL editor, so nothing in
 * the build would otherwise notice a missing table or column. The application
 * degrades quietly (probes, NULL-as-CAMPUS_PAY, table-absent reads), which is right
 * for an un-migrated database but wrong for production: a missing due table means
 * every unpaid ride is silently forgotten. This test fails loudly instead.
 */
public class LiveMoneySchemaTest {

    private static boolean available() {
        return DatabaseConnection.isAvailable();
    }

    private static int scalar(String sql) throws Exception {
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /** {@code to_regclass(...)} probes as 1/0 so the value can be read as an int. */
    private static String regclassPresent(String relation) {
        return "(SELECT to_regclass('" + relation + "') IS NOT NULL)::int";
    }

    @Test
    void duesTableExistsWithItsIndexAndTrigger() throws Exception {
        if (!available()) {
            System.out.println("SKIP: Database offline");
            return;
        }

        assertEquals(1, scalar("SELECT " + regclassPresent("public.rental_dues")),
                "public.rental_dues must exist (migration 202609280003)");

        List<String> columns = new ArrayList<>();
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(
                     "SELECT column_name FROM information_schema.columns "
                             + "WHERE table_schema = 'public' AND table_name = 'rental_dues' "
                             + "ORDER BY column_name");
             var rs = ps.executeQuery()) {
            while (rs.next()) {
                columns.add(rs.getString(1));
            }
        }
        for (String required : List.of("id", "rental_id", "user_id", "amount_poisha",
                "paid_poisha", "state", "reason", "created_at", "updated_at")) {
            assertTrue(columns.contains(required), "rental_dues is missing column " + required);
        }

        assertEquals(1, scalar("SELECT " + regclassPresent("public.rental_dues_user_state_idx")),
                "rental_dues_user_state_idx must exist (booking-gate read path)");

        assertEquals(1, scalar("SELECT count(*) FROM pg_trigger t "
                        + "JOIN pg_class c ON c.oid = t.tgrelid "
                        + "WHERE c.relname = 'rental_dues' AND NOT t.tgisinternal"),
                "rental_dues must carry its updated_at trigger");

        // One due per rental is what makes the return flow safe to retry: the
        // upsert in recordDue keys on rental_id, so it must be uniquely indexed.
        int uniqueRentalId;
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement("""
                     SELECT count(*)
                     FROM pg_index i
                     JOIN pg_class c ON c.oid = i.indrelid
                     JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = any(i.indkey)
                     WHERE c.relname = 'rental_dues' AND i.indisunique
                       AND a.attname = 'rental_id' AND array_length(i.indkey, 1) = 1
                     """);
             var rs = ps.executeQuery()) {
            rs.next();
            uniqueRentalId = rs.getInt(1);
        }
        assertEquals(1, uniqueRentalId,
                "rental_dues.rental_id must be uniquely indexed (at most one due per rental)");
    }

    @Test
    void paymentMethodColumnsExist() throws Exception {
        if (!available()) {
            System.out.println("SKIP: Database offline");
            return;
        }

        assertEquals(2, scalar("SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_schema = 'public' AND table_name = 'rentals' "
                        + "AND column_name in ('payment_method', 'cash_collected_poisha')"),
                "rentals needs payment_method + cash_collected_poisha (migration 202609280004)");

        // Campus Pay is the safe default for pre-migration rows.
        String defaultValue;
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement(
                     "SELECT column_default FROM information_schema.columns "
                             + "WHERE table_schema = 'public' AND table_name = 'rentals' "
                             + "AND column_name = 'payment_method'");
             var rs = ps.executeQuery()) {
            assertTrue(rs.next(), "rentals.payment_method must exist");
            defaultValue = rs.getString(1);
        }
        assertTrue(defaultValue != null && defaultValue.contains("CAMPUS_PAY"),
                "rentals.payment_method must default to CAMPUS_PAY, got: " + defaultValue);
    }

    @Test
    void noRentalIsEverPaidOutBeyondWhatWasCollected() throws Exception {
        if (!available()) {
            System.out.println("SKIP: Database offline");
            return;
        }

        // The escrow invariant, checked directly against stored history: a cash-at-hub
        // ride must never credit the owner more than the cash actually collected.
        int violations;
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement("""
                     SELECT count(*)
                     FROM public.rentals r
                     JOIN public.wallet_transactions w
                       ON w.reference_code = 'PAYOUT-' || r.id::text
                     WHERE r.payment_method = 'DOCK_PAY'
                       AND COALESCE(r.cash_collected_poisha, 0) > 0
                       AND w.amount_poisha > COALESCE(r.cash_collected_poisha, 0)
                     """);
             var rs = ps.executeQuery()) {
            rs.next();
            violations = rs.getInt(1);
        }
        assertEquals(0, violations,
                "an owner was paid more than the cash collected on a dock ride");
    }

    @Test
    void adminCanReadDuesAndOnlyAdminsCan() throws Exception {
        if (!available()) {
            System.out.println("SKIP: Database offline");
            return;
        }

        SupabaseCampusRepository repo = new SupabaseCampusRepository();
        CampusUser admin = new CampusUser("ac2b8d64-c379-4223-ba0a-71bde5d6edcb", "KUET Admin", "admin@kuet.ac.bd", Role.ADMIN);
        CampusUser student = new CampusUser("11305bb5-f12e-4c3c-a87c-ce0eca55aa18", "KUET member", "student@kuet.ac.bd", Role.STUDENT);

        // The Admin Dues tab depends on this read; it must not throw.
        List<?> all = repo.allDues(admin);
        assertTrue(all != null, "allDues must return a list for an admin");

        boolean threw = false;
        try {
            repo.allDues(student);
        } catch (SecurityException expected) {
            threw = true;
        }
        assertTrue(threw, "allDues must reject non-admin callers");
    }
}
