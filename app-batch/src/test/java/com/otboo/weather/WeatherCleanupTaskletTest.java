package com.otboo.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.otboo.weather.repository.WeatherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.batch.repeat.RepeatStatus;

class WeatherCleanupTaskletTest {

    @Test
    void deletesWeatherOlderThanSevenDays() throws Exception {
        var repository = mock(WeatherRepository.class);
        when(repository.deleteByForecastAtBefore(any())).thenReturn(3L);

        var result = new WeatherCleanupTasklet(repository).execute(null, null);

        assertThat(result).isEqualTo(RepeatStatus.FINISHED);
        verify(repository).deleteByForecastAtBefore(any());
    }
}
