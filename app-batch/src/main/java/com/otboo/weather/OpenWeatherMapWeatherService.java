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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenWeatherMapWeatherService {

    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);

    private final OpenWeatherMapClient client;
    private final WeatherRepository repository;

    public OpenWeatherMapWeatherService(OpenWeatherMapClient client, WeatherRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    @Transactional
    public List<Weather> fetchAndSave(
            double latitude, double longitude, int gridX, int gridY) {
        var calculated = WeatherGridConverter.toGrid(latitude, longitude);
        if (calculated.x() != gridX || calculated.y() != gridY) {
            throw new BusinessException(WeatherErrorCode.UNSUPPORTED_LOCATION);
        }
        return saveForecast(gridX, gridY, Instant.now().truncatedTo(ChronoUnit.HOURS),
                client.fetch(latitude, longitude));
    }

    @Transactional
    public List<Weather> saveForecast(
            int gridX, int gridY, Instant forecastedAt, OpenWeatherMapForecast forecast) {
        if (forecast == null || forecast.list() == null) {
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR);
        }

        return forecast.list().stream()
                .map(entry -> toWeather(gridX, gridY, forecastedAt, entry))
                .filter(weather -> repository.findByGridXAndGridYAndForecastedAtAndForecastAt(
                        weather.getGridX(), weather.getGridY(),
                        weather.getForecastedAt(), weather.getForecastAt()).isEmpty())
                .map(repository::save)
                .toList();
    }

    private Weather toWeather(int gridX, int gridY, Instant forecastedAt,
            OpenWeatherMapForecast.Entry entry) {
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
                .build();
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
