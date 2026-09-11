package com.otboo.weather.repository;

import com.otboo.weather.entity.Weather;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeatherRepository extends JpaRepository<Weather, UUID> {

    @Modifying
    @Query(value = """
            delete from weathers
            where forecast_at < :cutoff
              and not exists (
                  select 1 from feeds f where f.weather_id = weathers.id
              )
            """, nativeQuery = true)
    long deleteByForecastAtBefore(Instant cutoff);

    Optional<Weather> findByGridXAndGridYAndForecastedAtAndForecastAt(
            int gridX, int gridY, Instant forecastedAt, Instant forecastAt);

    Optional<Weather> findTopByGridXAndGridYAndForecastAtOrderByForecastedAtDesc(
            int gridX, int gridY, Instant forecastAt);

    List<Weather> findByGridXAndGridYAndForecastedAtOrderByForecastAtAsc(
            int gridX, int gridY, Instant forecastedAt);

    @Query("""
            select w from Weather w
            where w.gridX = :gridX
              and w.gridY = :gridY
              and w.forecastAt >= :from
              and w.forecastAt < :until
              and not exists (
                  select newer.id from Weather newer
                  where newer.gridX = w.gridX
                    and newer.gridY = w.gridY
                    and newer.forecastAt = w.forecastAt
                    and newer.forecastedAt > w.forecastedAt
              )
            order by w.forecastAt asc
            """)
    List<Weather> findLatestByGridAndForecastAtRange(
            @Param("gridX") int gridX,
            @Param("gridY") int gridY,
            @Param("from") Instant from,
            @Param("until") Instant until);
}
