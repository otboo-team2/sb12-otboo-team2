package com.otboo.recommendation;

import com.otboo.clothes.entity.ClothesType;
import com.otboo.weather.PrecipitationType;
import com.otboo.weather.dto.WeatherDto;
import org.springframework.stereotype.Component;

/** 현재 데이터로 안전하게 판단할 수 있는 날씨 기반 의상 제외 규칙. */
@Component
public class WeatherClothesFilter {

    private static final double HOT_TEMPERATURE = 28.0;

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

        // 온도 민감도가 높을수록 더 낮은 기온에서도 더위를 느끼는 것으로 보정한다.
        int sensitivity = temperatureSensitivity == null
                ? 3 : Math.clamp(temperatureSensitivity, 1, 5);
        double sensitivityOffset = (sensitivity - 3) * 0.5;
        double hotThreshold = HOT_TEMPERATURE - sensitivityOffset;
        if (currentTemperature >= hotThreshold
                && (type == ClothesType.OUTER || type == ClothesType.SCARF)) {
            return false;
        }

        double warmthMaxTemperature = warmth == null ? Double.POSITIVE_INFINITY : switch (warmth) {
            case "얇음" -> 35.0;
            case "보통" -> 28.0;
            case "두꺼움" -> 22.0;
            case "매우 두꺼움" -> 16.0;
            default -> Double.POSITIVE_INFINITY;
        };
        // 기존 더위 기준과 같은 방향으로 민감도를 보온성 기준에도 반영한다.
        double adjustedWarmthMaxTemperature = warmthMaxTemperature - sensitivityOffset;
        if (currentTemperature > adjustedWarmthMaxTemperature) {
            return false;
        }

        return true;
    }
}
