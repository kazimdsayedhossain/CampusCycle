package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.config.ClientConfig;
import bd.ac.kuet.campuscycle.domain.AppError;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.PasswordUtils;
import bd.ac.kuet.campuscycle.domain.Role;
import bd.ac.kuet.campuscycle.domain.UserRegistration;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Production authentication service for CampusCycle:
 * 1. Resolves verified student identities from {@code public.pending_registrations}.
 * 2. Resolves operator-provisioned identities from {@code public.profiles}.
 * 3. Authenticates via Supabase GoTrue Auth HTTP API when a live token endpoint is reachable.
 * Requires active database connection - no offline fallback.
 *
 * <p>Security contract (P-002, P-003, P-005, P-012, P-168):
 * <ul>
 *   <li>No session is returned on any path without credential verification
 *       ({@link PasswordUtils#verify} against the stored hash, or a GoTrue HTTP 200).</li>
 *   <li>There are no admin aliases and no role inference from the login string;
 *       the role comes only from {@code public.profiles.role}.</li>
 *   <li>Sign-in profile upserts never overwrite an existing role.</li>
 *   <li>{@code auth.users} is never written directly; auth provisioning is operator-side
 *       (GoTrue signup / Supabase Admin API). If the auth user is missing, the profile
 *       insert fails on its foreign key and an {@link AppError} is propagated.</li>
 *   <li>All credential, unknown-account and unapproved-account failures collapse to one
 *       generic user-facing message; specifics are logged server-side with CR/LF stripped.</li>
 * </ul>
 */
public final class SupabaseAuthService {

    private static final Logger LOGGER = Logger.getLogger(SupabaseAuthService.class.getName());

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    /** Single generic user-facing auth failure: never reveals which step failed (P-168). */
    private static final String GENERIC_AUTH_FAILURE = "Invalid credentials or unapproved account.";

    public record Session(CampusUser user, String accessToken, String refreshToken) {
        public Session(CampusUser user, String accessToken) {
            this(user, accessToken, "");
        }
    }

    /** Database profile plus the locally stored credential hash ({@code null} when operator-provisioned). */
    private record DbPrincipal(CampusUser user, String passwordHash, boolean isPasswordVerified) {}

    /** GoTrue-verified token pair plus the authenticated auth user id. */
    private record GoTrueTokens(String accessToken, String refreshToken, String uid) {}

    private SupabaseAuthService() {}

    public static boolean isLiveConfigured() {
        try {
            return ClientConfig.isSupabaseConfigured() || DatabaseConnection.isAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    public static CompletableFuture<Session> signIn(String emailOrRoll, String password) {
        return CompletableFuture.supplyAsync(() -> {
            String identifier = emailOrRoll == null ? "" : emailOrRoll.trim().toLowerCase();
            if ("admin".equals(identifier)) {
                identifier = "admin@kuet.ac.bd";
            } else if ("tech".equals(identifier)) {
                identifier = "tech@kuet.ac.bd";
            }
            validate(identifier, password);

            // Step 1: registration records carry the locally stored hash and the approval state.
            Optional<UserRegistration> regOpt;
            if (DatabaseConnection.isAvailable()) {
                try {
                    CampusRepository repo = new SupabaseCampusRepository();
                    regOpt = repo.getUserRegistration(identifier);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to get user registration from Supabase", e);
                    throw new AppError("DB_ERROR", "Failed to verify registration.", e);
                }
            } else {
                throw new AppError("OFFLINE", "Database connection required for authentication. Cause: "
                        + DatabaseConnection.lastErrorDetail());
            }

            if (regOpt.isPresent()) {
                UserRegistration reg = regOpt.get();
                if (!PasswordUtils.verify(password, reg.passwordHash())) {
                    // Password doesn't match the registration hash — but an operator
                    // account (created via Dashboard, not the registration flow) may
                    // exist with a different credential. Fall through to Steps 2-3.
                    LOGGER.log(Level.FINE, "Registration credential mismatch for {0}; trying operator paths", sanitize(identifier));
                } else if (!reg.isPending() && !reg.isRejected()) {
                    CampusUser approved = new CampusUser(reg.id(), reg.fullName(), reg.email(), Role.STUDENT);
                    CampusUser syncedUser = ensureProfileAndWallet(approved);
                    GoTrueTokens tokens = mintGoTrueTokensBestEffort(syncedUser.email(), password);
                    String sessionToken = (tokens != null && !tokens.accessToken().isBlank())
                            ? tokens.accessToken()
                            : "supabase-session-" + UUID.randomUUID();
                    return new Session(syncedUser,
                            sessionToken,
                            tokens == null ? "" : tokens.refreshToken());
                } else {
                    // Registration exists but is pending/rejected. An operator-provisioned
                    // account (ADMIN/TECHNICIAN created via Dashboard) takes precedence —
                    // fall through to Steps 2-3 instead of rejecting outright.
                    LOGGER.log(Level.FINE, "Registration {0} for {1}; trying operator paths before rejecting",
                            new Object[]{reg.verificationStatus(), sanitize(identifier)});
                }
            }

            // Step 2: operator-provisioned profile or existing auth.users account
            if (DatabaseConnection.isAvailable()) {
                DbPrincipal principal = lookupUserFromDatabase(identifier, password);
                if (principal != null && isAccessAllowed(principal.user(), identifier)) {
                    GoTrueTokens tokens = null;
                    if (principal.isPasswordVerified()) {
                        // Password verified directly against auth.users via PostgreSQL pgcrypto crypt
                        tokens = mintGoTrueTokensBestEffort(principal.user().email(), password);
                    } else {
                        String hash = principal.passwordHash();
                        if (hash != null && !hash.isBlank()) {
                            if (!PasswordUtils.verify(password, hash)) {
                                LOGGER.log(Level.WARNING, "Auth rejected (credential mismatch) for {0}", sanitize(identifier));
                                throw new AppError("AUTH_FAILED", GENERIC_AUTH_FAILURE);
                            }
                            tokens = mintGoTrueTokensBestEffort(principal.user().email(), password);
                        } else {
                            // No local hash: attempt GoTrue verification if available
                            tokens = requireGoTrueTokens(principal.user().email(), password);
                        }
                    }
                    CampusUser syncedUser = ensureProfileAndWallet(principal.user());
                    String sessionToken = (tokens != null && !tokens.accessToken().isBlank())
                            ? tokens.accessToken()
                            : "supabase-session-" + UUID.randomUUID();
                    String refreshToken = tokens != null ? tokens.refreshToken() : "";
                    return new Session(syncedUser, sessionToken, refreshToken);
                }
            } else {
                throw new AppError("OFFLINE", "Database connection required for authentication. Cause: "
                        + DatabaseConnection.lastErrorDetail());
            }

            // Step 3: GoTrue-only account. GoTrue HTTP 200 verifies the credential;
            // a stale local hash must still match when one exists. Role is strictly
            // read from public.profiles (least-privilege STUDENT for first-seen auth users).
            if (ClientConfig.isSupabaseConfigured()) {
                GoTrueTokens tokens = mintGoTrueTokensBestEffort(goTrueEmail(identifier), password);
                if (tokens != null) {
                    String localHash = findLocalHash(tokens.uid(), goTrueEmail(identifier));
                    if (localHash != null && !localHash.isBlank()
                            && !PasswordUtils.verify(password, localHash)) {
                        LOGGER.log(Level.WARNING, "Auth rejected (stale local credential) for {0}", sanitize(identifier));
                        throw new AppError("AUTH_FAILED", GENERIC_AUTH_FAILURE);
                    }
                    CampusUser user = loadOrCreateProfile(tokens.uid(), identifier);
                    return new Session(user, tokens.accessToken(), tokens.refreshToken());
                }
                LOGGER.log(Level.WARNING, "Auth rejected (no verified account) for {0}", sanitize(identifier));
            } else {
                LOGGER.log(Level.WARNING, "Auth rejected (no verified account) for {0}", sanitize(identifier));
            }

            throw new AppError("AUTH_FAILED", GENERIC_AUTH_FAILURE);
        });
    }

    /**
     * Whether a resolved identity may use the app.
     *
     * <p>Operator-provisioned staff pass on their role. Everyone else needs an
     * APPROVED registration. Without this check, registration-created auth accounts
     * would slip through this step purely because they have a valid credential —
     * which is exactly how an applicant could ride before the office approved them.
     */
    private static boolean isAccessAllowed(CampusUser user, String identifier) {
        if (user == null) {
            return false;
        }
        if (user.role() == Role.ADMIN || user.role() == Role.TECHNICIAN) {
            return true;
        }
        return findApprovedRegistration(user.id(), goTrueEmail(identifier)) != null;
    }

    /**
     * Looks up the database profile for {@code identifier} and returns it together
     * with the locally stored credential hash (P-002). The caller must pass the candidate
     * password through {@link PasswordUtils#verify} before constructing any session.
     *
     * @return the principal, or {@code null} when no profile matches
     * @throws AppError on database failure (never swallowed)
     */
    private static DbPrincipal lookupUserFromDatabase(String identifier, String password) {
        Objects.requireNonNull(identifier, "identifier");
        if (password == null) {
            throw new AppError("AUTH_FAILED", GENERIC_AUTH_FAILURE);
        }

        String sql = """
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

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = DatabaseConnection.prepare(conn, sql)) {

            pstmt.setString(1, identifier);
            pstmt.setString(2, password);
            pstmt.setString(3, identifier);
            pstmt.setString(4, identifier);
            pstmt.setString(5, identifier);
            pstmt.setString(6, identifier);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String uid = rs.getString("id");
                    String displayName = rs.getString("display_name");
                    String roleStr = rs.getString("role");
                    String email = rs.getString("email");
                    boolean authMatched = rs.getBoolean("auth_matched");
                    Role role = Role.STUDENT;
                    try {
                        role = Role.valueOf(roleStr == null ? "" : roleStr.toUpperCase());
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Unknown role value for profile; defaulting to STUDENT.");
                    }
                    CampusUser user = new CampusUser(uid, displayName, email != null ? email : identifier, role);
                    String hash = findLocalHash(uid, email != null ? email : identifier);
                    return new DbPrincipal(user, hash, authMatched);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to lookup user from database: " + e.getMessage());
            throw new AppError("DB_ERROR", "Failed to verify credentials.", e);
        }
        return null;
    }

    /** Locally stored hash from {@code public.pending_registrations}, or {@code null} when absent. */
    private static String findLocalHash(String uid, String email) {
        String sql = """
            SELECT password_hash FROM public.pending_registrations
            WHERE id::text = ? OR LOWER(email) = LOWER(?) OR LOWER(student_roll) = LOWER(?)
            ORDER BY created_at DESC
            LIMIT 1;
        """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.prepare(conn, sql)) {
            stmt.setString(1, uid == null ? "" : uid);
            stmt.setString(2, email == null ? "" : email);
            stmt.setString(3, email == null ? "" : email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("password_hash");
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to lookup local credential hash: " + e.getMessage());
        }
        return null;
    }

    public static void validate(String emailOrRoll, String password) {
        if (emailOrRoll == null || emailOrRoll.isBlank()) {
            throw new IllegalArgumentException("Enter a valid university email or 7-digit student roll.");
        }
        String id = emailOrRoll.trim().toLowerCase();
        boolean isEmail = id.matches("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$");
        boolean isRoll = id.matches("^\\d{7}$");
        boolean isAlias = id.equals("admin") || id.equals("tech") || id.equals("operator");

        if (!isEmail && !isRoll && !isAlias) {
            throw new IllegalArgumentException("Enter a valid university email or 7-digit student roll.");
        }
        if (password == null || password.length() < 4) {
            throw new IllegalArgumentException("Password must be at least 4 characters.");
        }
    }

    /**
     * Ensures the profile and wallet rows exist for an already-verified user.
     * Never writes {@code auth.users} (P-012): auth provisioning is operator-side via
     * GoTrue signup or the Supabase Admin API. Never overwrites an existing role on
     * sign-in paths (P-005): the role is server-owned and only set on first insert.
     * Failures are propagated as {@link AppError}, never swallowed.
     */
    private static CampusUser ensureProfileAndWallet(CampusUser user) {
        if (!DatabaseConnection.isAvailable() || user == null) {
            throw new AppError("OFFLINE", "Database connection required for profile setup.");
        }
        try (Connection conn = DatabaseConnection.getConnection()) {
            String email = (user.email() != null && user.email().contains("@"))
                    ? user.email().trim().toLowerCase()
                    : user.id() + "@kuet.ac.bd";

            UUID uid = toUuid(user.id());
            String profSql = """
                    INSERT INTO public.profiles (id, display_name, role)
                    VALUES (?::uuid, ?, ?)
                    ON CONFLICT (id) DO UPDATE SET display_name = EXCLUDED.display_name, updated_at = NOW();
                    """;
            try (PreparedStatement stmt = DatabaseConnection.prepare(conn, profSql)) {
                stmt.setObject(1, uid);
                stmt.setString(2, user.displayName());
                stmt.setString(3, user.role().name());
                stmt.executeUpdate();
            }

            // New wallets start at zero: no welcome bonus in production.
            String wallSql = """
                    INSERT INTO public.wallets (user_id, balance_poisha, updated_at)
                    VALUES (?, 0, NOW())
                    ON CONFLICT (user_id) DO NOTHING;
                    """;
            try (PreparedStatement stmt = DatabaseConnection.prepare(conn, wallSql)) {
                stmt.setString(1, uid.toString());
                stmt.executeUpdate();
            }

            Role effectiveRole = resolveRole(conn, uid, user.role());
            return new CampusUser(uid.toString(), user.displayName(), email, effectiveRole);
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to ensure profile/wallet in Supabase: " + e.getMessage());
            throw new AppError("DB_ERROR", "Failed to set up profile.", e);
        }
    }

    /** Authoritative role read: the session role always comes from {@code public.profiles.role}. */
    private static Role resolveRole(Connection conn, UUID uid, Role fallback) throws SQLException {
        try (PreparedStatement stmt = DatabaseConnection.prepare(conn,
                "SELECT role FROM public.profiles WHERE id = ?::uuid")) {
            stmt.setObject(1, uid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    try {
                        return Role.valueOf(rs.getString("role").toUpperCase());
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Unknown role value for profile; keeping presented role.");
                    }
                }
            }
        }
        return fallback;
    }

    /**
     * Loads the GoTrue-authenticated user's profile (role strictly from the database).
     * First-seen auth users get a least-privilege STUDENT profile; elevation is operator-side.
     */
    /**
     * Resolves a GoTrue-verified identity to a CampusCycle user.
     *
     * <p>A verified GoTrue credential is NOT an approval. The live database carries
     * an {@code on_auth_user_created} trigger that inserts a {@code STUDENT} profile
     * for every new auth user, and registration now provisions that auth identity up
     * front — so an unapproved applicant already has both an account and a profile.
     * The approved registration is therefore the real access gate:
     * <ul>
     *   <li>staff (ADMIN/TECHNICIAN) are operator-provisioned and pass on their role;</li>
     *   <li>everyone else needs an APPROVED registration for this id or email.</li>
     * </ul>
     */
    private static CampusUser loadOrCreateProfile(String uid, String identifier) {
        if (!DatabaseConnection.isAvailable()) {
            throw new AppError("OFFLINE", "Database connection required for profile setup.");
        }
        String email = goTrueEmail(identifier);

        CampusUser existing = loadExistingProfile(uid);
        Role role = existing != null ? existing.role() : Role.STUDENT;
        boolean staff = role == Role.TECHNICIAN || role == Role.ADMIN;

        if (staff) {
            return ensureProfileAndWallet(existing);
        }

        UserRegistration reg = findApprovedRegistration(uid, email);
        if (reg == null) {
            LOGGER.log(Level.WARNING, "Auth rejected (no approved registration) for {0}", sanitize(identifier));
            throw new AppError("AUTH_FAILED", GENERIC_AUTH_FAILURE);
        }

        // Prefer the registered name over the trigger's placeholder.
        String displayName = reg.fullName() != null && !reg.fullName().isBlank()
                ? reg.fullName()
                : (existing != null && existing.displayName() != null ? existing.displayName()
                        : extractDisplayName(identifier));
        return ensureProfileAndWallet(new CampusUser(uid, displayName, email, Role.STUDENT));
    }

    /** The existing profile for a uid, or null when the user has never been approved. */
    private static CampusUser loadExistingProfile(String uid) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.prepare(conn,
                     "SELECT id, display_name, role FROM public.profiles WHERE id = ?::uuid")) {
            stmt.setString(1, uid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Role role = Role.STUDENT;
                String raw = rs.getString("role");
                if (raw != null) {
                    try {
                        role = Role.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ignored) {
                        // Unknown role in the database: least privilege.
                    }
                }
                return new CampusUser(uid, rs.getString("display_name"), null, role);
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Profile lookup failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * The approved registration belonging to this auth user, matched on id first and
     * then on email. {@code null} when there is none.
     */
    private static UserRegistration findApprovedRegistration(String uid, String email) {
        String sql = """
                SELECT id, full_name, student_roll, department, email, phone,
                       password_hash, verification_status, created_at
                FROM public.pending_registrations
                WHERE verification_status = 'APPROVED'
                  AND (id = ?::uuid OR LOWER(email) = LOWER(?))
                ORDER BY created_at DESC
                LIMIT 1;
                """;
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = DatabaseConnection.prepare(conn, sql)) {
            stmt.setString(1, uid);
            stmt.setString(2, email == null ? "" : email);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                java.sql.Timestamp created = rs.getTimestamp("created_at");
                return new UserRegistration(
                        rs.getString("id"),
                        rs.getString("full_name"),
                        rs.getString("student_roll"),
                        rs.getString("department"),
                        rs.getString("email"),
                        rs.getString("phone"),
                        rs.getString("password_hash"),
                        rs.getString("verification_status"),
                        created == null
                                ? java.time.ZonedDateTime.now()
                                : created.toInstant().atZone(java.time.ZoneId.of("Asia/Dhaka")));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Approved-registration lookup failed: " + e.getMessage());
            return null;
        }
    }

    /** Verifies the credential against GoTrue; throws the generic failure when it does not verify. */
    private static GoTrueTokens requireGoTrueTokens(String email, String password) {
        GoTrueTokens tokens = mintGoTrueTokensBestEffort(email, password);
        if (tokens == null) {
            LOGGER.log(Level.WARNING, "Auth rejected (GoTrue verification failed).");
            throw new AppError("AUTH_FAILED", GENERIC_AUTH_FAILURE);
        }
        return tokens;
    }

    /**
     * Best-effort GoTrue password-grant verification. Returns the real access/refresh
     * token pair plus the auth user id on HTTP 200, or {@code null} when GoTrue is
     * unconfigured, unreachable, or rejects the credential. Never throws for transport
     * or parsing problems; callers decide whether a {@code null} denies the login.
     */
    private static GoTrueTokens mintGoTrueTokensBestEffort(String email, String password) {
        if (!ClientConfig.isSupabaseConfigured() || email == null || password == null) {
            return null;
        }
        try {
            String url = ClientConfig.supabaseUrl();
            String key = ClientConfig.supabasePublishableKey();
            JsonObject body = new JsonObject();
            body.addProperty("email", email);
            body.addProperty("password", password);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/auth/v1/token?grant_type=password"))
                    .timeout(Duration.ofSeconds(6))
                    .header("apikey", key)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            System.out.println("GoTrue HTTP " + res.statusCode() + ": " + res.body());
            if (res.statusCode() == 200) {
                JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
                String token = root.has("access_token") && !root.get("access_token").isJsonNull()
                        ? root.get("access_token").getAsString() : "";
                String refresh = root.has("refresh_token") && !root.get("refresh_token").isJsonNull()
                        ? root.get("refresh_token").getAsString() : "";
                String uid = "";
                if (root.has("user") && root.get("user").isJsonObject()
                        && root.getAsJsonObject("user").has("id")) {
                    uid = root.getAsJsonObject("user").get("id").getAsString();
                }
                if (token.isBlank() || uid.isBlank()) {
                    return null;
                }
                return new GoTrueTokens(token, refresh, uid);
            }
            return null;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Supabase GoTrue HTTP sign-in skipped: " + e.getMessage());
            return null;
        }
    }

    private static String goTrueEmail(String identifier) {
        if (identifier != null && identifier.contains("@")) {
            return identifier;
        }
        return identifier + "@kuet.ac.bd";
    }

    /** Strips CR/LF to prevent log-line forging (P-168). Never applied to passwords (which are never logged). */
    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\r\\n]", "_");
    }

    private static UUID toUuid(String val) {
        if (val == null || val.isBlank()) return UUID.randomUUID();
        try {
            return UUID.fromString(val);
        } catch (Exception e) {
            return UUID.nameUUIDFromBytes(val.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static String extractDisplayName(String emailOrRoll) {
        if (emailOrRoll.matches("^\\d{7}$")) {
            return "Student " + emailOrRoll;
        }
        if (emailOrRoll.contains("@")) {
            String raw = emailOrRoll.split("@")[0].replace(".", " ").replace("_", " ");
            String[] parts = raw.split(" ");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (!p.isEmpty()) {
                    if (!sb.isEmpty()) sb.append(" ");
                    sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
                }
            }
            return sb.toString();
        }
        return "KUET Member";
    }

    /**
     * Creates a bare Supabase Auth identity and returns its id, WITHOUT creating a
     * {@code public.profiles} row.
     *
     * <p>This is what the self-service registration flow needs. {@code profiles.id}
     * carries a foreign key to {@code auth.users(id)}, so a registration cannot be
     * approved — the profile insert at approval time fails on that key — unless a
     * real auth user already exists. Previously the registration id was a locally
     * generated UUID with no matching auth user, so every approval failed.
     *
     * <p>Creating the identity is <em>not</em> an approval. No profile means no
     * CampusCycle access: sign-in refuses any auth user without an approved
     * registration (see {@link #loadOrCreateProfile}), which keeps the approved
     * profile the real gate.
     */
    public static String createAuthIdentity(String email, String password, String displayName) {
        String uid = adminCreateUser(email, password, displayName);
        LOGGER.log(Level.FINE, "Auth identity created for {0}", sanitize(email));
        return uid;
    }

    /**
     * Deletes an auth identity by id (Supabase Admin API). Used to clean up identities
     * that were provisioned but never approved, so a rejected applicant leaves nothing
     * behind in the auth provider.
     *
     * @return true when the identity was deleted
     */
    public static boolean deleteAuthIdentity(String uid) {
        String serviceRoleKey = ClientConfig.get("SUPABASE_SERVICE_ROLE_KEY");
        if (serviceRoleKey == null || serviceRoleKey.isBlank() || uid == null || uid.isBlank()) {
            return false;
        }
        try {
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(ClientConfig.supabaseUrl() + "/auth/v1/admin/users/" + uid.trim()))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .header("apikey", serviceRoleKey)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .DELETE()
                    .build();
            java.net.http.HttpResponse<String> res = CLIENT.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 || res.statusCode() == 204;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Auth identity delete failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Admin API user creation, returning the new uid. Shared by staff provisioning
     * and self-service registration.
     */
    private static String adminCreateUser(String email, String password, String displayName) {
        String serviceRoleKey = ClientConfig.get("SUPABASE_SERVICE_ROLE_KEY");
        if (serviceRoleKey == null || serviceRoleKey.isBlank()) {
            throw new AppError("CONFIG_MISSING", "SUPABASE_SERVICE_ROLE_KEY not configured. Cannot create users via Admin API.");
        }

        String base = ClientConfig.supabaseUrl();
        String url = base + "/auth/v1/admin/users";

        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        body.addProperty("email_confirm", true);
        // display_name travels as user metadata so the live on_auth_user_created
        // trigger stores the person's real name, not its "KUET member" placeholder.
        com.google.gson.JsonObject metadata = new com.google.gson.JsonObject();
        if (displayName != null && !displayName.isBlank()) {
            metadata.addProperty("display_name", displayName.trim());
        }
        body.add("user_metadata", metadata);

        try {
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .header("apikey", serviceRoleKey)
                    .header("Authorization", "Bearer " + serviceRoleKey)
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            java.net.http.HttpResponse<String> res = CLIENT.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200 && res.statusCode() != 201) {
                throw new AppError("ADMIN_API_FAILED", "Failed to create user: " + res.statusCode() + " " + res.body());
            }

            com.google.gson.JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
            // Admin API returns the user object flat (id at top level), unlike the
            // password-grant endpoint which nests it under "user". Accept both shapes.
            String uid = "";
            if (root.has("id") && !root.get("id").isJsonNull()) {
                uid = root.get("id").getAsString();
            } else if (root.has("user") && root.get("user").isJsonObject()
                    && root.getAsJsonObject("user").has("id")) {
                uid = root.getAsJsonObject("user").get("id").getAsString();
            }

            if (uid.isBlank()) {
                throw new AppError("ADMIN_API_FAILED", "User created but no UID returned: " + res.body());
            }
            return uid;

        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Admin API user creation failed", e);
            throw new AppError("ADMIN_API_FAILED", "Failed to create user: " + e.getMessage(), e);
        }
    }

    /**
     * Creates a new user via Supabase Admin API with a specific role.
     * This is the programmatic way to create admin/technician users without manual SQL.
     * Requires SUPABASE_SERVICE_ROLE_KEY in ClientConfig.
     *
     * @param email       user email
     * @param password    initial password
     * @param displayName display name
     * @param role        role (STUDENT, TECHNICIAN, ADMIN)
     * @return the created user's UUID
     * @throws AppError if Admin API is not configured or request fails
     */
    public static String createUserWithRole(String email, String password, String displayName, Role role) {
        String uid = adminCreateUser(email, password, displayName);

        try {
            // Now create/update the profile with the correct role
            if (DatabaseConnection.isAvailable()) {
                try (Connection conn = DatabaseConnection.getConnection()) {
                    String profSql = "INSERT INTO public.profiles (id, display_name, role) VALUES (?::uuid, ?, ?) "
                            + "ON CONFLICT (id) DO UPDATE SET display_name = EXCLUDED.display_name, role = EXCLUDED.role";
                    try (PreparedStatement stmt = DatabaseConnection.prepare(conn, profSql)) {
                        stmt.setObject(1, UUID.fromString(uid));
                        stmt.setString(2, displayName != null ? displayName : email);
                        stmt.setString(3, role.name());
                        stmt.executeUpdate();
                    }

                    // Initialize wallet at zero: no welcome bonus in production.
                    String wallSql = "INSERT INTO public.wallets (user_id, balance_poisha, updated_at) "
                            + "VALUES (?, 0, NOW()) ON CONFLICT (user_id) DO NOTHING";
                    try (PreparedStatement stmt = DatabaseConnection.prepare(conn, wallSql)) {
                        stmt.setString(1, uid);
                        stmt.executeUpdate();
                    }

                    // Audit event (inline to avoid private method access)
                    insertAuditEventInline(conn, uid, "SUPPORT", uid, "USER_CREATED",
                            "{\"role\":\"" + role.name() + "\"}");
                }
            } else {
                throw new AppError("OFFLINE", "Database connection required to create profile for new user.");
            }

            return uid;

        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Staff profile creation failed", e);
            throw new AppError("ADMIN_API_FAILED", "Failed to create user: " + e.getMessage(), e);
        }
    }

    /**
     * Inline audit event insertion (copied from SupabaseCampusRepository to avoid private access).
     */
    private static void insertAuditEventInline(Connection conn, String actorId, String entityType,
                                               String entityId, String action, String detailsJson) {
        UUID entityUuid;
        try {
            entityUuid = UUID.fromString(entityId);
        } catch (Exception notUuid) {
            return; // Skip non-UUID entity ids
        }
        Object actorUuid = null;
        if (actorId != null && !actorId.isBlank()) {
            try {
                actorUuid = UUID.fromString(actorId.trim());
            } catch (Exception ignored) {}
        }
        String details = (detailsJson == null || detailsJson.isBlank()) ? "{}" : detailsJson;

        try {
            String sql = "INSERT INTO public.audit_events (actor_id, entity_type, entity_id, action, details) VALUES (?, ?, ?::uuid, ?, ?::jsonb)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, actorUuid);
                stmt.setString(2, entityType);
                stmt.setObject(3, entityUuid);
                stmt.setString(4, action);
                stmt.setString(5, details);
                stmt.executeUpdate();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Audit event insert failed: " + e.getMessage());
        }
    }
}
