package com.otboo.weather;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.http.ExternalApiClient;
import com.otboo.common.http.ExternalApiClientFactory;
import com.otboo.weather.exception.WeatherErrorCode;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KakaoRegionClient {

    private final ExternalApiClient api;
    private final String apiKey;

    @Autowired
    public KakaoRegionClient(
            ExternalApiClientFactory factory,
            @Value("${otboo.weather.kakao-api-key:}") String apiKey
    ) {
        this.api = factory.create("kakao", builder -> builder
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader("Authorization", "KakaoAK " + apiKey));
        this.apiKey = apiKey;
    }

    KakaoRegionClient(ExternalApiClient api, String apiKey) {
        this.api = api;
        this.apiKey = apiKey;
    }

    public List<String> findLocationNames(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new BusinessException(WeatherErrorCode.INVALID_COORDINATE);
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR);
        }

        // 카카오 Local API는 WGS84 경도를 x, 위도를 y로 받는다
        KakaoRegionResponse response = api.get(
                "/v2/local/geo/coord2regioncode.json?x=%s&y=%s&input_coord=WGS84"
                        .formatted(longitude, latitude),
                KakaoRegionResponse.class);
        if (response == null || response.documents() == null) {
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR);
        }

        return response.documents().stream()
                // H는 카카오가 정의한 행정구역 문서 유형
                .filter(document -> document != null && "H".equals(document.regionType()))
                .findFirst()
                .map(this::names)
                .filter(names -> !names.isEmpty())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR));
    }

    private List<String> names(KakaoRegionResponse.Document document) {
        return Stream.of(document.region1DepthName(), document.region2DepthName(),
                        document.region3DepthName(), document.region4DepthName())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
    }
}
