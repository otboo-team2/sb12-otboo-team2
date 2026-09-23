package com.otboo.weather;

import com.otboo.feed.dto.PrecipitationDto;
import com.otboo.feed.dto.TemperatureDto;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.weather.dto.HumidityDto;
import com.otboo.weather.dto.WeatherDto;
import com.otboo.weather.dto.WeatherApiLocation;
import com.otboo.weather.dto.WindSpeedDto;
import com.otboo.weather.repository.WeatherRegionRepository;
import com.otboo.weather.repository.WeatherRepository;
import com.otboo.weather.entity.WeatherRegion;
import com.otboo.weather.entity.Weather;
import java.math.BigDecimal;
import java.util.Comparator;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeatherService {

    private final WeatherRepository weatherRepository;
    private final WeatherRegionRepository regionRepository;
    private final KakaoRegionClient kakaoRegionClient;
    private final OpenWeatherMapWeatherService weatherCollectionService;
    private final WeatherRegionCreationService regionCreationService;

    @Transactional
    public WeatherApiLocation findLocation(double latitude, double longitude) {
        var grid = WeatherGridConverter.toGrid(latitude, longitude);
        var region = regionRepository.findByGridXAndGridY(grid.x(), grid.y()).orElse(null);
        if (region == null) {
            var locationNames = kakaoRegionClient.findLocationNames(latitude, longitude);
            boolean created = false;
            try {
                region = regionCreationService.create(grid.x(), grid.y(), locationNames);
                created = true;
            } catch (DataIntegrityViolationException e) {
                region = regionCreationService.find(grid.x(), grid.y());
            }
            if (created) {
                try {
                    weatherCollectionService.fetchAndSave(latitude, longitude, grid.x(), grid.y());
                } catch (BusinessException e) {
                    if (!isExternalFailure(e)) {
                        throw e;
                    }
                    log.warn("Initial weather collection failed. grid_x={}, grid_y={}, error_code={}",
                            grid.x(), grid.y(), e.getErrorCode().getCode());
                }
            }
        }
        return new WeatherApiLocation(latitude, longitude, grid.x(), grid.y(), region.getLocationNames());
    }

    private boolean isExternalFailure(BusinessException exception) {
        var code = exception.getErrorCode();
        return code == CommonErrorCode.EXTERNAL_API_ERROR
                || code == CommonErrorCode.EXTERNAL_API_TIMEOUT
                || code == CommonErrorCode.EXTERNAL_API_LIMIT_EXCEEDED;
    }

    @Transactional(readOnly = true)
    public List<WeatherDto> find(double latitude, double longitude) {
        var grid = WeatherGridConverter.toGrid(latitude, longitude);
        var region = regionRepository.findByGridXAndGridY(grid.x(), grid.y()).orElse(null);
        var now = Instant.now();
        var location = new WeatherApiLocation(latitude, longitude, grid.x(), grid.y(),
                region == null ? List.of() : region.getLocationNames());
        return weatherRepository.findLatestByGridAndForecastAtRange(
                grid.x(), grid.y(), now, now.plus(120, ChronoUnit.HOURS))
                .stream().map(weather -> toDto(weather, location)).toList();
    }

    private WeatherDto toDto(com.otboo.weather.entity.Weather weather, WeatherApiLocation location) {
        BigDecimal temperatureCompared = weather.getTemperatureComparedToDayBefore();
        BigDecimal humidityCompared = weather.getHumidityComparedToDayBefore();
        Weather previous = null;
        if (temperatureCompared == null || humidityCompared == null) {
            previous = findPreviousWeather(weather);
        }
        if (previous != null) {
            if (temperatureCompared == null) {
                temperatureCompared = weather.getTemperatureCurrent()
                        .subtract(previous.getTemperatureCurrent());
            }
            if (humidityCompared == null) {
                humidityCompared = weather.getHumidityCurrent()
                        .subtract(previous.getHumidityCurrent());
            }
        }
        return new WeatherDto(weather.getId(), weather.getForecastedAt(), weather.getForecastAt(), location,
                weather.getSkyStatus(),
                new PrecipitationDto(weather.getPrecipitationType(),
                        weather.getPrecipitationAmount().doubleValue(),
                        weather.getPrecipitationProbability().doubleValue()),
                new HumidityDto(weather.getHumidityCurrent().doubleValue(),
                        value(humidityCompared)),
                new TemperatureDto(weather.getTemperatureCurrent().doubleValue(),
                        value(temperatureCompared),
                        weather.getTemperatureMin().doubleValue(), weather.getTemperatureMax().doubleValue()),
                new WindSpeedDto(weather.getWindSpeed().doubleValue(), weather.getWindSpeedAsWord()));
    }

    private Weather findPreviousWeather(Weather weather) {
        var target = weather.getForecastAt().minus(24, ChronoUnit.HOURS);
        var exact = weatherRepository.findTopByGridXAndGridYAndForecastAtOrderByForecastedAtDesc(
                weather.getGridX(), weather.getGridY(), target);
        if (exact.isPresent()) {
            return exact.get();
        }
        return weatherRepository.findLatestByGridAndForecastAtRange(
                        weather.getGridX(), weather.getGridY(),
                        target.minus(6, ChronoUnit.HOURS), target.plus(6, ChronoUnit.HOURS)).stream()
                .min(Comparator.comparingLong(candidate -> Math.abs(
                        candidate.getForecastAt().toEpochMilli() - target.toEpochMilli())))
                .orElse(null);
    }

    private static Double value(java.math.BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
