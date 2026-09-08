package com.otboo.weather.repository;

import com.otboo.weather.entity.WeatherRegion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherRegionRepository extends JpaRepository<WeatherRegion, UUID> {

    Optional<WeatherRegion> findByGridXAndGridY(int gridX, int gridY);
}
