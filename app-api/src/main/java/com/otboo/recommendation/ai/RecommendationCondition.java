package com.otboo.recommendation.ai;

import com.otboo.clothes.entity.ClothesType;
import java.util.List;

public record RecommendationCondition(
        RecommendationOccasion occasion,
        List<String> styles,
        List<ClothesType> categories,
        List<String> keywords
) {

    public RecommendationCondition {
        styles = copy(styles);
        categories = copy(categories);
        keywords = copy(keywords);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
