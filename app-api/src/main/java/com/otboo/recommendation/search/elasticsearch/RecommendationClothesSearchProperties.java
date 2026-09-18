package com.otboo.recommendation.search.elasticsearch;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "otboo.recommendation.search")
public record RecommendationClothesSearchProperties(
        boolean enabled,
        String indexName
) {
    public RecommendationClothesSearchProperties {
        if (indexName == null || indexName.isBlank()) {
            indexName = "recommendation-clothes";
        }
    }
}
