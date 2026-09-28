package bd.ac.kuet.campuscycle.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Live external weather service demonstrating:
 * 1. HTTP networking with java.net.http.HttpClient
 * 2. Asynchronous REST requests
 * 3. JSON parsing with Google Gson
 * 4. Real cycling safety evaluation for Khulna & KUET campus
 */
public class WeatherService {

    // KUET, Khulna coordinates: 22.84N, 89.54E
    private static final String WEATHER_API_URL =
            "https://api.open-meteo.com/v1/forecast?latitude=22.84&longitude=89.54&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m";

    private static final java.net.http.HttpClient SHARED_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();
    private static final Logger LOGGER = Logger.getLogger(WeatherService.class.getName());

    private static volatile KhulnaWeather cached;
    private static volatile long cachedAt = 0;

    private final HttpClient httpClient = SHARED_CLIENT;

    /**
     * @param live   true when freshly fetched from the API; false for the
     *               offline fallback (P-062). Callers must render "--°C" +
     *               "unavailable" when {@code live == false}.
     * @param source where the reading came from ("open-meteo" or "fallback").
     */
    public record KhulnaWeather(
            double temperatureCelsius,
            int relativeHumidity,
            double windSpeedKmh,
            int weatherCode,
            String conditionDescription,
            boolean isSafeForCycling,
            String advice,
            boolean live,
            String source
    ) {}

    public WeatherService() {
    }

    /**
     * Fetches current Khulna weather asynchronously with 5-min cache.
     * Fallbacks are never cached; only live readings populate the cache.
     */
    public CompletableFuture<KhulnaWeather> fetchCurrentWeatherAsync() {
        KhulnaWeather hit = cached;
        if (hit != null && hit.live() && System.currentTimeMillis() - cachedAt < 300_000) {
            return CompletableFuture.completedFuture(hit);
        }
        return AppExecutor.supplyAsync(() -> {
            KhulnaWeather fresh = this.fetchCurrentWeather();
            if (fresh.live()) {
                cached = fresh;
                cachedAt = System.currentTimeMillis();
            }
            return fresh;
        });
    }

    /**
     * Synchronously fetches and parses weather JSON from the API.
     */
    public KhulnaWeather fetchCurrentWeather() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(WEATHER_API_URL))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                return fallbackWeather("API status code: " + response.statusCode());
            }

            // Parse JSON using Gson
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (root == null || !root.has("current") || !root.get("current").isJsonObject()) {
                return fallbackWeather("Missing current node in payload");
            }
            JsonObject current = root.getAsJsonObject("current");

            double temp = (current.has("temperature_2m") && !current.get("temperature_2m").isJsonNull())
                    ? current.get("temperature_2m").getAsDouble() : 28.5;
            int humidity = (current.has("relative_humidity_2m") && !current.get("relative_humidity_2m").isJsonNull())
                    ? current.get("relative_humidity_2m").getAsInt() : 72;
            double wind = (current.has("wind_speed_10m") && !current.get("wind_speed_10m").isJsonNull())
                    ? current.get("wind_speed_10m").getAsDouble() : 11.2;
            int code = (current.has("weather_code") && !current.get("weather_code").isJsonNull())
                    ? current.get("weather_code").getAsInt() : 1;

            String condition = mapWeatherCode(code);
            boolean safe = code < 60 && wind < 35.0; // No heavy rain or storm winds
            String advice = safe
                    ? "Great conditions for campus ride • " + temp + "°C"
                    : "Rain / gusty winds detected • Ride carefully";

            return new KhulnaWeather(temp, humidity, wind, code, condition, safe, advice, true, "open-meteo");

        } catch (Exception e) {
            return fallbackWeather(e.getMessage());
        }
    }

    private String mapWeatherCode(int code) {
        return switch (code) {
            case 0 -> "Clear Sky";
            case 1, 2, 3 -> "Partly Cloudy";
            case 45, 48 -> "Foggy";
            case 51, 53, 55 -> "Light Drizzle";
            case 61, 63, 65 -> "Rain";
            case 71, 73, 75 -> "Snow";
            case 80, 81, 82 -> "Rain Showers";
            case 95, 96, 99 -> "Thunderstorm";
            default -> "Overcast";
        };
    }

    private KhulnaWeather fallbackWeather(String reason) {
        LOGGER.log(Level.WARNING, "Weather unavailable, returning offline fallback. Reason: {0}", reason);
        return new KhulnaWeather(
                28.5,
                72,
                11.2,
                1,
                "Unavailable",
                true,
                "Live weather unavailable — check your connection",
                false,
                "fallback"
        );
    }
}
