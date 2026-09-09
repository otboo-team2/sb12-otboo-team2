package com.otboo.weather;

import com.otboo.common.exception.BusinessException;
import com.otboo.weather.entity.WeatherRegion;
import com.otboo.weather.exception.WeatherErrorCode;
import com.otboo.weather.repository.WeatherRegionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KakaoWeatherRegionService {

    private final KakaoRegionClient client;
    private final WeatherRegionRepository repository;

    public KakaoWeatherRegionService(KakaoRegionClient client, WeatherRegionRepository repository) {
        this.client = client;
        this.repository = repository;
    }

    @Transactional
    public WeatherRegion fetchAndSave(
            double latitude, double longitude, int gridX, int gridY) {
        var calculated = WeatherGridConverter.toGrid(latitude, longitude);
        if (calculated.x() != gridX || calculated.y() != gridY) {
            throw new BusinessException(WeatherErrorCode.UNSUPPORTED_LOCATION);
        }
        return repository.findByGridXAndGridY(gridX, gridY)
                .orElseGet(() -> repository.save(WeatherRegion.create(
                        gridX, gridY, client.findLocationNames(latitude, longitude))));
    }
}
