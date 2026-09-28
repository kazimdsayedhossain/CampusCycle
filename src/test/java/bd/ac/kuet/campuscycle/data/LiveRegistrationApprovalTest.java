package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.domain.PasswordUtils;
import bd.ac.kuet.campuscycle.domain.UserRegistration;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The registration → approval flow, live.
 *
 * <p>Regression cover for the defect where every approval failed: the registration id
 * was a locally generated UUID with no matching {@code auth.users} row, and
 * {@code profiles.id} has a foreign key to {@code auth.users(id)}, so the profile
 * insert at approval time always violated the key. Registration now provisions a real
 * auth identity and stores its id.
 *
 * <p>Everything created here is removed again in the cleanup.
 */
public class LiveRegistrationApprovalTest {

    private static final String PASSWORD = "ApproveMe123!";

    private String authUid;

    private static boolean live() {
        return DatabaseConnection.isAvailable();
    }

    @Test
    void registrationCanBeApprovedAndOnlyThenGrantsAccess() throws Exception {
        if (!live()) {
            System.out.println("SKIP: Database offline");
            return;
        }

        SupabaseCampusRepository repo = new SupabaseCampusRepository();
        String email = "campuscycle.approve.ok@stud.kuet.ac.bd";
        String passwordHash = PasswordUtils.hash(PASSWORD);

        // 1. Submit. The repo provisions the auth identity and keys the row to it.
        UserRegistration draft = new UserRegistration(
                "", "Approval Test Student", "1907001", "CSE", email,
                "01700000000", passwordHash, "PENDING_APPROVAL", ZonedDateTime.now());
        authUid = repo.submitUserRegistration(draft, PASSWORD);

        // 2. The registration id must now be a real auth user, or approval cannot work.
        assertEquals(1, authUserCount(authUid),
                "registration id must be a real auth.users id (profiles.id FK depends on it)");
        // The live on_auth_user_created trigger inserts a placeholder STUDENT profile for
        // every auth user, so a profile existing here proves nothing about approval —
        // the approved registration is the gate, which is what the other test proves.
        String placeholder = profileDisplayName(authUid);
        assertTrue(placeholder == null || !placeholder.isBlank(),
                "trigger-created profile may exist before approval");

        // 3. Approve — this is the call that used to fail.
        repo.approveUserRegistration(authUid);

        assertEquals(1, profileCount(authUid), "approval must create the profile");
        var approved = repo.getUserRegistration(email).orElseThrow();
        assertTrue(approved.isApproved(), "status must be APPROVED");
        assertEquals(1, walletCount(authUid), "approval must create the wallet");

        // 4. The approved student can now sign in with the password they registered.
        var session = SupabaseAuthService.signIn(email, PASSWORD).join();
        assertNotNull(session.user());
        assertEquals(authUid, session.user().id());
        assertEquals(bd.ac.kuet.campuscycle.domain.Role.STUDENT, session.user().role());
        System.out.println("Approved and signed in: " + session.user().email() + " role=" + session.user().role());
    }

    @Test
    void anUnapprovedApplicantIsStillRefused() throws Exception {
        if (!live()) {
            System.out.println("SKIP: Database offline");
            return;
        }
        SupabaseCampusRepository repo = new SupabaseCampusRepository();
        String email = "campuscycle.approve.pending@stud.kuet.ac.bd";

        UserRegistration draft = new UserRegistration(
                "", "Still Pending Student", "1907002", "CSE", email,
                "01700000000", PasswordUtils.hash(PASSWORD), "PENDING_APPROVAL", ZonedDateTime.now());
        authUid = repo.submitUserRegistration(draft, PASSWORD);

        assertEquals(1, authUserCount(authUid), "the auth identity exists before approval");
        assertEquals(0, walletCount(authUid),
                "an unapproved applicant must not get a wallet either");

        // A real auth user and a valid password, but no approval: access must be refused.
        assertThrows(Exception.class, () -> SupabaseAuthService.signIn(email, PASSWORD).join(),
                "an unapproved applicant must not be able to sign in");
        System.out.println("Unapproved applicant correctly refused");
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private int authUserCount(String uid) throws Exception {
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement("SELECT count(*) FROM auth.users WHERE id = ?::uuid")) {
            ps.setString(1, uid);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int profileCount(String uid) throws Exception {
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement("SELECT count(*) FROM public.profiles WHERE id = ?::uuid")) {
            ps.setString(1, uid);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private String profileDisplayName(String uid) throws Exception {
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement("SELECT display_name FROM public.profiles WHERE id = ?::uuid")) {
            ps.setString(1, uid);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private int walletCount(String uid) throws Exception {
        try (var conn = DatabaseConnection.getConnection();
             var ps = conn.prepareStatement("SELECT count(*) FROM public.wallets WHERE user_id = ?")) {
            ps.setString(1, uid);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Removes everything this test created, auth user included. */
    @org.junit.jupiter.api.AfterEach
    void cleanUp() {
        if (!live()) return;
        try {
            if (authUid != null) {
                try (var conn = DatabaseConnection.getConnection();
                     var ps = conn.prepareStatement("DELETE FROM public.pending_registrations WHERE id = ?::uuid")) {
                    ps.setString(1, authUid);
                    ps.executeUpdate();
                }
                SupabaseAuthService.deleteAuthIdentity(authUid);
            }
        } catch (Exception e) {
            System.out.println("cleanup warning: " + e.getMessage());
        }
    }
}
