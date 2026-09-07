package com.otboo.weather.repository;

import com.otboo.weather.entity.Weather;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherRepository extends JpaRepository<Weather, UUID> {

    Optional<Weather> findByGridXAndGridYAndForecastedAtAndForecastAt(
            int gridX, int gridY, Instant forecastedAt, Instant forecastAt);

    List<Weather> findByGridXAndGridYAndForecastedAtOrderByForecastAtAsc(
            int gridX, int gridY, Instant forecastedAt);
}
