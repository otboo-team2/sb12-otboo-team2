package com.otboo.recommendation;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.clothes.entity.ClothesType;
import com.otboo.weather.PrecipitationType;
import org.junit.jupiter.api.Test;

class WeatherClothesFilterTest {

    private final WeatherClothesFilter filter = new WeatherClothesFilter();

    @Test
    void hotWeatherExcludesOuterAndScarf() {
        assertThat(filter.isSuitable(30.0, PrecipitationType.NONE, 3, ClothesType.OUTER))
                .isFalse();
        assertThat(filter.isSuitable(30.0, PrecipitationType.NONE, 3, ClothesType.SCARF))
                .isFalse();
        assertThat(filter.isSuitable(30.0, PrecipitationType.NONE, 3, ClothesType.TOP))
                .isTrue();
    }

    @Test
    void heatSensitiveUserGetsLowerHotThreshold() {
        assertThat(filter.isSuitable(27.0, PrecipitationType.NONE, 5, ClothesType.OUTER))
                .isFalse();
        assertThat(filter.isSuitable(27.0, PrecipitationType.NONE, 1, ClothesType.OUTER))
                .isTrue();
    }

    @Test
    void hotThresholdIncludesBoundaryAndSensitivityIsClamped() {
        assertThat(filter.isSuitable(29.0, PrecipitationType.NONE, 1, ClothesType.OUTER))
                .isFalse();
        assertThat(filter.isSuitable(28.99, PrecipitationType.NONE, 1, ClothesType.OUTER))
                .isTrue();
        assertThat(filter.isSuitable(27.0, PrecipitationType.NONE, 99, ClothesType.OUTER))
                .isFalse();
        assertThat(filter.isSuitable(29.0, PrecipitationType.NONE, -99, ClothesType.OUTER))
                .isFalse();
    }

    @Test
    void rainDoesNotExcludeWithoutWaterproofAttribute() {
        assertThat(filter.isSuitable(18.0, PrecipitationType.RAIN, 3, ClothesType.SHOES))
                .isTrue();
    }

    @Test
    void precipitationTypesDoNotFilterWithoutWaterproofAttribute() {
        for (PrecipitationType precipitation : PrecipitationType.values()) {
            assertThat(filter.isSuitable(18.0, precipitation, 3, ClothesType.TOP))
                    .as("precipitation=%s", precipitation)
                    .isTrue();
        }
    }

    @Test
    void warmthExcludesThickClothesInWarmWeather() {
        assertThat(filter.isSuitable(23.0, PrecipitationType.NONE, 3, ClothesType.OUTER, "매우 두꺼움"))
                .isFalse();
        assertThat(filter.isSuitable(23.0, PrecipitationType.NONE, 3, ClothesType.TOP, "얇음"))
                .isTrue();
    }

    @Test
    void warmthThresholdFollowsTemperatureSensitivity() {
        assertThat(filter.isSuitable(23.0, PrecipitationType.NONE, 1, ClothesType.TOP, "두꺼움"))
                .isTrue();
        assertThat(filter.isSuitable(23.0, PrecipitationType.NONE, 3, ClothesType.TOP, "두꺼움"))
                .isFalse();
        assertThat(filter.isSuitable(21.0, PrecipitationType.NONE, 5, ClothesType.TOP, "두꺼움"))
                .isTrue();
        assertThat(filter.isSuitable(21.1, PrecipitationType.NONE, 5, ClothesType.TOP, "두꺼움"))
                .isFalse();
    }

    @Test
    void warmthThresholdAllowsExactBoundary() {
        assertThat(filter.isSuitable(22.0, PrecipitationType.NONE, 3, ClothesType.TOP, "두꺼움"))
                .isTrue();
        assertThat(filter.isSuitable(22.01, PrecipitationType.NONE, 3, ClothesType.TOP, "두꺼움"))
                .isFalse();
        assertThat(filter.isSuitable(17.0, PrecipitationType.NONE, 1, ClothesType.TOP, "매우 두꺼움"))
                .isTrue();
        assertThat(filter.isSuitable(17.01, PrecipitationType.NONE, 1, ClothesType.TOP, "매우 두꺼움"))
                .isFalse();
    }
}
