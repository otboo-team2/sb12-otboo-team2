package com.otboo.weather;

import com.otboo.weather.entity.WeatherRegion;
import com.otboo.weather.repository.WeatherRegionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WeatherRegionCreationService {

    private final WeatherRegionRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WeatherRegion create(int gridX, int gridY, List<String> locationNames) {
        return repository.saveAndFlush(WeatherRegion.create(gridX, gridY, locationNames));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public WeatherRegion find(int gridX, int gridY) {
        return repository.findByGridXAndGridY(gridX, gridY).orElseThrow();
    }
}
