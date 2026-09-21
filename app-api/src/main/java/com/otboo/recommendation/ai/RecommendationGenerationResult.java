package com.otboo.recommendation.ai;

import java.util.List;
import java.util.UUID;

public record RecommendationGenerationResult(List<UUID> clothesIds, String reason) {
    public RecommendationGenerationResult {
        clothesIds = List.copyOf(clothesIds);
    }
}
