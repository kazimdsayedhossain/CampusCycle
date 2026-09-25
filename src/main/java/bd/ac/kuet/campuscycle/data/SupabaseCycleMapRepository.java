package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.config.ClientConfig;
import bd.ac.kuet.campuscycle.domain.CycleMapPoint;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Reads the deliberately scoped public map RPC; owner contact information never crosses this boundary. */
public final class SupabaseCycleMapRepository implements CycleMapRepository {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public List<CycleMapPoint> availableCycleLocations() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(ClientConfig.supabaseUrl() + "/rest/v1/rpc/map_cycle_locations"))
                    .timeout(Duration.ofSeconds(15))
                    .header("apikey", ClientConfig.supabasePublishableKey())
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Campus cycle locations are currently unavailable.");
            }
            JsonArray rows = JsonParser.parseString(response.body()).getAsJsonArray();
            List<CycleMapPoint> result = new ArrayList<>();
            for (JsonElement element : rows) {
                JsonObject row = element.getAsJsonObject();
                result.add(new CycleMapPoint(
                        text(row, "cycle_id"), text(row, "label"), text(row, "pickup_point"),
                        row.get("latitude").getAsDouble(), row.get("longitude").getAsDouble()));
            }
            return List.copyOf(result);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Campus cycle locations are currently unavailable.");
        }
    }

    private static String text(JsonObject object, String property) {
        return object.get(property).getAsString();
    }
}
