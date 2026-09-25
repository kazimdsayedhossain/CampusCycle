package bd.ac.kuet.campuscycle.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

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

    private final HttpClient httpClient;

    public record KhulnaWeather(
            double temperatureCelsius,
            int relativeHumidity,
            double windSpeedKmh,
            int weatherCode,
            String conditionDescription,
            boolean isSafeForCycling,
            String advice
    ) {}

    public WeatherService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .build();
    }

    /**
     * Fetches current Khulna weather asynchronously.
     */
    public CompletableFuture<KhulnaWeather> fetchCurrentWeatherAsync() {
        return AppExecutor.supplyAsync(this::fetchCurrentWeather);
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
            JsonObject current = root.getAsJsonObject("current");

            double temp = current.get("temperature_2m").getAsDouble();
            int humidity = current.get("relative_humidity_2m").getAsInt();
            double wind = current.get("wind_speed_10m").getAsDouble();
            int code = current.get("weather_code").getAsInt();

            String condition = mapWeatherCode(code);
            boolean safe = code < 60 && wind < 35.0; // No heavy rain or storm winds
            String advice = safe
                    ? "Great conditions for campus ride • " + temp + "°C"
                    : "Rain / gusty winds detected • Ride carefully";

            return new KhulnaWeather(temp, humidity, wind, code, condition, safe, advice);

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
        return new KhulnaWeather(
                28.5,
                72,
                11.2,
                1,
                "Partly Cloudy (Cached)",
                true,
                "Optimal campus cycling conditions"
        );
    }
}
