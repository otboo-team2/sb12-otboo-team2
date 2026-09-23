package com.otboo.weather.entity;

import com.otboo.common.entity.BaseEntity;
import com.otboo.common.exception.BusinessException;
import com.otboo.weather.WeatherGridConverter;
import com.otboo.weather.exception.WeatherErrorCode;
import com.otboo.weather.PrecipitationType;
import com.otboo.weather.SkyStatus;
import com.otboo.weather.WindStrength;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "weathers")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Weather extends BaseEntity {

    @Column(name = "grid_x", nullable = false)
    private int gridX;

    @Column(name = "grid_y", nullable = false)
    private int gridY;

    @Column(name = "forecasted_at", nullable = false)
    private Instant forecastedAt;

    @Column(name = "forecast_at", nullable = false)
    private Instant forecastAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sky_status", nullable = false, length = 32)
    private SkyStatus skyStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "precipitation_type", nullable = false, length = 32)
    private PrecipitationType precipitationType;

    @Column(name = "precipitation_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal precipitationAmount;

    @Column(name = "precipitation_probability", nullable = false, precision = 5, scale = 2)
    private BigDecimal precipitationProbability;

    @Column(name = "humidity_current", nullable = false, precision = 5, scale = 2)
    private BigDecimal humidityCurrent;

    @Column(name = "temperature_current", nullable = false, precision = 5, scale = 2)
    private BigDecimal temperatureCurrent;

    @Column(name = "temperature_min", nullable = false, precision = 5, scale = 2)
    private BigDecimal temperatureMin;

    @Column(name = "temperature_max", nullable = false, precision = 5, scale = 2)
    private BigDecimal temperatureMax;

    @Column(name = "wind_speed", nullable = false, precision = 6, scale = 2)
    private BigDecimal windSpeed;

    @Enumerated(EnumType.STRING)
    @Column(name = "wind_speed_as_word", nullable = false, length = 16)
    private WindStrength windSpeedAsWord;

    @Column(name = "humidity_compared_to_day_before", nullable = true, precision = 5, scale = 2)
    private BigDecimal humidityComparedToDayBefore;

    @Column(name = "temperature_compared_to_day_before", nullable = true, precision = 5, scale = 2)
    private BigDecimal temperatureComparedToDayBefore;

    @Builder
    private Weather(int gridX,
            int gridY,
            Instant forecastedAt,
            Instant forecastAt,
            SkyStatus skyStatus,
            PrecipitationType precipitationType,
            BigDecimal precipitationAmount,
            BigDecimal precipitationProbability,
            BigDecimal humidityCurrent,
            BigDecimal temperatureCurrent,
            BigDecimal temperatureMin,
            BigDecimal temperatureMax,
            BigDecimal windSpeed,
            WindStrength windSpeedAsWord,
            BigDecimal humidityComparedToDayBefore,
            BigDecimal temperatureComparedToDayBefore) {
        WeatherGridConverter.validateGrid(gridX, gridY);
        if (forecastedAt == null || forecastAt == null || skyStatus == null
                || precipitationType == null || windSpeedAsWord == null) {
            throw new BusinessException(WeatherErrorCode.INVALID_WEATHER);
        }
        validateNumber(precipitationAmount, "precipitationAmount", "0", "99999999.99");
        validateNumber(precipitationProbability, "precipitationProbability", "0", "100");
        validateNumber(humidityCurrent, "humidityCurrent", "0", "100");
        validateNumber(temperatureCurrent, "temperatureCurrent", "-999.99", "999.99");
        validateNumber(temperatureMin, "temperatureMin", "-999.99", "999.99");
        validateNumber(temperatureMax, "temperatureMax", "-999.99", "999.99");
        validateNumber(windSpeed, "windSpeed", "0", "9999.99");
        if (temperatureMin.compareTo(temperatureMax) > 0) {
            throw new BusinessException(WeatherErrorCode.INVALID_WEATHER);
        }

        if (humidityComparedToDayBefore != null) {
            validateNumber(humidityComparedToDayBefore, "humidityComparedToDayBefore", "-100", "100");
        }
        if (temperatureComparedToDayBefore != null) {
            validateNumber(temperatureComparedToDayBefore, "temperatureComparedToDayBefore", "-999.99", "999.99");
        }
        this.gridX = gridX;
        this.gridY = gridY;
        this.forecastedAt = forecastedAt;
        this.forecastAt = forecastAt;
        this.skyStatus = skyStatus;
        this.precipitationType = precipitationType;
        this.precipitationAmount = precipitationAmount;
        this.precipitationProbability = precipitationProbability;
        this.humidityCurrent = humidityCurrent;
        this.temperatureCurrent = temperatureCurrent;
        this.temperatureMin = temperatureMin;
        this.temperatureMax = temperatureMax;
        this.windSpeed = windSpeed;
        this.windSpeedAsWord = windSpeedAsWord;
        this.humidityComparedToDayBefore = humidityComparedToDayBefore;
        this.temperatureComparedToDayBefore = temperatureComparedToDayBefore;
    }

    public void updateFrom(Weather source) {
        this.skyStatus = source.skyStatus;
        this.precipitationType = source.precipitationType;
        this.precipitationAmount = source.precipitationAmount;
        this.precipitationProbability = source.precipitationProbability;
        this.humidityCurrent = source.humidityCurrent;
        this.temperatureCurrent = source.temperatureCurrent;
        this.temperatureMin = source.temperatureMin;
        this.temperatureMax = source.temperatureMax;
        this.windSpeed = source.windSpeed;
        this.windSpeedAsWord = source.windSpeedAsWord;
        this.humidityComparedToDayBefore = source.humidityComparedToDayBefore;
        this.temperatureComparedToDayBefore = source.temperatureComparedToDayBefore;
    }

    private static void validateNumber(BigDecimal value, String field, String min, String max) {

        if (value == null || value.stripTrailingZeros().scale() > 2
                || value.compareTo(new BigDecimal(min)) < 0
                || value.compareTo(new BigDecimal(max)) > 0) {
            throw new BusinessException(WeatherErrorCode.INVALID_WEATHER).addDetail("field", field);
        }
    }
}
