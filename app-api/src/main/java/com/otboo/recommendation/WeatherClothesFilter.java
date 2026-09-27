package com.otboo.recommendation;

import com.otboo.clothes.entity.ClothesType;
import com.otboo.weather.PrecipitationType;
import com.otboo.weather.dto.WeatherDto;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 현재 데이터로 안전하게 판단할 수 있는 날씨 기반 의상 제외 규칙. */
@Component
public class WeatherClothesFilter {

    private static final double HOT_TEMPERATURE = 28.0;
    private static final Map<String, TemperatureRange> WARMTH_RANGES = Map.of(
            "얇음", new TemperatureRange(18.0, 35.0),
            "보통", new TemperatureRange(10.0, 27.0),
            "두꺼움", new TemperatureRange(-5.0, 15.0),
            "매우 두꺼움", new TemperatureRange(Double.NEGATIVE_INFINITY, 5.0));

    public boolean isSuitable(WeatherDto weather, Integer temperatureSensitivity, ClothesType type) {
        if (weather == null) {
            return true;
        }
        Double current = weather.temperature() == null ? null : weather.temperature().current();
        PrecipitationType precipitation = weather.precipitation() == null
                ? null : weather.precipitation().type();
        return isSuitable(current, precipitation, temperatureSensitivity, type);
    }

    boolean isSuitable(
            Double currentTemperature,
            PrecipitationType precipitation,
            Integer temperatureSensitivity,
            ClothesType type) {
        return isSuitable(currentTemperature, precipitation, temperatureSensitivity, type, null);
    }

    boolean isSuitable(
            Double currentTemperature,
            PrecipitationType precipitation,
            Integer temperatureSensitivity,
            ClothesType type,
            String warmth) {
        if (currentTemperature == null || type == null) {
            return true;
        }

        // 온도 민감도가 높을수록 같은 기온을 더 춥게 느끼는 것으로 보정한다.
        int sensitivity = temperatureSensitivity == null
                ? 3 : Math.clamp(temperatureSensitivity, 1, 5);
        double sensitivityOffset = (sensitivity - 3) * 0.5;
        double perceivedTemperature = currentTemperature - sensitivityOffset;
        if (perceivedTemperature >= HOT_TEMPERATURE
                && (type == ClothesType.OUTER || type == ClothesType.SCARF)) {
            return false;
        }

        TemperatureRange range = warmth == null ? null : WARMTH_RANGES.get(warmth);
        if (range != null && !range.includes(perceivedTemperature)) {
            return false;
        }

        return true;
    }

    double suitabilityDistance(Double currentTemperature, Integer temperatureSensitivity, String warmth) {
        if (currentTemperature == null || warmth == null) {
            return 0.0;
        }
        int sensitivity = temperatureSensitivity == null
                ? 3 : Math.clamp(temperatureSensitivity, 1, 5);
        double perceivedTemperature = currentTemperature - (sensitivity - 3) * 0.5;
        TemperatureRange range = WARMTH_RANGES.get(warmth);
        if (range == null) {
            return 0.0;
        }
        return Math.abs(perceivedTemperature - range.midpoint());
    }

    private record TemperatureRange(double min, double max) {

        private boolean includes(double temperature) {
            return temperature >= min && temperature <= max;
        }

        private double midpoint() {
            if (Double.isInfinite(min)) {
                return max - 10.0;
            }
            return (min + max) / 2.0;
        }
    }
}
