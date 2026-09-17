package com.otboo.recommendation.ai;

import com.otboo.recommendation.RecommendationDto;
import com.otboo.recommendation.RecommendationCandidates;
import com.otboo.recommendation.RecommendationService;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 조건 추출을 수행하며 외부 AI 장애 시 기본 추천 결과를 반환한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRecommendationService {

    private final RecommendationService recommendationService;
    private final OpenAiRecommendationClient openAiRecommendationClient;

    public RecommendationDto find(UUID userId, RecommendationAiRequest request) {
        if (request == null || request.weatherId() == null
                || request.prompt() == null || request.prompt().isBlank()
                || request.prompt().length() > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        // MySQL 조회와 날씨 필터는 외부 AI 오류 처리 범위 밖에서 수행한다.
        RecommendationCandidates candidates = recommendationService.findCandidates(userId, request.weatherId());
        RecommendationDto basic = recommendationService.recommend(candidates);
        if (candidates.clothes().isEmpty()) {
            return basic;
        }
        try {
            RecommendationCondition condition = openAiRecommendationClient.extractCondition(request.prompt());
            log.info("recommendation_condition_extracted occasion={} styles_count={} keywords_count={}",
                    condition.occasion(), condition.styles().size(), condition.keywords().size());
        } catch (BusinessException e) {
            if (e.getErrorCode() != CommonErrorCode.EXTERNAL_API_ERROR
                    && e.getErrorCode() != CommonErrorCode.EXTERNAL_API_TIMEOUT
                    && e.getErrorCode() != CommonErrorCode.EXTERNAL_API_LIMIT_EXCEEDED) {
                throw e;
            }
            log.warn("recommendation_ai_fallback error_code={}", e.getErrorCode().getCode());
        }
        return basic;
    }
}
