package com.otboo.weather;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.weather.WeatherGridConverter;
import com.otboo.weather.entity.Weather;
import com.otboo.weather.exception.WeatherErrorCode;
import com.otboo.weather.repository.WeatherRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class OpenWeatherMapWeatherService {

    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
    private static final int FORECAST_HOURS = 120;

    private final OpenWeatherMapClient client;
    private final WeatherRepository repository;

    public OpenWeatherMapWeatherService(OpenWeatherMapClient client, WeatherRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    public List<Weather> fetchAndConvert(
            double latitude, double longitude, int gridX, int gridY) {
        var calculated = WeatherGridConverter.toGrid(latitude, longitude);
        if (calculated.x() != gridX || calculated.y() != gridY) {
            throw new BusinessException(WeatherErrorCode.UNSUPPORTED_LOCATION);
        }
        return convertForecast(gridX, gridY, Instant.now().truncatedTo(ChronoUnit.HOURS),
                client.fetch(latitude, longitude));
    }

    @Transactional
    public List<Weather> saveForecast(List<Weather> incoming) {
        return incoming.stream().map(this::upsert).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Weather> fetchAndSave(
            double latitude, double longitude, int gridX, int gridY) {
        var converted = fetchAndConvert(latitude, longitude, gridX, gridY);
        return saveForecast(converted);
    }

    @Transactional
    public List<Weather> saveForecast(
            int gridX, int gridY, Instant forecastedAt, OpenWeatherMapForecast forecast) {
        var converted = convertForecast(gridX, gridY, forecastedAt, forecast);
        return saveForecast(converted);
    }

    private List<Weather> convertForecast(
            int gridX, int gridY, Instant forecastedAt, OpenWeatherMapForecast forecast) {
        if (forecast == null || forecast.list() == null) {
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR);
        }

        Instant now = Instant.now();
        Instant until = now.plus(FORECAST_HOURS, ChronoUnit.HOURS);
        log.debug("[WEATHER RANGE DEBUG] filter now={} UTC / {} KST, until={} UTC / {} KST",
                now, now.atZone(java.time.ZoneId.of("Asia/Seoul")), until,
                until.atZone(java.time.ZoneId.of("Asia/Seoul")));
        Map<Instant, OpenWeatherMapForecast.Entry> entries = forecast.list().stream()
                .collect(Collectors.toMap(OpenWeatherMapForecast.Entry::forecastAt, Function.identity(), (a, b) -> b));
        var converted = forecast.list().stream()
                .map(entry -> toWeather(gridX, gridY, forecastedAt, entry, entries.get(entry.forecastAt().minus(24, ChronoUnit.HOURS))))
                .toList();
        var filtered = converted.stream()
                .filter(weather -> !weather.getForecastAt().isBefore(now)
                        && weather.getForecastAt().isBefore(until))
                .toList();
        log.debug("[WEATHER RANGE DEBUG] filter before={} after={}", converted.size(), filtered.size());
        if (!filtered.isEmpty()) {
            log.debug("[WEATHER RANGE DEBUG] filter first={} UTC / {} KST, last={} UTC / {} KST",
                    filtered.getFirst().getForecastAt(), filtered.getFirst().getForecastAt().atZone(java.time.ZoneId.of("Asia/Seoul")),
                    filtered.getLast().getForecastAt(), filtered.getLast().getForecastAt().atZone(java.time.ZoneId.of("Asia/Seoul")));
        }
        converted.stream()
                .filter(weather -> !filtered.contains(weather))
                .forEach(weather -> log.debug("[WEATHER RANGE DEBUG] excluded={} UTC / {} KST, reasonBeforeNow={} reasonAtOrAfterUntil={}",
                        weather.getForecastAt(), weather.getForecastAt().atZone(java.time.ZoneId.of("Asia/Seoul")),
                        weather.getForecastAt().isBefore(now), !weather.getForecastAt().isBefore(until)));
        var zone = java.time.ZoneId.of("Asia/Seoul");
        log.debug("[WEATHER RANGE DEBUG] 9/30 KST before={} after={}",
                converted.stream().filter(w -> w.getForecastAt().atZone(zone).toLocalDate().toString().equals("2026-09-30")).count(),
                filtered.stream().filter(w -> w.getForecastAt().atZone(zone).toLocalDate().toString().equals("2026-09-30")).count());
        return filtered;
    }

    private Weather upsert(Weather incoming) {
        return repository.findByGridXAndGridYAndForecastedAtAndForecastAt(
                        incoming.getGridX(), incoming.getGridY(),
                        incoming.getForecastedAt(), incoming.getForecastAt())
                .map(existing -> {
                    existing.updateFrom(incoming);
                    return repository.save(existing);
                })
                .orElseGet(() -> repository.save(incoming));
    }

    private Weather toWeather(int gridX, int gridY, Instant forecastedAt,
            OpenWeatherMapForecast.Entry entry, OpenWeatherMapForecast.Entry previousDay) {
        BigDecimal previousHumidity = previousDay == null ? null : previousDay.main().humidity();
        BigDecimal previousTemperature = previousDay == null ? null : previousDay.main().temp();
        if (previousDay == null) {
            var previousForecastAt = entry.forecastAt().minus(24, ChronoUnit.HOURS);
            var storedPrevious = repository
                    .findTopByGridXAndGridYAndForecastAtOrderByForecastedAtDesc(
                            gridX, gridY, previousForecastAt);
            if (storedPrevious.isEmpty()) {
                storedPrevious = repository.findLatestByGridAndForecastAtRange(
                                gridX, gridY,
                                previousForecastAt.minus(6, ChronoUnit.HOURS),
                                previousForecastAt.plus(6, ChronoUnit.HOURS)).stream()
                        .min(Comparator.comparingLong(weather -> Math.abs(
                                weather.getForecastAt().toEpochMilli() - previousForecastAt.toEpochMilli())));
            }
            previousHumidity = storedPrevious.map(Weather::getHumidityCurrent).orElse(null);
            previousTemperature = storedPrevious.map(Weather::getTemperatureCurrent).orElse(null);
        }

        int conditionCode = entry.weather().getFirst().id();
        BigDecimal precipitationAmount = entry.rain() != null
                ? entry.rain().threeHours()
                : entry.snow() != null ? entry.snow().threeHours() : BigDecimal.ZERO;
        if (precipitationAmount == null) {
            precipitationAmount = BigDecimal.ZERO;
        }

        BigDecimal windSpeed = scale(entry.wind().speed());
        return Weather.builder()
                .gridX(gridX)
                .gridY(gridY)
                .forecastedAt(forecastedAt)
                .forecastAt(entry.forecastAt())
                .skyStatus(OpenWeatherMapWeatherMapper.toSkyStatus(
                        conditionCode, entry.clouds().all()))
                .precipitationType(OpenWeatherMapWeatherMapper.toPrecipitationType(conditionCode))
                .precipitationAmount(scale(precipitationAmount))
                .precipitationProbability(scale(entry.pop().multiply(PERCENT)))
                .humidityCurrent(scale(entry.main().humidity()))
                .temperatureCurrent(scale(entry.main().temp()))
                .temperatureMin(scale(entry.main().tempMin()))
                .temperatureMax(scale(entry.main().tempMax()))
                .windSpeed(windSpeed)
                .windSpeedAsWord(OpenWeatherMapWeatherMapper.toWindStrength(
                        windSpeed.doubleValue()))
                .humidityComparedToDayBefore(previousHumidity == null ? null
                        : scale(entry.main().humidity().subtract(previousHumidity)))
                .temperatureComparedToDayBefore(previousTemperature == null ? null
                        : scale(entry.main().temp().subtract(previousTemperature)))
                .build();
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
