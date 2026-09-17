package com.otboo.recommendation.ai;

import com.otboo.clothes.entity.ClothesType;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecommendationConditionTool {

    private RecommendationConditionTool() {
    }

    public static Map<String, Object> definition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("occasion", Map.of(
                "anyOf", List.of(
                        Map.of("type", "string", "enum", occasionValues()),
                        Map.of("type", "null"))));
        properties.put("styles", stringArray());
        properties.put("fits", stringArray());
        properties.put("colors", stringArray());
        properties.put("categories", Map.of(
                "type", "array",
                "items", Map.of(
                        "type", "string",
                        "enum", clothesTypeValues())));
        properties.put("keywords", stringArray());

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("additionalProperties", false);
        parameters.put("properties", properties);
        parameters.put("required", List.of(
                "occasion", "styles", "fits", "colors", "categories", "keywords"));

        return Map.of(
                "type", "function",
                "name", "extract_recommendation_condition",
                "description", "사용자의 자연어 요청을 추천 조건으로 변환한다. 의상 ID는 선택하지 않는다.",
                "strict", true,
                "parameters", parameters);
    }

    private static Map<String, Object> stringArray() {
        return Map.of(
                "type", "array",
                "items", Map.of("type", "string"));
    }

    private static List<String> occasionValues() {
        return Arrays.stream(RecommendationOccasion.values()).map(Enum::name).toList();
    }

    private static List<String> clothesTypeValues() {
        return Arrays.stream(ClothesType.values()).map(Enum::name).toList();
    }
}
