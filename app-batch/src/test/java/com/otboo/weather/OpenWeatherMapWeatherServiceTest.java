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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class OpenWeatherMapWeatherServiceTest {

    @Test
    void fetchAndSaveUsesIndependentTransaction() throws NoSuchMethodException {
        var transaction = OpenWeatherMapWeatherService.class
                .getMethod("fetchAndSave", double.class, double.class, int.class, int.class)
                .getAnnotation(Transactional.class);

        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

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
    void usesStoredForecastWhenResponseDoesNotContainPreviousDay() {
        var repository = mock(WeatherRepository.class);
        var forecastAt = Instant.now().plus(6, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
        var previous = Weather.builder()
                .gridX(60).gridY(127)
                .forecastedAt(forecastAt.minus(24, ChronoUnit.HOURS))
                .forecastAt(forecastAt.minus(24, ChronoUnit.HOURS))
                .skyStatus(SkyStatus.CLEAR)
                .precipitationType(PrecipitationType.NONE)
                .precipitationAmount(BigDecimal.ZERO)
                .precipitationProbability(BigDecimal.ZERO)
                .humidityCurrent(new BigDecimal("50.00"))
                .temperatureCurrent(new BigDecimal("20.00"))
                .temperatureMin(new BigDecimal("20.00"))
                .temperatureMax(new BigDecimal("20.00"))
                .windSpeed(BigDecimal.ONE)
                .windSpeedAsWord(WindStrength.WEAK)
                .build();
        when(repository.findTopByGridXAndGridYAndForecastAtOrderByForecastedAtDesc(
                60, 127, forecastAt.minus(24, ChronoUnit.HOURS)))
                .thenReturn(Optional.empty());
        when(repository.findLatestByGridAndForecastAtRange(
                eq(60), eq(127), any(), any())).thenReturn(List.of(previous));
        when(repository.findByGridXAndGridYAndForecastedAtAndForecastAt(
                anyInt(), anyInt(), any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(Weather.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = new OpenWeatherMapWeatherService(mock(OpenWeatherMapClient.class), repository)
                .saveForecast(60, 127, Instant.now(),
                        new OpenWeatherMapForecast("200", List.of(
                                entryAt(forecastAt, 0))));

        assertThat(result.getFirst().getTemperatureComparedToDayBefore()).isEqualByComparingTo("3.10");
        assertThat(result.getFirst().getHumidityComparedToDayBefore()).isEqualByComparingTo("10.00");
    }

    @Test
    void updatesForecastAlreadyStored() {
        var repository = mock(WeatherRepository.class);
        var existing = mock(Weather.class);
        when(repository.findByGridXAndGridYAndForecastedAtAndForecastAt(
                anyInt(), anyInt(), any(), any())).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        var service = new OpenWeatherMapWeatherService(mock(OpenWeatherMapClient.class), repository);
        var result = service.saveForecast(60, 127, Instant.now(),
                new OpenWeatherMapForecast("200", List.of(entry(0))));

        assertThat(result).containsExactly(existing);
        verify(repository).save(existing);
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
    void savesOnlyForecastsWithinNext120Hours() {
        var repository = mock(WeatherRepository.class);
        when(repository.findByGridXAndGridYAndForecastedAtAndForecastAt(
                anyInt(), anyInt(), any(), any())).thenReturn(Optional.empty());
        when(repository.save(any(Weather.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var now = Instant.now();
        var result = new OpenWeatherMapWeatherService(mock(OpenWeatherMapClient.class), repository)
                .saveForecast(60, 127, now,
                        new OpenWeatherMapForecast("200", List.of(
                                entryAt(now.minusSeconds(1), 0),
                                entryAt(now.plusSeconds(119 * 3600L), 0),
                                entryAt(now.plusSeconds(121 * 3600L), 0))));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getForecastAt()).isAfter(now);
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
        return entryAt(Instant.now().plusSeconds(3600), probability, windSpeed);
    }

    private OpenWeatherMapForecast.Entry entryAt(Instant forecastAt, double probability) {
        return entryAt(forecastAt, probability, "3.12");
    }

    private OpenWeatherMapForecast.Entry entryAt(Instant forecastAt, double probability, String windSpeed) {
        return new OpenWeatherMapForecast.Entry(
                forecastAt.getEpochSecond(),
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
