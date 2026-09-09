package com.otboo.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoRegionResponse(List<Document> documents) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Document(
            @JsonProperty("region_type") String regionType,
            @JsonProperty("address_name") String addressName,
            @JsonProperty("region_1depth_name") String region1DepthName,
            @JsonProperty("region_2depth_name") String region2DepthName,
            @JsonProperty("region_3depth_name") String region3DepthName,
            @JsonProperty("region_4depth_name") String region4DepthName,
            BigDecimal x,
            BigDecimal y
    ) {
    }
}
