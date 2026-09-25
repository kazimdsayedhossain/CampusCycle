package bd.ac.kuet.campuscycle.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class WeatherServiceTest {

    @Test
    void testLiveWeatherFetchAndJsonParsing() {
        WeatherService weatherService = new WeatherService();
        WeatherService.KhulnaWeather weather = weatherService.fetchCurrentWeather();

        assertNotNull(weather, "Weather object should not be null");
        assertNotNull(weather.conditionDescription(), "Condition description should not be null");
        assertNotNull(weather.advice(), "Advice should not be null");
        assertTrue(weather.temperatureCelsius() > -10 && weather.temperatureCelsius() < 60, "Temperature should be realistic");

        System.out.printf("Live Khulna Weather: %.1f°C, %d%% humidity, Wind: %.1f km/h, Condition: %s, Advice: %s%n",
                weather.temperatureCelsius(),
                weather.relativeHumidity(),
                weather.windSpeedKmh(),
                weather.conditionDescription(),
                weather.advice());
    }
}
