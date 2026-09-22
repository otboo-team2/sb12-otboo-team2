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
    void coldSensitiveUserKeepsOuterLongerWhileLessSensitiveUserExcludesItEarlier() {
        assertThat(filter.isSuitable(27.0, PrecipitationType.NONE, 5, ClothesType.OUTER))
                .isTrue();
        assertThat(filter.isSuitable(27.0, PrecipitationType.NONE, 1, ClothesType.OUTER))
                .isFalse();
    }

    @Test
    void hotThresholdIncludesBoundaryAndSensitivityIsClamped() {
        assertThat(filter.isSuitable(27.0, PrecipitationType.NONE, 1, ClothesType.OUTER))
                .isFalse();
        assertThat(filter.isSuitable(26.99, PrecipitationType.NONE, 1, ClothesType.OUTER))
                .isTrue();
        assertThat(filter.isSuitable(29.0, PrecipitationType.NONE, 99, ClothesType.OUTER))
                .isFalse();
        assertThat(filter.isSuitable(27.0, PrecipitationType.NONE, -99, ClothesType.OUTER))
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
    void missingOrUnknownWarmthDoesNotGuessAnUnsupportedTemperatureRange() {
        assertThat(filter.isSuitable(-20.0, PrecipitationType.NONE, 3, ClothesType.TOP, null))
                .isTrue();
        assertThat(filter.isSuitable(-20.0, PrecipitationType.NONE, 3, ClothesType.TOP, "알 수 없음"))
                .isTrue();
    }

    @Test
    void thinClothesRequireMildWeatherAndRespectBothBoundaries() {
        assertThat(filter.isSuitable(17.99, PrecipitationType.NONE, 3, ClothesType.TOP, "얇음"))
                .isFalse();
        assertThat(filter.isSuitable(18.0, PrecipitationType.NONE, 3, ClothesType.TOP, "얇음"))
                .isTrue();
        assertThat(filter.isSuitable(35.0, PrecipitationType.NONE, 3, ClothesType.TOP, "얇음"))
                .isTrue();
        assertThat(filter.isSuitable(35.01, PrecipitationType.NONE, 3, ClothesType.TOP, "얇음"))
                .isFalse();
    }

    @Test
    void normalWarmthCoversCoolToWarmWeatherIncludingBoundaries() {
        assertThat(filter.isSuitable(9.99, PrecipitationType.NONE, 3, ClothesType.TOP, "보통")).isFalse();
        assertThat(filter.isSuitable(10.0, PrecipitationType.NONE, 3, ClothesType.TOP, "보통")).isTrue();
        assertThat(filter.isSuitable(27.0, PrecipitationType.NONE, 3, ClothesType.TOP, "보통")).isTrue();
        assertThat(filter.isSuitable(27.01, PrecipitationType.NONE, 3, ClothesType.TOP, "보통")).isFalse();
    }

    @Test
    void thickWarmthCoversColdWeatherButNotSeventeenDegreesForColdSensitiveUser() {
        assertThat(filter.isSuitable(-5.01, PrecipitationType.NONE, 3, ClothesType.TOP, "두꺼움")).isFalse();
        assertThat(filter.isSuitable(-5.0, PrecipitationType.NONE, 3, ClothesType.TOP, "두꺼움")).isTrue();
        assertThat(filter.isSuitable(15.0, PrecipitationType.NONE, 3, ClothesType.TOP, "두꺼움")).isTrue();
        assertThat(filter.isSuitable(15.01, PrecipitationType.NONE, 3, ClothesType.TOP, "두꺼움")).isFalse();
        assertThat(filter.isSuitable(17.0, PrecipitationType.NONE, 5, ClothesType.OUTER, "두꺼움"))
                .isFalse();
    }

    @Test
    void veryThickWarmthHasNoArtificialColdMinimumAndExcludesAboveUpperBoundary() {
        assertThat(filter.isSuitable(-30.0, PrecipitationType.NONE, 3, ClothesType.OUTER, "매우 두꺼움"))
                .isTrue();
        assertThat(filter.isSuitable(5.0, PrecipitationType.NONE, 3, ClothesType.OUTER, "매우 두꺼움"))
                .isTrue();
        assertThat(filter.isSuitable(5.01, PrecipitationType.NONE, 3, ClothesType.OUTER, "매우 두꺼움"))
                .isFalse();
    }

    @Test
    void sensitivityOneThreeAndFiveShiftTheSameRangeByPerceivedTemperature() {
        assertThat(filter.isSuitable(17.0, PrecipitationType.NONE, 1, ClothesType.TOP, "얇음")).isTrue();
        assertThat(filter.isSuitable(17.0, PrecipitationType.NONE, 3, ClothesType.TOP, "얇음")).isFalse();
        assertThat(filter.isSuitable(18.0, PrecipitationType.NONE, 5, ClothesType.TOP, "얇음")).isFalse();

        assertThat(filter.isSuitable(16.0, PrecipitationType.NONE, 1, ClothesType.TOP, "두꺼움")).isFalse();
        assertThat(filter.isSuitable(15.0, PrecipitationType.NONE, 3, ClothesType.TOP, "두꺼움")).isTrue();
        assertThat(filter.isSuitable(16.0, PrecipitationType.NONE, 5, ClothesType.TOP, "두꺼움")).isTrue();
    }

    @Test
    void missingTemperatureOrTypeKeepsCandidateWhenSuitabilityCannotBeJudged() {
        assertThat(filter.isSuitable(null, PrecipitationType.NONE, 3, ClothesType.OUTER, "두꺼움"))
                .isTrue();
        assertThat(filter.isSuitable(30.0, PrecipitationType.NONE, 3, null, "두꺼움"))
                .isTrue();
    }
}
