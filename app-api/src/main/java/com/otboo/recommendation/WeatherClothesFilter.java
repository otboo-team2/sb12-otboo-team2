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
        if (currentTemperature == null || type == null) {
            return true;
        }

        // 온도 민감도가 높을수록 더 낮은 기온에서도 더위를 느끼는 것으로 보정한다.
        int sensitivity = temperatureSensitivity == null
                ? 3 : Math.clamp(temperatureSensitivity, 1, 5);
        double hotThreshold = HOT_TEMPERATURE - (sensitivity - 3) * 0.5;
        if (currentTemperature >= hotThreshold
                && (type == ClothesType.OUTER || type == ClothesType.SCARF)) {
            return false;
        }

        return true;
    }
}
