package com.otboo.weather.dto;

import com.otboo.feed.dto.PrecipitationDto;
import com.otboo.feed.dto.TemperatureDto;
import com.otboo.weather.SkyStatus;
import java.time.Instant;
import java.util.UUID;

public record WeatherDto(
        UUID id,
        Instant forecastedAt,
        Instant forecastAt,
        WeatherApiLocation location,
        SkyStatus skyStatus,
        PrecipitationDto precipitation,
        HumidityDto humidity,
        TemperatureDto temperature,
        WindSpeedDto windSpeed
) {
}
