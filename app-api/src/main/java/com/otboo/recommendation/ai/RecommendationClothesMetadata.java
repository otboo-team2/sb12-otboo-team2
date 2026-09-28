package com.otboo.recommendation.ai;

import java.util.List;
import java.util.Set;

public record RecommendationClothesMetadata(
        List<String> inferredStyles,
        RecommendationFormality formality,
        List<RecommendationOccasion> occasions
) {
    public static final Set<String> ALLOWED_STYLES = Set.of(
            "캐주얼", "포멀", "스포티", "클래식", "스트릿", "미니멀");
    public static final RecommendationClothesMetadata EMPTY =
            new RecommendationClothesMetadata(List.of(), null, List.of());

    public RecommendationClothesMetadata {
        inferredStyles = inferredStyles == null ? List.of() : List.copyOf(inferredStyles);
        occasions = occasions == null ? List.of() : List.copyOf(occasions);
        if (!ALLOWED_STYLES.containsAll(inferredStyles)) {
            throw new IllegalArgumentException("허용되지 않은 추천 스타일");
        }
    }
}
