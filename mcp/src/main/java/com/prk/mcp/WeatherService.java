package com.prk.mcp;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class WeatherService {
    private final RestTemplate rest = new RestTemplate();

    @Tool(description = "Get today's weather for a specified city")
    public Map<String,Object> getTodayWeather(String city) {
        // 1. Geocode city name
        double[] coords = geocodeCity(city);
        if (coords == null) {
            return Map.of("error", "City not found");
        }
        double lat = coords[0], lon = coords[1];

        // 2. Call Open-Meteo forecast API for current weather
        String url = String.format(
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weathercode&timezone=UTC",
            lat, lon);
        Map<?,?> resp = rest.getForObject(url, Map.class);
        if (resp == null || resp.get("current_weather")==null) {
            return Map.of("error", "Weather data unavailable");
        }
        Map<?,?> current = (Map<?,?>) resp.get("current_weather");
        return Map.of(
            "city", city,
            "latitude", lat,
            "longitude", lon,
            "time", current.get("time"),
            "temperature", current.get("temperature"),
            "weatherCode", current.get("weathercode")
        );
    }

    @Tool(description = "Get a 7-day weather forecast for a specified city")
    public Map<String,Object> get7DayForecast(String city) {
        double[] coords = geocodeCity(city);
        if (coords == null) {
            return Map.of("error", "City not found");
        }
        double lat = coords[0], lon = coords[1];
        // Call Open-Meteo for daily forecast
        String url = String.format(
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
            "&daily=weathercode,temperature_2m_max,temperature_2m_min&timezone=UTC",
            lat, lon);
        Map<?,?> resp = rest.getForObject(url, Map.class);
        Map<?,?> daily = (Map<?,?>) resp.get("daily");
        if (daily == null) {
            return Map.of("error", "Forecast data unavailable");
        }
        List<?> dates = (List<?>) daily.get("time");
        List<?> codes = (List<?>) daily.get("weathercode");
        List<?> tmax  = (List<?>) daily.get("temperature_2m_max");
        List<?> tmin  = (List<?>) daily.get("temperature_2m_min");
        List<Map<String,Object>> forecastList = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            forecastList.add(Map.of(
                "date", dates.get(i),
                "weatherCode", codes.get(i),
                "tempMax", tmax.get(i),
                "tempMin", tmin.get(i)
            ));
        }
        return Map.of(
            "city", city,
            "latitude", lat,
            "longitude", lon,
            "forecast", forecastList
        );
    }

    @Tool(description = "Get weather data for a past day for a specified city (format YYYY-MM-DD)")
    public Map<String,Object> getPastWeather(String city, String date) {
        double[] coords = geocodeCity(city);
        if (coords == null) {
            return Map.of("error", "City not found");
        }
        double lat = coords[0], lon = coords[1];
        // Call Open-Meteo historical archive for that date
        String url = String.format(
            "https://archive-api.open-meteo.com/v1/archive?latitude=%.4f&longitude=%.4f" +
            "&start_date=%s&end_date=%s" +
            "&daily=weathercode,temperature_2m_max,temperature_2m_min&timezone=UTC",
            lat, lon, date, date);
        Map<?,?> resp = rest.getForObject(url, Map.class);
        Map<?,?> daily = (Map<?,?>) resp.get("daily");
        if (daily == null) {
            return Map.of("error", "Historical data not available");
        }
        List<?> dates = (List<?>) daily.get("time");
        if (dates.isEmpty()) {
            return Map.of("error", "No data for given date");
        }
        // We expect one entry (same start and end date)
        return Map.of(
            "city", city,
            "latitude", lat,
            "longitude", lon,
            "date", dates.get(0),
            "weatherCode", ((List<?>)daily.get("weathercode")).get(0),
            "tempMax", ((List<?>)daily.get("temperature_2m_max")).get(0),
            "tempMin", ((List<?>)daily.get("temperature_2m_min")).get(0)
        );
    }

    // Helper: call Open-Meteo geocoding API
    private double[] geocodeCity(String city) {
        try {
            String url = "https://geocoding-api.open-meteo.com/v1/search?name="
                + URLEncoder.encode(city, StandardCharsets.UTF_8)
                + "&language=en&count=1";
            Map<?,?> geo = rest.getForObject(url, Map.class);
            List<?> results = (List<?>) geo.get("results");
            if (results == null || results.isEmpty()) {
                return null;
            }
            Map<?,?> first = (Map<?,?>) results.get(0);
            return new double[]{ 
                ((Number) first.get("latitude")).doubleValue(),
                ((Number) first.get("longitude")).doubleValue() 
            };
        } catch (Exception e) {
            return null;
        }
    }
}
