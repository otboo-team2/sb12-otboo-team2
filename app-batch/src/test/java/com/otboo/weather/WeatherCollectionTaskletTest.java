package com.otboo.weather;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.weather.entity.WeatherRegion;
import com.otboo.weather.repository.WeatherRegionRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeatherCollectionTaskletTest {

    @Test
    void continuesAfterExternalFailure() throws Exception {
        var regions = mock(WeatherRegionRepository.class);
        var service = mock(OpenWeatherMapWeatherService.class);
        when(regions.findAll()).thenReturn(List.of(
                WeatherRegion.create(60, 127, List.of("서울특별시")),
                WeatherRegion.create(98, 76, List.of("부산광역시"))));
        when(service.fetchAndSave(anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenThrow(new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR))
                .thenReturn(List.of());

        new WeatherCollectionTasklet(regions, service).execute(null, null);

        verify(service, times(2)).fetchAndSave(anyDouble(), anyDouble(), anyInt(), anyInt());
    }
}
