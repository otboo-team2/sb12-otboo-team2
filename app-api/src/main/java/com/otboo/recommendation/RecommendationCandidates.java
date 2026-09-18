package com.otboo.recommendation;

import com.otboo.clothes.dto.ClothesDto;
import com.otboo.weather.PrecipitationType;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 날씨 필터를 통과한 전체 의상. 카테고리별 최종 선택 전의 후보를 유지한다. */
public record RecommendationCandidates(
        UUID weatherId,
        UUID userId,
        double temperature,
        PrecipitationType precipitationType,
        Integer temperatureSensitivity,
        Set<String> preferredStyles,
        List<ClothesDto> clothes
) {
    public RecommendationCandidates {
        preferredStyles = Set.copyOf(preferredStyles);
        clothes = List.copyOf(clothes);
    }
}
