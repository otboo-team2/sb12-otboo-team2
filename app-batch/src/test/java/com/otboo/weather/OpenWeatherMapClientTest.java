package com.otboo.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.http.ExternalApiClient;
import com.otboo.weather.exception.WeatherErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenWeatherMapClientTest {

    @Test
    void requestsForecastWithCoordinatesAndMetricUnits() {
        var api = mock(ExternalApiClient.class);
        var response = new OpenWeatherMapForecast("200", List.of(validEntry()));
        when(api.get(anyString(), eq(OpenWeatherMapForecast.class))).thenReturn(response);

        var client = new OpenWeatherMapClient(api, "test-key");
        assertThat(client.fetch(37.5, 127.0)).isSameAs(response);

        verify(api).get(
                "/data/2.5/forecast?lat=37.5&lon=127.0&appid=test-key&units=metric",
                OpenWeatherMapForecast.class);
    }

    @Test
    void rejectsInvalidCoordinates() {
        var client = new OpenWeatherMapClient(mock(ExternalApiClient.class), "test-key");

        assertThatThrownBy(() -> client.fetch(Double.NaN, 127))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(WeatherErrorCode.INVALID_COORDINATE);
    }

    @Test
    void rejectsMissingApiKey() {
        var client = new OpenWeatherMapClient(mock(ExternalApiClient.class), " ");

        assertThatThrownBy(() -> client.fetch(37.5, 127))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR);
    }

    private OpenWeatherMapForecast.Entry validEntry() {
        return new OpenWeatherMapForecast.Entry(
                1788822000L,
                new OpenWeatherMapForecast.Main(
                        BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN),
                List.of(new OpenWeatherMapForecast.Condition(800)),
                new OpenWeatherMapForecast.Clouds(20),
                new OpenWeatherMapForecast.Wind(BigDecimal.ONE),
                BigDecimal.ZERO, null, null);
    }
}
