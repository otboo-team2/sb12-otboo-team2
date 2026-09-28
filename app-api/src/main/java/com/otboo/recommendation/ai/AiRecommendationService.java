package com.otboo.recommendation.ai;

import com.otboo.recommendation.RecommendationDto;
import com.otboo.recommendation.RecommendationCandidates;
import com.otboo.recommendation.OotdCombinationPolicy;
import com.otboo.recommendation.ExcludedOutfits;
import com.otboo.recommendation.RecommendationService;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.recommendation.search.elasticsearch.RecommendationClothesVectorSearch;
import com.otboo.recommendation.search.RecommendationClothesVerifier;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.feed.dto.OotdDto;
import com.otboo.feed.dto.ClothesAttributeWithDefDto;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

/** 검증된 검색 후보에서 AI 의상을 선택하며 외부 장애 시 기본 추천 결과를 반환한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRecommendationService {

    private final RecommendationService recommendationService;
    private final OpenAiRecommendationClient openAiRecommendationClient;
    private final RecommendationQueryEmbeddingService queryEmbeddingService;
    private final ObjectProvider<RecommendationClothesVectorSearch> vectorSearch;
    private final RecommendationClothesVerifier clothesVerifier;

    public RecommendationDto find(UUID userId, RecommendationAiRequest request) {
        if (request == null || request.weatherId() == null
                || request.prompt() == null || request.prompt().isBlank()
                || request.prompt().length() > 100) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        // MySQL 조회와 날씨 필터는 외부 AI 오류 처리 범위 밖에서 수행한다.
        RecommendationCandidates found = recommendationService.findCandidates(userId, request.weatherId());
        Set<UUID> excludedIds = Set.copyOf(request.excludeClothesIds());
        RecommendationCandidates candidates = new RecommendationCandidates(
                found.weatherId(), found.userId(), found.temperature(), found.precipitationType(),
                found.temperatureSensitivity(), found.preferredStyles(), found.clothes().stream()
                        .filter(clothes -> !excludedIds.contains(clothes.id()))
                        .toList());
        Set<Set<UUID>> excludedOutfits = ExcludedOutfits.canonicalize(request.excludedOutfits());
        RecommendationDto basic = excludedOutfits.isEmpty()
                ? recommendationService.recommend(candidates)
                : recommendationService.recommend(candidates, request.excludedOutfits());
        if (candidates.clothes().isEmpty()) {
            return basic;
        }
        RecommendationClothesVectorSearch search = vectorSearch.getIfAvailable();
        if (search == null) {
            return basic;
        }
        List<UUID> candidateIds = candidates.clothes().stream().map(clothes -> clothes.id()).toList();
        List<UUID> retrievedIds = List.of();
        RecommendationCondition condition = null;
        try {
            condition = openAiRecommendationClient.extractCondition(request.prompt());
            var vector = queryEmbeddingService.embed(request.prompt(), condition, candidates.preferredStyles());
            retrievedIds = search.search(userId, candidateIds, vector);
            if (condition != null) {
                log.info("recommendation_condition_extracted occasion={} styles_count={} keywords_count={}",
                        condition.occasion(), condition.styles().size(), condition.keywords().size());
            }
        } catch (BusinessException e) {
            if (e.getErrorCode() != CommonErrorCode.EXTERNAL_API_ERROR
                    && e.getErrorCode() != CommonErrorCode.EXTERNAL_API_TIMEOUT
                    && e.getErrorCode() != CommonErrorCode.EXTERNAL_API_LIMIT_EXCEEDED) {
                throw e;
            }
            log.warn("recommendation_ai_fallback error_code={}", e.getErrorCode().getCode());
        }
        if (retrievedIds.isEmpty()) {
            return basic;
        }
        // DB 오류는 외부 API fallback에 포함시키지 않는다.
        List<UUID> verifiedIds = clothesVerifier.verify(userId, candidateIds, retrievedIds);
        if (verifiedIds.isEmpty()) {
            return basic;
        }
        Map<UUID, ClothesDto> candidatesById = candidates.clothes().stream()
                .collect(Collectors.toMap(ClothesDto::id, Function.identity()));
        List<ClothesDto> verifiedClothes = verifiedIds.stream().map(candidatesById::get).toList();
        if (verifiedClothes.stream().anyMatch(item -> item == null)) {
            return basic;
        }
        try {
            Map<UUID, RecommendationClothesMetadata> metadataById = search.metadata(userId, verifiedIds);
            RecommendationGenerationResult generated = openAiRecommendationClient.generate(
                    request.prompt(), condition, candidates, verifiedClothes, metadataById);
            if (generated == null || generated.clothesIds().isEmpty()
                    || generated.reason() == null || generated.reason().isBlank()
                    || generated.clothesIds().size() != Set.copyOf(generated.clothesIds()).size()
                    || !Set.copyOf(verifiedIds).containsAll(generated.clothesIds())
                    || generated.clothesIds().stream().anyMatch(excludedIds::contains)
                    || ExcludedOutfits.contains(excludedOutfits, generated.clothesIds())) {
                log.warn("recommendation_ai_fallback error_code={}", CommonErrorCode.EXTERNAL_API_ERROR.getCode());
                return basic;
            }
            List<ClothesDto> generatedClothes = generated.clothesIds().stream()
                    .map(candidatesById::get).toList();
            if (!OotdCombinationPolicy.isValid(generatedClothes)) {
                log.warn("recommendation_ai_fallback error_code={}", CommonErrorCode.EXTERNAL_API_ERROR.getCode());
                return basic;
            }
            List<OotdDto> clothes = generatedClothes.stream().map(AiRecommendationService::toOotd).toList();
            return new RecommendationDto(candidates.weatherId(), userId, clothes, generated.reason());
        } catch (BusinessException e) {
            if (e.getErrorCode() != CommonErrorCode.EXTERNAL_API_ERROR
                    && e.getErrorCode() != CommonErrorCode.EXTERNAL_API_TIMEOUT
                    && e.getErrorCode() != CommonErrorCode.EXTERNAL_API_LIMIT_EXCEEDED) {
                throw e;
            }
            log.warn("recommendation_ai_fallback error_code={}", e.getErrorCode().getCode());
            return basic;
        }
    }

    private static OotdDto toOotd(ClothesDto clothes) {
        return new OotdDto(clothes.id(), clothes.name(), clothes.imageUrl(), clothes.type().name(),
                clothes.attributes().stream().map(attribute -> new ClothesAttributeWithDefDto(
                        attribute.definitionId(), attribute.definitionName(),
                        attribute.selectableValues(), attribute.value())).toList());
    }
}
