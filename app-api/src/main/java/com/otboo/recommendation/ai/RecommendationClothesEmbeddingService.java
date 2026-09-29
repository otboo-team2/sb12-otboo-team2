package com.otboo.recommendation.ai;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.recommendation.search.elasticsearch.RecommendationClothesDocument;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;

/** 추천 의상 문서의 결정적 content를 embedding으로 변환한다. */
public class RecommendationClothesEmbeddingService {

    @Qualifier("recommendationIndexEmbeddingClient")
    private final OpenAiEmbeddingClient embeddingClient;

    public RecommendationClothesEmbeddingService(
            @Qualifier("recommendationIndexEmbeddingClient") OpenAiEmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    public List<Float> embed(RecommendationClothesDocument document) {
        if (document == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        return embeddingClient.embed(document.content());
    }
}
