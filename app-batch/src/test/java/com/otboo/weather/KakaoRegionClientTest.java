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

class KakaoRegionClientTest {

    @Test
    void extractsAdministrativeRegionNames() {
        var api = mock(ExternalApiClient.class);
        var response = new KakaoRegionResponse(List.of(
                new KakaoRegionResponse.Document("H", "서울특별시 강남구 역삼동", "서울특별시",
                        "강남구", "역삼동", null, BigDecimal.valueOf(127), BigDecimal.valueOf(37)),
                new KakaoRegionResponse.Document("B", "", "", "", "", "", null, null)));
        when(api.get(anyString(), eq(KakaoRegionResponse.class))).thenReturn(response);

        var client = new KakaoRegionClient(api, "test-key");

        assertThat(client.findLocationNames(37.5, 127.0))
                .containsExactly("서울특별시", "강남구", "역삼동");
        verify(api).get(
                "/v2/local/geo/coord2regioncode.json?x=127.0&y=37.5&input_coord=WGS84",
                KakaoRegionResponse.class);
    }

    @Test
    void rejectsInvalidCoordinates() {
        var client = new KakaoRegionClient(mock(ExternalApiClient.class), "test-key");

        assertThatThrownBy(() -> client.findLocationNames(91, 127))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(WeatherErrorCode.INVALID_COORDINATE);
    }

    @Test
    void rejectsMissingApiKey() {
        var client = new KakaoRegionClient(mock(ExternalApiClient.class), " ");

        assertThatThrownBy(() -> client.findLocationNames(37.5, 127))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR);
    }
}
