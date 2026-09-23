package com.otboo.recommendation.ai;

import com.otboo.clothes.dto.ClothesDto;
import com.otboo.common.logging.SafeExceptionLog;
import com.otboo.common.storage.ImageStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationClothesMetadataAnalyzer {

    private final OpenAiRecommendationClothesMetadataClient client;
    private final ImageStorage imageStorage;

    public RecommendationClothesMetadata analyze(ClothesDto clothes) {
        try {
            String image = clothes.imageUrl() == null ? null : imageStorage.readAsDataUri(clothes.imageUrl());
            return client.analyze(clothes, image);
        } catch (RuntimeException exception) {
            log.warn("recommendation_clothes_metadata_fallback clothesId={} exception_type={}",
                    clothes.id(), exception.getClass().getSimpleName(), SafeExceptionLog.sanitized(exception));
            return RecommendationClothesMetadata.EMPTY;
        }
    }
}
