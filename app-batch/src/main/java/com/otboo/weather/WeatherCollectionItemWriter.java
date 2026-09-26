package com.otboo.weather;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WeatherCollectionItemWriter implements ItemWriter<WeatherCollectionResult> {

    private final OpenWeatherMapWeatherService weatherService;
    private final MeterRegistry meterRegistry;

    @Override
    public void write(Chunk<? extends WeatherCollectionResult> chunk) {
        for (WeatherCollectionResult result : chunk) {
            weatherService.saveForecast(result.weather());
            meterRegistry.counter("weather.collection.success").increment();
        }
    }
}
