package com.otboo.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.weather.entity.WeatherRegion;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeatherCollectionItemProcessorTest {

    @Test
    void processesRegionAndRecordsSuccess() {
        var service = mock(OpenWeatherMapWeatherService.class);
        when(service.fetchAndConvert(anyDouble(), anyDouble(), anyInt(), anyInt())).thenReturn(List.of());
        var processor = new WeatherCollectionItemProcessor(service, new SimpleMeterRegistry());
        var region = WeatherRegion.create(60, 127, List.of("서울특별시"));

        assertThat(processor.process(region).region()).isSameAs(region);
    }

    @Test
    void externalFailureForOneRegionDoesNotPreventProcessingTheNextRegion() {
        var service = mock(OpenWeatherMapWeatherService.class);
        when(service.fetchAndConvert(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenThrow(new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR))
                .thenReturn(List.of());
        var processor = new WeatherCollectionItemProcessor(service, new SimpleMeterRegistry());
        var first = WeatherRegion.create(60, 127, List.of("서울특별시"));
        var second = WeatherRegion.create(61, 128, List.of("부산광역시"));

        assertThat(processor.process(first)).isNull();
        assertThat(processor.process(second).region()).isSameAs(second);
        verify(service, times(2)).fetchAndConvert(anyDouble(), anyDouble(), anyInt(), anyInt());
    }

    @Test
    void skipsExternalFailureSoOtherRegionsContinue() {
        var service = mock(OpenWeatherMapWeatherService.class);
        when(service.fetchAndConvert(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenThrow(new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR));
        var processor = new WeatherCollectionItemProcessor(service, new SimpleMeterRegistry());
        var region = WeatherRegion.create(60, 127, List.of("서울특별시"));

        assertThat(processor.process(region)).isNull();
    }
}
