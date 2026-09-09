package com.otboo.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.otboo.common.exception.BusinessException;
import com.otboo.weather.entity.Weather;
import com.otboo.weather.exception.WeatherErrorCode;
import com.otboo.weather.repository.WeatherRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OpenWeatherMapWeatherServiceTest {

    @Test
    void convertsAndSavesForecastAndConvertsProbabilityToPercent() {
        var client = mock(OpenWeatherMapClient.class);
        var repository = mock(WeatherRepository.class);
        when(repository.findByGridXAndGridYAndForecastedAtAndForecastAt(
                anyInt(), anyInt(), any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(Weather.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var service = new OpenWeatherMapWeatherService(client, repository);
        var result = service.saveForecast(60, 127, Instant.parse("2026-09-08T00:00:00Z"),
                new OpenWeatherMapForecast("200", List.of(entry(0.35))));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getPrecipitationProbability()).isEqualByComparingTo("35.00");
        assertThat(result.getFirst().getPrecipitationAmount()).isEqualByComparingTo("1.20");
        assertThat(result.getFirst().getPrecipitationType()).isEqualTo(PrecipitationType.RAIN);
        verify(repository).save(any(Weather.class));
    }

    @Test
    void skipsForecastAlreadyStored() {
        var repository = mock(WeatherRepository.class);
        when(repository.findByGridXAndGridYAndForecastedAtAndForecastAt(
                anyInt(), anyInt(), any(), any())).thenReturn(Optional.of(mock(Weather.class)));

        var service = new OpenWeatherMapWeatherService(mock(OpenWeatherMapClient.class), repository);
        var result = service.saveForecast(60, 127, Instant.now(),
                new OpenWeatherMapForecast("200", List.of(entry(0))));

        assertThat(result).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void roundsWindSpeedBeforeCalculatingStrength() {
        var repository = mock(WeatherRepository.class);
        when(repository.findByGridXAndGridYAndForecastedAtAndForecastAt(
                anyInt(), anyInt(), any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(Weather.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = new OpenWeatherMapWeatherService(mock(OpenWeatherMapClient.class), repository)
                .saveForecast(60, 127, Instant.now(),
                        new OpenWeatherMapForecast("200", List.of(entry(0, "3.999"))));

        assertThat(result.getFirst().getWindSpeed()).isEqualByComparingTo("4.00");
        assertThat(result.getFirst().getWindSpeedAsWord()).isEqualTo(WindStrength.MODERATE);
    }

    @Test
    void rejectsMismatchedCoordinates() {
        var client = mock(OpenWeatherMapClient.class);
        var service = new OpenWeatherMapWeatherService(client, mock(WeatherRepository.class));

        assertThatThrownBy(() -> service.fetchAndSave(37.5665, 126.978, 1, 1))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(WeatherErrorCode.UNSUPPORTED_LOCATION);
        verifyNoInteractions(client);
    }

    @Test
    void truncatesCollectionTimeToHour() {
        var client = mock(OpenWeatherMapClient.class);
        var repository = mock(WeatherRepository.class);
        when(client.fetch(anyDouble(), anyDouble()))
                .thenReturn(new OpenWeatherMapForecast("200", List.of(entry(0))));
        when(repository.findByGridXAndGridYAndForecastedAtAndForecastAt(
                anyInt(), anyInt(), any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(Weather.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = new OpenWeatherMapWeatherService(client, repository)
                .fetchAndSave(37.5665, 126.978, 60, 127);

        assertThat(result.getFirst().getForecastedAt().getEpochSecond() % 3600).isZero();
    }

    private OpenWeatherMapForecast.Entry entry(double probability) {
        return entry(probability, "3.12");
    }

    private OpenWeatherMapForecast.Entry entry(double probability, String windSpeed) {
        return new OpenWeatherMapForecast.Entry(
                1788822000L,
                new OpenWeatherMapForecast.Main(
                        new BigDecimal("23.10"), new BigDecimal("22.00"),
                        new BigDecimal("24.00"), new BigDecimal("60.00")),
                List.of(new OpenWeatherMapForecast.Condition(500)),
                new OpenWeatherMapForecast.Clouds(85),
                new OpenWeatherMapForecast.Wind(new BigDecimal(windSpeed)),
                BigDecimal.valueOf(probability),
                new OpenWeatherMapForecast.Rain(new BigDecimal("1.20")),
                null);
    }
}
