package bd.ac.kuet.campuscycle.data;

/** In-memory session only. Tokens never touch disk. Cleared on sign-out. */
public final class SessionStore {
    private static volatile String accessToken = "";
    private static volatile String userId = "";

    private SessionStore() {}

    public static void set(String token, String uid) {
        accessToken = token == null ? "" : token;
        userId = uid == null ? "" : uid;
    }

    public static void clear() {
        accessToken = "";
        userId = "";
    }

    public static String token() {
        return accessToken;
    }

    public static String userId() {
        return userId;
    }

    public static boolean hasSession() {
        return !accessToken.isBlank();
    }
}
