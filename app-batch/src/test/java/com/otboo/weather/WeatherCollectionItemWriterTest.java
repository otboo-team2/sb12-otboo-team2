package com.otboo.weather;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.weather.entity.WeatherRegion;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.Chunk;

class WeatherCollectionItemWriterTest {

    @Test
    void propagatesStorageFailureSoChunkCanRollback() {
        var service = mock(OpenWeatherMapWeatherService.class);
        when(service.saveForecast(org.mockito.ArgumentMatchers.anyList()))
                .thenThrow(new IllegalStateException("storage failure"));
        var writer = new WeatherCollectionItemWriter(service, new SimpleMeterRegistry());
        var result = new WeatherCollectionResult(
                WeatherRegion.create(60, 127, List.of("서울특별시")), List.of());

        assertThatThrownBy(() -> writer.write(new Chunk<>(List.of(result))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void writesEachProcessedRegionThroughWeatherService() {
        var service = mock(OpenWeatherMapWeatherService.class);
        var writer = new WeatherCollectionItemWriter(service, new SimpleMeterRegistry());
        var region = WeatherRegion.create(60, 127, List.of("서울특별시"));
        var result = new WeatherCollectionResult(region, List.of());

        writer.write(new Chunk<>(List.of(result)));

        verify(service).saveForecast(List.of());
    }
}
