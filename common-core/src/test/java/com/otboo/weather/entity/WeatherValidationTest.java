package com.otboo.weather.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.common.exception.BusinessException;
import com.otboo.weather.PrecipitationType;
import com.otboo.weather.SkyStatus;
import com.otboo.weather.WindStrength;
import com.otboo.weather.exception.WeatherErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class WeatherValidationTest {

    @Test
    void rejectsInvalidWeatherValuesWithDomainCode() {
        List<Consumer<Weather.WeatherBuilder>> invalid = List.of(
                b -> b.forecastedAt(null), b -> b.forecastAt(null),
                b -> b.skyStatus(null), b -> b.precipitationType(null), b -> b.windSpeedAsWord(null),
                b -> b.precipitationAmount(null), b -> b.precipitationProbability(null),
                b -> b.humidityCurrent(null), b -> b.temperatureCurrent(null),
                b -> b.temperatureMin(null), b -> b.temperatureMax(null), b -> b.windSpeed(null),
                b -> b.windSpeed(new BigDecimal("-1")),
                b -> b.precipitationAmount(new BigDecimal("-1")),
                b -> b.precipitationProbability(new BigDecimal("101")),
                b -> b.humidityCurrent(new BigDecimal("-1")),
                b -> b.humidityCurrent(new BigDecimal("101")),
                b -> b.temperatureMin(new BigDecimal("21")),
                b -> b.temperatureCurrent(new BigDecimal("1000")),
                b -> b.windSpeed(new BigDecimal("0.001")),
                b -> b.humidityComparedToDayBefore(new BigDecimal("101")),
                b -> b.temperatureComparedToDayBefore(new BigDecimal("1000")));
        for (var change : invalid) {
            var builder = validWeather();
            change.accept(builder);
            assertThatThrownBy(builder::build).isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(WeatherErrorCode.INVALID_WEATHER);
        }
    }

    @Test
    void permitsNegativeTemperaturesAndOptionalComparisons() {
        var weather = validWeather().temperatureCurrent(new BigDecimal("-2.50"))
                .humidityComparedToDayBefore(new BigDecimal("-10"))
                .precipitationProbability(new BigDecimal("100.000")).build();
        assertThat(weather.getTemperatureCurrent()).isEqualByComparingTo("-2.50");
        assertThat(weather.getTemperatureComparedToDayBefore()).isNull();
    }

    @Test
    void bothEntitiesRejectUnsupportedGrid() {
        assertThatThrownBy(() -> validWeather().gridX(0).build())
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(WeatherErrorCode.UNSUPPORTED_LOCATION);
        assertThatThrownBy(() -> WeatherRegion.create(60, 254, List.of("서울")))
                .isInstanceOf(BusinessException.class).extracting("errorCode")
                .isEqualTo(WeatherErrorCode.UNSUPPORTED_LOCATION);
    }

    @Test
    void rejectsMissingOrBlankRegionNames() {
        for (List<String> names : Arrays.<List<String>>asList(
                null, List.of(), List.of(" "), Arrays.asList("서울", null))) {
            assertThatThrownBy(() -> WeatherRegion.create(60, 127, names))
                    .isInstanceOf(BusinessException.class).extracting("errorCode")
                    .isEqualTo(WeatherErrorCode.INVALID_LOCATION_NAMES);
        }
    }

    @Test
    void regionNamesCannotBeChangedThroughCallerList() {
        var names = new ArrayList<>(List.of("서울"));
        var region = WeatherRegion.create(60, 127, names);
        names.clear();
        assertThat(region.getLocationNames()).containsExactly("서울");
        assertThatThrownBy(() -> region.getLocationNames().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private Weather.WeatherBuilder validWeather() {
        return Weather.builder().gridX(60).gridY(127)
                .forecastedAt(Instant.parse("2026-09-07T00:00:00Z"))
                .forecastAt(Instant.parse("2026-09-07T03:00:00Z"))
                .skyStatus(SkyStatus.CLEAR).precipitationType(PrecipitationType.NONE)
                .precipitationAmount(BigDecimal.ZERO).precipitationProbability(BigDecimal.ZERO)
                .humidityCurrent(new BigDecimal("50"))
                .temperatureCurrent(BigDecimal.TEN).temperatureMin(new BigDecimal("-10"))
                .temperatureMax(new BigDecimal("20"))
                .windSpeed(BigDecimal.ZERO).windSpeedAsWord(WindStrength.WEAK);
    }
}
