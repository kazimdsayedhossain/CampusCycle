package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.config.ClientConfig;
import bd.ac.kuet.campuscycle.domain.AppError;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Minimal PostgREST RPC client. Publishable key only, user JWT for auth. */
public final class SupabaseRpcClient {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();
    private static final Gson GSON = new Gson();

    private SupabaseRpcClient() {}

    public static boolean isConfigured() {
        try {
            return ClientConfig.isSupabaseConfigured();
        } catch (Exception e) {
            return false;
        }
    }

    public static List<Map<String, Object>> call(String function, Map<String, Object> params) {
        if (!isConfigured()) {
            throw new AppError("OFFLINE", "Live store is not configured.");
        }
        try {
            String base = ClientConfig.supabaseUrl();
            String key = ClientConfig.supabasePublishableKey();
            String body = params == null ? "{}" : GSON.toJson(params);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/rest/v1/rpc/" + function))
                    .timeout(Duration.ofSeconds(8))
                    .header("apikey", key)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (SessionStore.hasSession()) {
                builder.header("Authorization", "Bearer " + SessionStore.token());
            } else {
                builder.header("Authorization", "Bearer " + key);
            }
            HttpResponse<String> res = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                throw new AppError("RPC_FAILED", "Request failed. Please retry.");
            }
            String json = res.body() == null || res.body().isBlank() ? "[]" : res.body().trim();
            if (json.startsWith("{")) json = "[" + json + "]";
            Type type = new TypeToken<List<Map<String, Object>>>() {}.getType();
            List<Map<String, Object>> out = GSON.fromJson(json, type);
            return out == null ? List.of() : out;
        } catch (AppError e) {
            throw e;
        } catch (Exception e) {
            throw new AppError("RPC_FAILED", "Request failed. Please retry.", e);
        }
    }

    public static String str(Map<String, Object> row, String key, String fallback) {
        Object v = row.get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    public static int num(Map<String, Object> row, String key, int fallback) {
        try {
            Object v = row.get(key);
            if (v instanceof Number n) return n.intValue();
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }

    public static double dbl(Map<String, Object> row, String key, double fallback) {
        try {
            Object v = row.get(key);
            if (v instanceof Number n) return n.doubleValue();
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return fallback;
        }
    }
}
