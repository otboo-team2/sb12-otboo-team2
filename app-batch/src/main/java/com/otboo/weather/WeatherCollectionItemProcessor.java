package com.otboo.weather;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.weather.entity.WeatherRegion;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherCollectionItemProcessor implements ItemProcessor<WeatherRegion, WeatherCollectionResult> {

    private final OpenWeatherMapWeatherService weatherService;
    private final MeterRegistry meterRegistry;

    @Override
    public WeatherCollectionResult process(WeatherRegion region) {
        try {
            var coordinate = WeatherGridConverter.toCoordinate(region.getGridX(), region.getGridY());
            var weather = weatherService.fetchAndConvert(coordinate.latitude(), coordinate.longitude(),
                    region.getGridX(), region.getGridY());
            return new WeatherCollectionResult(region, weather);
        } catch (BusinessException e) {
            if (!isExternalFailure(e)) {
                throw e;
            }
            meterRegistry.counter("weather.collection.failure").increment();
            log.warn("Weather collection failed. grid_x={}, grid_y={}, error_code={}",
                    region.getGridX(), region.getGridY(), e.getErrorCode().getCode());
            return null;
        }
    }

    private boolean isExternalFailure(BusinessException exception) {
        var code = exception.getErrorCode();
        return code == CommonErrorCode.EXTERNAL_API_ERROR
                || code == CommonErrorCode.EXTERNAL_API_TIMEOUT
                || code == CommonErrorCode.EXTERNAL_API_LIMIT_EXCEEDED;
    }
}
