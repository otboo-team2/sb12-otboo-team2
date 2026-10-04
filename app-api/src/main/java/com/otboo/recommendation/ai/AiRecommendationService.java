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
import java.util.function.Supplier;
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
    private final RecommendationAiConcurrencyLimit aiConcurrency;
    private final RecommendationAiMetrics aiMetrics;

    public RecommendationDto find(UUID userId, RecommendationAiRequest request) {
        long startedAt = System.nanoTime();
        Attempt attempt = new Attempt();
        try {
            return findInternal(userId, request, attempt);
        } catch (RuntimeException e) {
            attempt.error();
            throw e;
        } finally {
            long elapsed = System.nanoTime() - startedAt;
            aiMetrics.recordRequest(attempt.outcome, attempt.reason, elapsed);
            log.info("recommendation_ai_result outcome={} reason={} elapsed_ms={} condition_ms={} "
                            + "embedding_ms={} search_ms={} generation_ms={} thread={}",
                    attempt.outcome, attempt.reason, elapsed / 1_000_000, attempt.conditionMs,
                    attempt.embeddingMs, attempt.searchMs, attempt.generationMs,
                    Thread.currentThread().isVirtual() ? "virtual" : "platform");
        }
    }

    private RecommendationDto findInternal(UUID userId, RecommendationAiRequest request, Attempt attempt) {
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
            return attempt.fallback("no_candidates", basic);
        }
        RecommendationClothesVectorSearch search = vectorSearch.getIfAvailable();
        if (search == null) {
            return attempt.fallback("search_disabled", basic);
        }
        // OpenAI 를 기다리는 요청 수를 묶는다. 넘치면 기다리지 않고 기본 추천으로 보낸다.
        if (!aiConcurrency.tryEnter()) {
            log.warn("recommendation_ai_fallback reason=busy");
            return attempt.fallback("busy", basic);
        }
        try {
            return findWithAi(userId, request, candidates, excludedIds, excludedOutfits, basic, search, attempt);
        } finally {
            aiConcurrency.exit();
        }
    }

    private RecommendationDto findWithAi(UUID userId, RecommendationAiRequest request,
            RecommendationCandidates candidates, Set<UUID> excludedIds, Set<Set<UUID>> excludedOutfits,
            RecommendationDto basic, RecommendationClothesVectorSearch search, Attempt attempt) {
        List<UUID> candidateIds = candidates.clothes().stream().map(clothes -> clothes.id()).toList();
        List<UUID> retrievedIds = List.of();
        RecommendationCondition condition = null;
        String externalFailure = null;
        try {
            condition = timed(attempt, "condition",
                    () -> openAiRecommendationClient.extractCondition(request.prompt()));
            RecommendationCondition extracted = condition;
            var vector = timed(attempt, "embedding",
                    () -> queryEmbeddingService.embed(request.prompt(), extracted, candidates.preferredStyles()));
            retrievedIds = timed(attempt, "search", () -> search.search(userId, candidateIds, vector));
        } catch (BusinessException e) {
            if (e.getErrorCode() != CommonErrorCode.EXTERNAL_API_ERROR
                    && e.getErrorCode() != CommonErrorCode.EXTERNAL_API_TIMEOUT
                    && e.getErrorCode() != CommonErrorCode.EXTERNAL_API_LIMIT_EXCEEDED) {
                throw e;
            }
            log.warn("recommendation_ai_fallback error_code={}",
                    e.getErrorCode().getCode());
            externalFailure = reasonOf(e);
        }
        if (retrievedIds.isEmpty()) {
            return attempt.fallback(externalFailure != null ? externalFailure : "no_retrieval", basic);
        }
        // DB 오류는 외부 API fallback에 포함시키지 않는다.
        List<UUID> verifiedIds = clothesVerifier.verify(userId, candidateIds, retrievedIds);
        if (verifiedIds.isEmpty()) {
            return attempt.fallback("not_verified", basic);
        }
        Map<UUID, ClothesDto> candidatesById = candidates.clothes().stream()
                .collect(Collectors.toMap(ClothesDto::id, Function.identity()));
        List<ClothesDto> verifiedClothes = verifiedIds.stream().map(candidatesById::get).toList();
        if (verifiedClothes.stream().anyMatch(item -> item == null)) {
            return attempt.fallback("not_verified", basic);
        }
        try {
            Map<UUID, RecommendationClothesMetadata> metadataById = timed(attempt, "metadata",
                    () -> search.metadata(userId, verifiedIds));
            RecommendationCondition extracted = condition;
            RecommendationGenerationResult generated = timed(attempt, "generation",
                    () -> openAiRecommendationClient.generate(
                            request.prompt(), extracted, candidates, verifiedClothes, metadataById));
            if (generated == null || generated.clothesIds().isEmpty()
                    || generated.reason() == null || generated.reason().isBlank()
                    || generated.clothesIds().size() != Set.copyOf(generated.clothesIds()).size()
                    || !Set.copyOf(verifiedIds).containsAll(generated.clothesIds())
                    || generated.clothesIds().stream().anyMatch(excludedIds::contains)
                    || ExcludedOutfits.contains(excludedOutfits, generated.clothesIds())) {
                log.warn("recommendation_ai_fallback error_code={}",
                        CommonErrorCode.EXTERNAL_API_ERROR.getCode());
                return attempt.fallback("invalid_result", basic);
            }
            List<ClothesDto> generatedClothes = generated.clothesIds().stream()
                    .map(candidatesById::get).toList();
            if (!OotdCombinationPolicy.isValid(generatedClothes)) {
                log.warn("recommendation_generation_invalid_outfit clothes_types={} clothes_count={}",
                        generatedClothes.stream()
                                .map(item -> item == null ? null : item.type())
                                .toList(),
                        generatedClothes.size());
                log.warn("recommendation_ai_fallback error_code={}",
                        CommonErrorCode.EXTERNAL_API_ERROR.getCode());
                return attempt.fallback("invalid_result", basic);
            }
            List<OotdDto> clothes = generatedClothes.stream().map(AiRecommendationService::toOotd).toList();
            attempt.succeed();
            return new RecommendationDto(candidates.weatherId(), userId, clothes, generated.reason());
        } catch (BusinessException e) {
            if (e.getErrorCode() != CommonErrorCode.EXTERNAL_API_ERROR
                    && e.getErrorCode() != CommonErrorCode.EXTERNAL_API_TIMEOUT
                    && e.getErrorCode() != CommonErrorCode.EXTERNAL_API_LIMIT_EXCEEDED) {
                throw e;
            }
            log.warn("recommendation_ai_fallback error_code={}",
                    e.getErrorCode().getCode());
            return attempt.fallback(reasonOf(e), basic);
        }
    }

    private <T> T timed(Attempt attempt, String stage, Supplier<T> call) {
        long startedAt = System.nanoTime();
        boolean isSuccess = false;
        try {
            T result = call.get();
            isSuccess = true;
            return result;
        } finally {
            long elapsed = System.nanoTime() - startedAt;
            attempt.recordStage(stage, elapsed);
            aiMetrics.recordStage(stage, isSuccess, elapsed);
        }
    }

    private static String reasonOf(BusinessException e) {
        if (e.getErrorCode() == CommonErrorCode.EXTERNAL_API_TIMEOUT) {
            return "timeout";
        }
        return e.getErrorCode() == CommonErrorCode.EXTERNAL_API_LIMIT_EXCEEDED ? "limit_exceeded" : "external_error";
    }

    /** 요청 한 건의 결과와 단계별 시간. 요청 스레드 하나에서만 쓴다 */
    private static final class Attempt {

        private String outcome = RecommendationAiMetrics.OUTCOME_ERROR;
        private String reason = "exception";
        private long conditionMs = -1;
        private long embeddingMs = -1;
        private long searchMs = -1;
        private long generationMs = -1;

        RecommendationDto fallback(String fallbackReason, RecommendationDto basic) {
            outcome = RecommendationAiMetrics.OUTCOME_FALLBACK;
            reason = fallbackReason;
            return basic;
        }

        void succeed() {
            outcome = RecommendationAiMetrics.OUTCOME_AI;
            reason = "none";
        }

        void error() {
            outcome = RecommendationAiMetrics.OUTCOME_ERROR;
            reason = "exception";
        }

        void recordStage(String stage, long elapsedNanos) {
            long ms = elapsedNanos / 1_000_000;
            switch (stage) {
                case "condition" -> conditionMs = ms;
                case "embedding" -> embeddingMs = ms;
                case "search" -> searchMs = ms;
                case "generation" -> generationMs = ms;
                default -> { }
            }
        }
    }

    private static OotdDto toOotd(ClothesDto clothes) {
        return new OotdDto(clothes.id(), clothes.name(), clothes.imageUrl(), clothes.type().name(),
                clothes.attributes().stream().map(attribute -> new ClothesAttributeWithDefDto(
                        attribute.definitionId(), attribute.definitionName(),
                        attribute.selectableValues(), attribute.value())).toList());
    }
}
