package com.otboo.recommendation.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "otboo.recommendation.ai")
public record RecommendationAiProperties(
        String apiKey,
        String model,
        String baseUrl,
        String embeddingModel,
        Integer embeddingDimensions
) {
    public RecommendationAiProperties {
        if (embeddingDimensions == null || embeddingDimensions != 1536) {
            throw new IllegalArgumentException("추천 의상 Elasticsearch mapping은 embeddingDimensions=1536을 요구합니다");
        }
    }
}
