package bd.ac.kuet.campuscycle.data;

import bd.ac.kuet.campuscycle.config.ClientConfig;
import bd.ac.kuet.campuscycle.domain.CycleMapPoint;
import bd.ac.kuet.campuscycle.domain.CycleRoute;
import bd.ac.kuet.campuscycle.domain.RoutePoint;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** Calls FreeRoute's ORS-compatible cycling directions endpoint. */
public final class FreeRouteDirectionsRepository implements CycleDirectionsRepository {
    private static final URI DIRECTIONS_ENDPOINT = URI.create("https://api.maps.freeroute.org/v1/directions/cycling-regular");
    private final HttpClient client;

    public FreeRouteDirectionsRepository() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    FreeRouteDirectionsRepository(HttpClient client) {
        this.client = client;
    }

    @Override
    public CycleRoute cyclingRoute(RoutePoint origin, CycleMapPoint destination) {
        JsonArray coordinates = new JsonArray();
        coordinates.add(coordinate(origin.longitude(), origin.latitude()));
        coordinates.add(coordinate(destination.longitude(), destination.latitude()));
        JsonObject payload = new JsonObject();
        payload.add("coordinates", coordinates);
        payload.addProperty("instructions", false);
        HttpRequest request = HttpRequest.newBuilder(DIRECTIONS_ENDPOINT)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + ClientConfig.freeRouteApiKey())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Cycling directions are currently unavailable.");
            }
            return parseRoute(destination.cycleId(), response.body());
        } catch (IOException exception) {
            throw new IllegalStateException("Cycling directions are currently unavailable.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cycling directions request was interrupted.", exception);
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalStateException) throw exception;
            throw new IllegalStateException("Cycling directions returned an invalid response.", exception);
        }
    }

    static CycleRoute parseRoute(String cycleId, String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray routes = root.getAsJsonArray("routes");
        if (routes == null || routes.isEmpty()) throw new IllegalArgumentException("No route returned");
        JsonObject route = routes.get(0).getAsJsonObject();
        List<RoutePoint> points = PolylineDecoder.decode(route.get("geometry").getAsString());
        JsonObject summary = route.getAsJsonObject("summary");
        return new CycleRoute(cycleId, points, Math.round(summary.get("distance").getAsFloat()), Math.round(summary.get("duration").getAsFloat()));
    }

    private static JsonArray coordinate(double longitude, double latitude) {
        JsonArray coordinate = new JsonArray();
        coordinate.add(longitude);
        coordinate.add(latitude);
        return coordinate;
    }
}
