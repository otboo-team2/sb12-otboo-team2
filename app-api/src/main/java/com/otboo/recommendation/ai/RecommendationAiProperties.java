package com.otboo.recommendation.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "otboo.recommendation.ai")
public record RecommendationAiProperties(String apiKey, String model, String baseUrl) {
}
