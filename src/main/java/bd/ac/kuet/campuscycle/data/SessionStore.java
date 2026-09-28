package bd.ac.kuet.campuscycle.data;

/** In-memory session only. Tokens never touch disk. Cleared on sign-out. */
public final class SessionStore {
    /** Session time-to-live: 12 hours from issuance. */
    public static final long TTL_MS = 12L * 60 * 60 * 1000;

    private static volatile String accessToken = "";
    private static volatile String refreshToken = "";
    private static volatile String userId = "";
    private static volatile long issuedAtMs = 0L;

    private SessionStore() {}

    /**
     * Stores the real token pair issued at sign-in. Existing callers pass the live
     * access token (never a placeholder literal); the refresh token enables renewal.
     */
    public static synchronized void set(String token, String uid) {
        set(token, "", uid);
    }

    /** Stores the real access + refresh token pair and (re)starts the 12h TTL clock. */
    public static synchronized void set(String token, String refresh, String uid) {
        accessToken = token == null ? "" : token;
        refreshToken = refresh == null ? "" : refresh;
        userId = uid == null ? "" : uid;
        issuedAtMs = System.currentTimeMillis();
    }

    public static synchronized void clear() {
        accessToken = "";
        refreshToken = "";
        userId = "";
        issuedAtMs = 0L;
    }

    public static String token() {
        return accessToken;
    }

    public static String refreshToken() {
        return refreshToken;
    }

    public static String userId() {
        return userId;
    }

    /** Epoch millis when the current pair was stored, or 0 when no session was ever set. */
    public static long issuedAtMillis() {
        return issuedAtMs;
    }

    /** True when there is no token or the 12h TTL has elapsed. */
    public static boolean isExpired() {
        if (accessToken.isBlank()) {
            return true;
        }
        if (issuedAtMs <= 0L) {
            return true;
        }
        return System.currentTimeMillis() - issuedAtMs >= TTL_MS;
    }

    public static boolean hasSession() {
        return !accessToken.isBlank() && !isExpired();
    }
}
