package com.otboo.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OpenWeatherMapWeatherMapperTest {

    @Test
    void mapsSkyStatus() {
        assertThat(OpenWeatherMapWeatherMapper.toSkyStatus(800, 0)).isEqualTo(SkyStatus.CLEAR);
        assertThat(OpenWeatherMapWeatherMapper.toSkyStatus(803, 60)).isEqualTo(SkyStatus.MOSTLY_CLOUDY);
        assertThat(OpenWeatherMapWeatherMapper.toSkyStatus(804, 90)).isEqualTo(SkyStatus.CLOUDY);
        assertThat(OpenWeatherMapWeatherMapper.toSkyStatus(500, 85)).isEqualTo(SkyStatus.CLOUDY);
    }

    @Test
    void mapsPrecipitationType() {
        assertThat(OpenWeatherMapWeatherMapper.toPrecipitationType(201)).isEqualTo(PrecipitationType.RAIN);
        assertThat(OpenWeatherMapWeatherMapper.toPrecipitationType(521)).isEqualTo(PrecipitationType.SHOWER);
        assertThat(OpenWeatherMapWeatherMapper.toPrecipitationType(615)).isEqualTo(PrecipitationType.RAIN_SNOW);
        assertThat(OpenWeatherMapWeatherMapper.toPrecipitationType(601)).isEqualTo(PrecipitationType.SNOW);
        assertThat(OpenWeatherMapWeatherMapper.toPrecipitationType(800)).isEqualTo(PrecipitationType.NONE);
    }

    @Test
    void mapsWindStrengthBoundaries() {
        assertThat(OpenWeatherMapWeatherMapper.toWindStrength(3.9)).isEqualTo(WindStrength.WEAK);
        assertThat(OpenWeatherMapWeatherMapper.toWindStrength(4)).isEqualTo(WindStrength.MODERATE);
        assertThat(OpenWeatherMapWeatherMapper.toWindStrength(9)).isEqualTo(WindStrength.STRONG);
    }

    @Test
    void rejectsUnknownOrInvalidValues() {
        assertThatThrownBy(() -> OpenWeatherMapWeatherMapper.toSkyStatus(800, 101))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OpenWeatherMapWeatherMapper.toPrecipitationType(999))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OpenWeatherMapWeatherMapper.toWindStrength(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
