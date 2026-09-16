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
    void rainDoesNotExcludeWithoutWaterproofAttribute() {
        assertThat(filter.isSuitable(18.0, PrecipitationType.RAIN, 3, ClothesType.SHOES))
                .isTrue();
    }
}
