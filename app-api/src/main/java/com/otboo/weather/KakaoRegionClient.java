package com.otboo.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.http.ExternalApiClient;
import com.otboo.common.http.ExternalApiClientFactory;
import com.otboo.weather.exception.WeatherErrorCode;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class KakaoRegionClient {
    private final ExternalApiClient api;
    private final String apiKey;

    public KakaoRegionClient(ExternalApiClientFactory factory,
            @Value("${otboo.weather.kakao-api-key:}") String apiKey) {
        this.api = factory.create("kakao", builder -> builder
                .baseUrl("https://dapi.kakao.com")
                .defaultHeader("Authorization", "KakaoAK " + apiKey));
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
        var response = api.get(
                "/v2/local/geo/coord2regioncode.json?x=%s&y=%s&input_coord=WGS84"
                        .formatted(longitude, latitude), KakaoRegionResponse.class);
        if (response == null || response.documents() == null) {
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR);
        }
        return response.documents().stream()
                .filter(d -> d != null && "H".equals(d.regionType()))
                .findFirst().map(d -> Stream.of(d.region1DepthName(), d.region2DepthName(),
                        d.region3DepthName(), d.region4DepthName())
                        .filter(n -> n != null && !n.isBlank()).distinct().toList())
                .filter(names -> !names.isEmpty())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoRegionResponse(List<Document> documents) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        private record Document(
                @JsonProperty("region_type") String regionType,
                @JsonProperty("region_1depth_name") String region1DepthName,
                @JsonProperty("region_2depth_name") String region2DepthName,
                @JsonProperty("region_3depth_name") String region3DepthName,
                @JsonProperty("region_4depth_name") String region4DepthName) {
        }
    }
}
