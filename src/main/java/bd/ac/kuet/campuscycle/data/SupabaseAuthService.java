package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.config.ClientConfig;
import bd.ac.kuet.campuscycle.domain.CampusUser;
import bd.ac.kuet.campuscycle.domain.Role;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Supabase Auth via HTTPS (password grant). Tokens stay in memory only.
 * Falls back to validated local session when Supabase is not configured.
 */
public final class SupabaseAuthService {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    public record Session(CampusUser user, String accessToken) {}

    private SupabaseAuthService() {}

    public static boolean isLiveConfigured() {
        try {
            return ClientConfig.isSupabaseConfigured();
        } catch (Exception e) {
            return false;
        }
    }

    public static CompletableFuture<Session> signIn(String emailOrRoll, String password) {
        return CompletableFuture.supplyAsync(() -> {
            String email = emailOrRoll.trim().toLowerCase();
            validate(email, password);
            if (!isLiveConfigured()) {
                return localSession(email);
            }
            try {
                String url = ClientConfig.supabaseUrl();
                String key = ClientConfig.supabasePublishableKey();
                JsonObject body = new JsonObject();
                body.addProperty("email", email);
                body.addProperty("password", password);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/auth/v1/token?grant_type=password"))
                        .timeout(Duration.ofSeconds(8))
                        .header("apikey", key)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();
                HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) {
                    throw new IllegalArgumentException("Invalid credentials or unverified account.");
                }
                JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
                String token = root.has("access_token") ? root.get("access_token").getAsString() : "";
                String uid = root.has("user") ? root.getAsJsonObject("user").get("id").getAsString()
                        : UUID.nameUUIDFromBytes(email.getBytes()).toString();
                CampusUser user = new CampusUser(uid, email.split("@")[0], email, Role.STUDENT);
                return new Session(user, token);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Sign-in service unavailable. Please retry.");
            }
        });
    }

    public static void validate(String email, String password) {
        if (!email.matches("^[a-z0-9._%+-]+@kuet\\.ac\\.bd$") && !email.matches("^\\d{7}$")) {
            throw new IllegalArgumentException("Use your KUET email (@kuet.ac.bd) or 7-digit roll.");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters.");
        }
    }

    private static Session localSession(String email) {
        String uid = UUID.nameUUIDFromBytes(email.getBytes()).toString();
        String name = email.contains("@") ? email.split("@")[0] : "Student " + email;
        CampusUser user = new CampusUser(uid, name, email, Role.STUDENT);
        LocalDatabase.getInstance().saveProfile(user);
        return new Session(user, "");
    }
}
