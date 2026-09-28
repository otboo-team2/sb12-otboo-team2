package com.otboo.recommendation.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.http.ExternalApiClient;
import com.otboo.common.http.ExternalApiClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.feed.dto.OotdDto;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.recommendation.RecommendationCandidates;
import com.otboo.weather.PrecipitationType;
import com.otboo.recommendation.RecommendationDto;
import com.otboo.recommendation.RecommendationService;
import com.otboo.recommendation.search.elasticsearch.RecommendationClothesVectorSearch;
import com.otboo.recommendation.search.RecommendationClothesVerifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.beans.factory.ObjectProvider;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class AiRecommendationServiceTest {

    @Mock RecommendationService recommendationService;
    @Mock OpenAiRecommendationClient openAiRecommendationClient;
    @Mock RecommendationQueryEmbeddingService queryEmbeddingService;
    @Mock ObjectProvider<RecommendationClothesVectorSearch> vectorSearch;
    @Mock RecommendationClothesVerifier clothesVerifier;
    @InjectMocks AiRecommendationService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID weatherId = UUID.randomUUID();
    private final RecommendationAiRequest request = new RecommendationAiRequest(weatherId, "데이트룩 추천해줘");
    private final RecommendationDto basic = new RecommendationDto(weatherId, userId,
            List.of(new OotdDto(UUID.randomUUID(), "셔츠", null, "TOP", List.of())));
    private final RecommendationCondition emptyCondition =
            new RecommendationCondition(null, List.of(), List.of(), List.of());
    private final RecommendationCandidates candidates = new RecommendationCandidates(
            weatherId, userId, 25.0, PrecipitationType.NONE, 3, Set.of("캐주얼"),
            List.of(new ClothesDto(basic.clothes().get(0).clothesId(), userId, "셔츠",
                    null, ClothesType.TOP, false, List.of())));

    @Test
    void skipsOpenAiWhenWeatherCandidatesAreEmpty() {
        var empty = new RecommendationCandidates(weatherId, userId, 25.0,
                PrecipitationType.NONE, 3, Set.of(), List.of());
        var result = new RecommendationDto(weatherId, userId, List.of());
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(empty);
        given(recommendationService.recommend(empty)).willReturn(result);

        assertThat(service.find(userId, request)).isSameAs(result);
        verifyNoInteractions(openAiRecommendationClient, queryEmbeddingService, vectorSearch, clothesVerifier);
    }

    @Test
    void returnsBasicRecommendationWithoutAiOrSearchWhenSearchIsDisabled() {
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(candidates);
        given(recommendationService.recommend(candidates)).willReturn(basic);
        given(vectorSearch.getIfAvailable()).willReturn(null);

        assertThat(service.find(userId, request)).isSameAs(basic);
        verify(recommendationService).findCandidates(userId, weatherId);
        verify(recommendationService).recommend(candidates);
        verify(vectorSearch).getIfAvailable();
        verifyNoInteractions(openAiRecommendationClient, queryEmbeddingService, clothesVerifier);
        verifyNoMoreInteractions(recommendationService, vectorSearch);
    }

    @Test
    void returnsGeneratedRecommendationAfterRetrieval() {
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(candidates);
        given(recommendationService.recommend(candidates)).willReturn(basic);
        var condition = new RecommendationCondition(RecommendationOccasion.DATE,
                List.of("캐주얼"), List.of(), List.of());
        given(openAiRecommendationClient.extractCondition(request.prompt())).willReturn(condition);
        var vector = List.of(0.1f);
        given(queryEmbeddingService.embed(request.prompt(), condition, candidates.preferredStyles()))
                .willReturn(vector);
        var search = mock(RecommendationClothesVectorSearch.class);
        when(vectorSearch.getIfAvailable()).thenReturn(search);
        var ids = List.of(candidates.clothes().getFirst().id());
        given(search.search(userId, ids, vector)).willReturn(ids);
        given(clothesVerifier.verify(userId, ids, ids)).willReturn(ids);
        var metadata = new RecommendationClothesMetadata(
                List.of("캐주얼"), RecommendationFormality.MEDIUM, List.of(RecommendationOccasion.DATE));
        given(search.metadata(userId, ids)).willReturn(Map.of(ids.getFirst(), metadata));
        given(openAiRecommendationClient.generate(
                request.prompt(), condition, candidates, candidates.clothes(), Map.of(ids.getFirst(), metadata)))
                .willReturn(new RecommendationGenerationResult(ids, "데이트에 어울리는 셔츠입니다."));

        var result = service.find(userId, request);
        assertThat(result.clothes()).extracting(OotdDto::clothesId).containsExactlyElementsOf(ids);
        assertThat(result.reason()).isEqualTo("데이트에 어울리는 셔츠입니다.");

        var order = inOrder(recommendationService, openAiRecommendationClient, queryEmbeddingService);
        order.verify(recommendationService).findCandidates(userId, weatherId);
        order.verify(recommendationService).recommend(candidates);
        order.verify(openAiRecommendationClient).extractCondition(request.prompt());
        order.verify(queryEmbeddingService).embed(request.prompt(), condition, candidates.preferredStyles());
        verify(vectorSearch).getIfAvailable();
        verify(search).search(userId, ids, vector);
        verify(clothesVerifier).verify(userId, ids, ids);
        verify(search).metadata(userId, ids);
        verify(openAiRecommendationClient).generate(
                eq(request.prompt()), same(condition), eq(candidates), eq(candidates.clothes()),
                eq(Map.of(ids.getFirst(), metadata)));
        verifyNoMoreInteractions(recommendationService, openAiRecommendationClient, queryEmbeddingService,
                vectorSearch, search, clothesVerifier);
    }

    @Test
    void excludesCurrentRecommendationBeforeRetrievalAndGeneration() {
        ClothesDto excluded = candidates.clothes().getFirst();
        ClothesDto remaining = new ClothesDto(UUID.randomUUID(), userId, "다른 셔츠", null,
                ClothesType.TOP, false, List.of());
        var all = new RecommendationCandidates(weatherId, userId, 25.0,
                PrecipitationType.NONE, 3, Set.of("캐주얼"), List.of(excluded, remaining));
        var filtered = new RecommendationCandidates(weatherId, userId, 25.0,
                PrecipitationType.NONE, 3, Set.of("캐주얼"), List.of(remaining));
        var alternativeRequest = new RecommendationAiRequest(
                weatherId, request.prompt(), List.of(excluded.id()));
        var filteredBasic = new RecommendationDto(weatherId, userId,
                List.of(new OotdDto(remaining.id(), remaining.name(), null, "TOP", List.of())));
        var search = mock(RecommendationClothesVectorSearch.class);
        var vector = List.of(0.1f);
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(all);
        given(recommendationService.recommend(filtered)).willReturn(filteredBasic);
        given(vectorSearch.getIfAvailable()).willReturn(search);
        given(openAiRecommendationClient.extractCondition(request.prompt())).willReturn(emptyCondition);
        given(queryEmbeddingService.embed(request.prompt(), emptyCondition, filtered.preferredStyles()))
                .willReturn(vector);
        given(search.search(userId, List.of(remaining.id()), vector)).willReturn(List.of(remaining.id()));
        given(clothesVerifier.verify(userId, List.of(remaining.id()), List.of(remaining.id())))
                .willReturn(List.of(remaining.id()));
        given(openAiRecommendationClient.generate(
                request.prompt(), emptyCondition, filtered, List.of(remaining), Map.of()))
                .willReturn(new RecommendationGenerationResult(List.of(remaining.id()), "다른 추천"));

        var result = service.find(userId, alternativeRequest);

        assertThat(result.clothes()).extracting(OotdDto::clothesId).containsExactly(remaining.id());
        verify(search).search(userId, List.of(remaining.id()), vector);
        verify(openAiRecommendationClient).generate(
                request.prompt(), emptyCondition, filtered, List.of(remaining), Map.of());
    }

    @Test
    void externalFailureFallsBackWithoutExcludedClothes() {
        ClothesDto excluded = candidates.clothes().getFirst();
        ClothesDto remaining = new ClothesDto(UUID.randomUUID(), userId, "다른 셔츠", null,
                ClothesType.TOP, false, List.of());
        var all = new RecommendationCandidates(weatherId, userId, 25.0,
                PrecipitationType.NONE, 3, Set.of(), List.of(excluded, remaining));
        var filtered = new RecommendationCandidates(weatherId, userId, 25.0,
                PrecipitationType.NONE, 3, Set.of(), List.of(remaining));
        var filteredBasic = new RecommendationDto(weatherId, userId,
                List.of(new OotdDto(remaining.id(), remaining.name(), null, "TOP", List.of())));
        var alternativeRequest = new RecommendationAiRequest(
                weatherId, request.prompt(), List.of(excluded.id(), UUID.randomUUID()));
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(all);
        given(recommendationService.recommend(filtered)).willReturn(filteredBasic);
        given(vectorSearch.getIfAvailable()).willReturn(mock(RecommendationClothesVectorSearch.class));
        given(openAiRecommendationClient.extractCondition(request.prompt()))
                .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR));

        var result = service.find(userId, alternativeRequest);

        assertThat(result).isSameAs(filteredBasic);
        assertThat(result.clothes()).extracting(OotdDto::clothesId).containsExactly(remaining.id());
    }

    @Test
    void rejectsExcludedIdReturnedByGeneration() {
        ClothesDto excluded = candidates.clothes().getFirst();
        ClothesDto remaining = new ClothesDto(UUID.randomUUID(), userId, "다른 셔츠", null,
                ClothesType.TOP, false, List.of());
        var all = new RecommendationCandidates(weatherId, userId, 25.0,
                PrecipitationType.NONE, 3, Set.of(), List.of(excluded, remaining));
        var filtered = new RecommendationCandidates(weatherId, userId, 25.0,
                PrecipitationType.NONE, 3, Set.of(), List.of(remaining));
        var filteredBasic = new RecommendationDto(weatherId, userId,
                List.of(new OotdDto(remaining.id(), remaining.name(), null, "TOP", List.of())));
        var alternativeRequest = new RecommendationAiRequest(
                weatherId, request.prompt(), List.of(excluded.id()));
        var search = mock(RecommendationClothesVectorSearch.class);
        var vector = List.of(0.1f);
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(all);
        given(recommendationService.recommend(filtered)).willReturn(filteredBasic);
        given(vectorSearch.getIfAvailable()).willReturn(search);
        given(openAiRecommendationClient.extractCondition(request.prompt())).willReturn(emptyCondition);
        given(queryEmbeddingService.embed(request.prompt(), emptyCondition, filtered.preferredStyles()))
                .willReturn(vector);
        given(search.search(userId, List.of(remaining.id()), vector)).willReturn(List.of(remaining.id()));
        given(clothesVerifier.verify(userId, List.of(remaining.id()), List.of(remaining.id())))
                .willReturn(List.of(remaining.id()));
        given(openAiRecommendationClient.generate(
                request.prompt(), emptyCondition, filtered, List.of(remaining), Map.of()))
                .willReturn(new RecommendationGenerationResult(List.of(excluded.id()), "잘못된 추천"));

        var result = service.find(userId, alternativeRequest);

        assertThat(result).isSameAs(filteredBasic);
        assertThat(result.clothes()).extracting(OotdDto::clothesId).doesNotContain(excluded.id());
    }

    @Test
    void duplicateGeneratedOutfitFallsBackWithoutRemovingReusableClothesOrRetryingGeneration() {
        ClothesDto topA = candidates.clothes().getFirst();
        ClothesDto bottomB = new ClothesDto(UUID.randomUUID(), userId, "하의 B", null,
                ClothesType.BOTTOM, false, List.of());
        ClothesDto shoesC = new ClothesDto(UUID.randomUUID(), userId, "신발 C", null,
                ClothesType.SHOES, false, List.of());
        ClothesDto shoesD = new ClothesDto(UUID.randomUUID(), userId, "신발 D", null,
                ClothesType.SHOES, false, List.of());
        var all = new RecommendationCandidates(weatherId, userId, 25.0,
                PrecipitationType.NONE, 3, Set.of(), List.of(topA, bottomB, shoesC, shoesD));
        List<List<UUID>> history = List.of(List.of(shoesC.id(), bottomB.id(), topA.id()));
        var alternativeRequest = new RecommendationAiRequest(
                weatherId, request.prompt(), List.of(), history);
        var fallback = new RecommendationDto(weatherId, userId, List.of(
                new OotdDto(topA.id(), topA.name(), null, "TOP", List.of()),
                new OotdDto(bottomB.id(), bottomB.name(), null, "BOTTOM", List.of()),
                new OotdDto(shoesD.id(), shoesD.name(), null, "SHOES", List.of())));
        List<UUID> ids = all.clothes().stream().map(ClothesDto::id).toList();
        var search = mock(RecommendationClothesVectorSearch.class);
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(all);
        given(recommendationService.recommend(all, history)).willReturn(fallback);
        given(vectorSearch.getIfAvailable()).willReturn(search);
        given(openAiRecommendationClient.extractCondition(request.prompt())).willReturn(emptyCondition);
        given(queryEmbeddingService.embed(request.prompt(), emptyCondition, all.preferredStyles()))
                .willReturn(List.of(0.1f));
        given(search.search(userId, ids, List.of(0.1f))).willReturn(ids);
        given(clothesVerifier.verify(userId, ids, ids)).willReturn(ids);
        given(openAiRecommendationClient.generate(
                request.prompt(), emptyCondition, all, all.clothes(), Map.of()))
                .willReturn(new RecommendationGenerationResult(
                        List.of(topA.id(), bottomB.id(), shoesC.id()), "같은 추천"));

        var result = service.find(userId, alternativeRequest);

        assertThat(result).isSameAs(fallback);
        assertThat(result.clothes()).extracting(OotdDto::clothesId)
                .containsExactly(topA.id(), bottomB.id(), shoesD.id());
        verify(search).search(userId, ids, List.of(0.1f));
        verify(openAiRecommendationClient, times(1)).generate(
                request.prompt(), emptyCondition, all, all.clothes(), Map.of());
    }

    @Test
    void allCandidatesExcludedReturnsEmptyWithoutExternalCalls() {
        var alternativeRequest = new RecommendationAiRequest(
                weatherId, request.prompt(), List.of(candidates.clothes().getFirst().id()));
        var empty = new RecommendationCandidates(weatherId, userId, 25.0,
                PrecipitationType.NONE, 3, Set.of("캐주얼"), List.of());
        var emptyResult = new RecommendationDto(weatherId, userId, List.of());
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(candidates);
        given(recommendationService.recommend(empty)).willReturn(emptyResult);

        assertThat(service.find(userId, alternativeRequest)).isSameAs(emptyResult);
        verifyNoInteractions(openAiRecommendationClient, queryEmbeddingService, vectorSearch, clothesVerifier);
    }

    @Test
    void generationReceivesOnlyVerifiedClothes() {
        UUID rejectedId = UUID.randomUUID();
        ClothesDto rejected = new ClothesDto(rejectedId, userId, "제외할 옷", null,
                ClothesType.BOTTOM, false, List.of());
        var allCandidates = new RecommendationCandidates(weatherId, userId, 25.0,
                PrecipitationType.NONE, 3, Set.of(), List.of(candidates.clothes().getFirst(), rejected));
        UUID validId = candidates.clothes().getFirst().id();
        var candidateIds = List.of(validId, rejectedId);
        var search = mock(RecommendationClothesVectorSearch.class);
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(allCandidates);
        given(recommendationService.recommend(allCandidates)).willReturn(basic);
        given(vectorSearch.getIfAvailable()).willReturn(search);
        var condition = new RecommendationCondition(null, List.of(), List.of(), List.of());
        given(openAiRecommendationClient.extractCondition(request.prompt())).willReturn(condition);
        given(queryEmbeddingService.embed(any(), any(), any())).willReturn(List.of(0.1f));
        given(search.search(userId, candidateIds, List.of(0.1f))).willReturn(candidateIds);
        given(clothesVerifier.verify(userId, candidateIds, candidateIds)).willReturn(List.of(validId));
        given(openAiRecommendationClient.generate(request.prompt(), condition, allCandidates,
                List.of(candidates.clothes().getFirst()), Map.of()))
                .willReturn(new RecommendationGenerationResult(List.of(validId), "선택 이유"));

        var result = service.find(userId, request);

        assertThat(result.clothes()).extracting(OotdDto::clothesId).containsExactly(validId);
        assertThat(result.reason()).isEqualTo("선택 이유");
    }

    @Test
    void rejectsUnverifiedGeneratedClothesId() {
        var ids = List.of(candidates.clothes().getFirst().id());
        stubRetrievedIds(ids);
        given(clothesVerifier.verify(userId, ids, ids)).willReturn(ids);
        given(openAiRecommendationClient.generate(
                request.prompt(), emptyCondition, candidates, candidates.clothes(), Map.of()))
                .willReturn(new RecommendationGenerationResult(List.of(UUID.randomUUID()), "허위 추천"));

        assertThat(service.find(userId, request)).isSameAs(basic);
    }

    @Test
    void rejectsEntireGenerationWhenValidAndUnverifiedIdsAreMixed() {
        var validId = candidates.clothes().getFirst().id();
        var ids = List.of(validId);
        stubRetrievedIds(ids);
        given(clothesVerifier.verify(userId, ids, ids)).willReturn(ids);
        given(openAiRecommendationClient.generate(
                request.prompt(), emptyCondition, candidates, candidates.clothes(), Map.of()))
                .willReturn(new RecommendationGenerationResult(List.of(validId, UUID.randomUUID()), "잘못된 조합"));

        assertThat(service.find(userId, request)).isSameAs(basic);
    }

    @Test
    void rejectsDuplicateGeneratedIds() {
        var validId = candidates.clothes().getFirst().id();
        var ids = List.of(validId);
        stubRetrievedIds(ids);
        given(clothesVerifier.verify(userId, ids, ids)).willReturn(ids);
        given(openAiRecommendationClient.generate(
                request.prompt(), emptyCondition, candidates, candidates.clothes(), Map.of()))
                .willReturn(new RecommendationGenerationResult(List.of(validId, validId), "중복 조합"));

        assertThat(service.find(userId, request)).isSameAs(basic);
    }

    @Test
    void rejectsGenerationThatCombinesDressWithTopAndBottom() {
        ClothesDto top = candidates.clothes().getFirst();
        ClothesDto bottom = new ClothesDto(UUID.randomUUID(), userId, "바지", null,
                ClothesType.BOTTOM, false, List.of());
        ClothesDto dress = new ClothesDto(UUID.randomUUID(), userId, "원피스", null,
                ClothesType.DRESS, false, List.of());
        var all = new RecommendationCandidates(weatherId, userId, 25.0,
                PrecipitationType.NONE, 3, Set.of(), List.of(top, bottom, dress));
        var ids = all.clothes().stream().map(ClothesDto::id).toList();
        var search = mock(RecommendationClothesVectorSearch.class);
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(all);
        given(recommendationService.recommend(all)).willReturn(basic);
        given(vectorSearch.getIfAvailable()).willReturn(search);
        given(openAiRecommendationClient.extractCondition(request.prompt())).willReturn(emptyCondition);
        given(queryEmbeddingService.embed(request.prompt(), emptyCondition, all.preferredStyles()))
                .willReturn(List.of(0.1f));
        given(search.search(userId, ids, List.of(0.1f))).willReturn(ids);
        given(clothesVerifier.verify(userId, ids, ids)).willReturn(ids);
        given(openAiRecommendationClient.generate(request.prompt(), emptyCondition, all, all.clothes(), Map.of()))
                .willReturn(new RecommendationGenerationResult(ids, "잘못된 조합"));

        assertThat(service.find(userId, request)).isSameAs(basic);
    }

    @Test
    void keepsLlmSelectionOrderForSubsetOfVerifiedClothes() {
        var first = candidates.clothes().getFirst();
        var second = new ClothesDto(UUID.randomUUID(), userId, "바지", null,
                ClothesType.BOTTOM, false, List.of());
        var third = new ClothesDto(UUID.randomUUID(), userId, "운동화", null,
                ClothesType.SHOES, false, List.of());
        var all = new RecommendationCandidates(weatherId, userId, 25.0,
                PrecipitationType.NONE, 3, Set.of("캐주얼"), List.of(first, second, third));
        var ids = List.of(first.id(), second.id(), third.id());
        var search = mock(RecommendationClothesVectorSearch.class);
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(all);
        given(recommendationService.recommend(all)).willReturn(basic);
        given(vectorSearch.getIfAvailable()).willReturn(search);
        var condition = new RecommendationCondition(null, List.of(), List.of(), List.of());
        given(openAiRecommendationClient.extractCondition(request.prompt())).willReturn(condition);
        given(queryEmbeddingService.embed(any(), any(), any())).willReturn(List.of(0.1f));
        given(search.search(userId, ids, List.of(0.1f))).willReturn(ids);
        given(clothesVerifier.verify(userId, ids, ids)).willReturn(ids);
        given(openAiRecommendationClient.generate(request.prompt(), condition, all, all.clothes(), Map.of()))
                .willReturn(new RecommendationGenerationResult(List.of(third.id(), first.id()), "선택 이유"));

        var result = service.find(userId, request);

        assertThat(result.clothes()).extracting(OotdDto::clothesId)
                .containsExactly(third.id(), first.id());
        assertThat(result.reason()).isEqualTo("선택 이유");
    }

    @Test
    void clientGenerationParsingFailureFallsBackThroughService() throws Exception {
        var factory = mock(ExternalApiClientFactory.class);
        var api = mock(ExternalApiClient.class);
        given(factory.create(eq("llm"), eq(HttpClient.Redirect.NEVER), any())).willReturn(api);
        var realClient = new OpenAiRecommendationClient(factory,
                new RecommendationAiProperties("test-key", "test-model", "https://example.test/v1",
                        "text-embedding-3-small", 1536), new ObjectMapper());
        String conditionResponse = new ObjectMapper().writeValueAsString(java.util.Map.of(
                "status", "completed", "output", List.of(java.util.Map.of(
                        "type", "function_call", "name", "extract_recommendation_condition",
                        "arguments", "{\"occasion\":null,\"styles\":[],\"categories\":[],\"keywords\":[]}"))));
        given(api.post(eq("/responses"), any(), eq(String.class)))
                .willReturn(conditionResponse, """
                        {"status":"completed","output":[{"type":"function_call",
                        "name":"select_recommendation_clothes","arguments":"not-json"}]}
                        """);
        var search = mock(RecommendationClothesVectorSearch.class);
        var ids = List.of(candidates.clothes().getFirst().id());
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(candidates);
        given(recommendationService.recommend(candidates)).willReturn(basic);
        given(vectorSearch.getIfAvailable()).willReturn(search);
        given(queryEmbeddingService.embed(any(), any(), any())).willReturn(List.of(0.1f));
        given(search.search(userId, ids, List.of(0.1f))).willReturn(ids);
        given(clothesVerifier.verify(userId, ids, ids)).willReturn(ids);
        var realService = new AiRecommendationService(recommendationService, realClient,
                queryEmbeddingService, vectorSearch, clothesVerifier);

        assertThat(realService.find(userId, request)).isSameAs(basic);
        verify(api, times(2)).post(eq("/responses"), any(), eq(String.class));
    }

    @Test
    void emptyVerifiedIdsSkipGeneration() {
        var ids = List.of(candidates.clothes().getFirst().id());
        stubRetrievedIds(ids);
        given(clothesVerifier.verify(userId, ids, ids)).willReturn(List.of());

        assertThat(service.find(userId, request)).isSameAs(basic);
        verify(openAiRecommendationClient, never()).generate(any(), any(), any(), any(), anyMap());
    }

    @Test
    void malformedGenerationResultFallsBack() {
        var ids = List.of(candidates.clothes().getFirst().id());
        stubRetrievedIds(ids);
        given(clothesVerifier.verify(userId, ids, ids)).willReturn(ids);
        given(openAiRecommendationClient.generate(
                request.prompt(), emptyCondition, candidates, candidates.clothes(), Map.of()))
                .willReturn(new RecommendationGenerationResult(ids, " "));

        assertThat(service.find(userId, request)).isSameAs(basic);
    }

    @ParameterizedTest
    @EnumSource(value = CommonErrorCode.class, names = {
            "EXTERNAL_API_ERROR", "EXTERNAL_API_TIMEOUT", "EXTERNAL_API_LIMIT_EXCEEDED"
    })
    void generationExternalFailureFallsBack(CommonErrorCode code) {
        var ids = List.of(candidates.clothes().getFirst().id());
        stubRetrievedIds(ids);
        given(clothesVerifier.verify(userId, ids, ids)).willReturn(ids);
        given(openAiRecommendationClient.generate(
                request.prompt(), emptyCondition, candidates, candidates.clothes(), Map.of()))
                .willThrow(new BusinessException(code));

        assertThat(service.find(userId, request)).isSameAs(basic);
    }

    private RecommendationClothesVectorSearch stubRetrievedIds(List<UUID> ids) {
        var search = mock(RecommendationClothesVectorSearch.class);
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(candidates);
        given(recommendationService.recommend(candidates)).willReturn(basic);
        given(vectorSearch.getIfAvailable()).willReturn(search);
        given(openAiRecommendationClient.extractCondition(request.prompt())).willReturn(emptyCondition);
        given(queryEmbeddingService.embed(any(), any(), any())).willReturn(List.of(0.1f));
        given(search.search(userId, ids, List.of(0.1f))).willReturn(ids);
        return search;
    }

    @ParameterizedTest
    @EnumSource(value = CommonErrorCode.class, names = {
            "EXTERNAL_API_ERROR", "EXTERNAL_API_TIMEOUT", "EXTERNAL_API_LIMIT_EXCEEDED"
    })
    void returnsBasicRecommendationWhenExternalAiFails(CommonErrorCode code) {
        enableSearch();
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(candidates);
        given(recommendationService.recommend(candidates)).willReturn(basic);
        given(openAiRecommendationClient.extractCondition(request.prompt()))
                .willThrow(new BusinessException(code));

        assertThat(service.find(userId, request)).isSameAs(basic);

        var order = inOrder(recommendationService, openAiRecommendationClient);
        order.verify(recommendationService).findCandidates(userId, weatherId);
        order.verify(recommendationService).recommend(candidates);
        order.verify(openAiRecommendationClient).extractCondition(request.prompt());
        verifyNoInteractions(queryEmbeddingService, clothesVerifier);
        verifyNoMoreInteractions(recommendationService, openAiRecommendationClient);
    }

    @ParameterizedTest
    @EnumSource(value = CommonErrorCode.class, names = {
            "EXTERNAL_API_ERROR", "EXTERNAL_API_TIMEOUT", "EXTERNAL_API_LIMIT_EXCEEDED"
    })
    void returnsBasicRecommendationWhenQueryEmbeddingFails(CommonErrorCode code) {
        enableSearch();
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(candidates);
        given(recommendationService.recommend(candidates)).willReturn(basic);
        var condition = new RecommendationCondition(null, List.of(), List.of(), List.of());
        given(openAiRecommendationClient.extractCondition(request.prompt())).willReturn(condition);
        given(queryEmbeddingService.embed(request.prompt(), condition, candidates.preferredStyles()))
                .willThrow(new BusinessException(code));

        assertThat(service.find(userId, request)).isSameAs(basic);
        verify(vectorSearch).getIfAvailable();
        verifyNoInteractions(clothesVerifier);
    }

    @Test
    void fallsBackWhenVectorSearchFails() {
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(candidates);
        given(recommendationService.recommend(candidates)).willReturn(basic);
        var condition = new RecommendationCondition(null, List.of(), List.of(), List.of());
        given(openAiRecommendationClient.extractCondition(request.prompt())).willReturn(condition);
        var vector = List.of(0.1f);
        given(queryEmbeddingService.embed(request.prompt(), condition, candidates.preferredStyles()))
                .willReturn(vector);
        var search = mock(RecommendationClothesVectorSearch.class);
        when(vectorSearch.getIfAvailable()).thenReturn(search);
        given(search.search(userId, List.of(candidates.clothes().getFirst().id()), vector))
                .willThrow(new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR));

        assertThat(service.find(userId, request)).isSameAs(basic);
        verifyNoInteractions(clothesVerifier);
    }

    @Test
    void mysqlVerificationFailureIsNotTreatedAsExternalFallback() {
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(candidates);
        given(recommendationService.recommend(candidates)).willReturn(basic);
        var condition = new RecommendationCondition(null, List.of(), List.of(), List.of());
        given(openAiRecommendationClient.extractCondition(request.prompt())).willReturn(condition);
        var vector = List.of(0.1f);
        given(queryEmbeddingService.embed(request.prompt(), condition, candidates.preferredStyles()))
                .willReturn(vector);
        var search = mock(RecommendationClothesVectorSearch.class);
        when(vectorSearch.getIfAvailable()).thenReturn(search);
        var ids = List.of(candidates.clothes().getFirst().id());
        given(search.search(userId, ids, vector)).willReturn(ids);
        var failure = new DataAccessResourceFailureException("MySQL unavailable");
        given(clothesVerifier.verify(userId, ids, ids)).willThrow(failure);

        assertThatThrownBy(() -> service.find(userId, request)).isSameAs(failure);
    }

    @Test
    void emptyVectorSearchResultSkipsMysqlVerification() {
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(candidates);
        given(recommendationService.recommend(candidates)).willReturn(basic);
        var condition = new RecommendationCondition(null, List.of(), List.of(), List.of());
        given(openAiRecommendationClient.extractCondition(request.prompt())).willReturn(condition);
        var vector = List.of(0.1f);
        given(queryEmbeddingService.embed(request.prompt(), condition, candidates.preferredStyles()))
                .willReturn(vector);
        var search = mock(RecommendationClothesVectorSearch.class);
        when(vectorSearch.getIfAvailable()).thenReturn(search);
        given(search.search(userId, List.of(candidates.clothes().getFirst().id()), vector))
                .willReturn(List.of());

        assertThat(service.find(userId, request)).isSameAs(basic);
        verifyNoInteractions(clothesVerifier);
    }

    @Test
    void doesNotHideNonExternalQueryEmbeddingFailure() {
        enableSearch();
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(candidates);
        given(recommendationService.recommend(candidates)).willReturn(basic);
        var condition = new RecommendationCondition(null, List.of(), List.of(), List.of());
        given(openAiRecommendationClient.extractCondition(request.prompt())).willReturn(condition);
        var failure = new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        given(queryEmbeddingService.embed(request.prompt(), condition, candidates.preferredStyles()))
                .willThrow(failure);

        assertThatThrownBy(() -> service.find(userId, request)).isSameAs(failure);
    }

    @Test
    void propagatesMysqlFailureWithoutCallingOpenAi() {
        var failure = new DataAccessResourceFailureException("MySQL unavailable");
        given(recommendationService.findCandidates(userId, weatherId)).willThrow(failure);

        assertThatThrownBy(() -> service.find(userId, request)).isSameAs(failure);
        verifyNoInteractions(openAiRecommendationClient, queryEmbeddingService);
    }

    @Test
    void propagatesMissingWeatherWithoutCallingOpenAi() {
        var failure = new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
        given(recommendationService.findCandidates(userId, weatherId)).willThrow(failure);

        assertThatThrownBy(() -> service.find(userId, request)).isSameAs(failure);
        verifyNoInteractions(openAiRecommendationClient, queryEmbeddingService);
    }

    @Test
    void doesNotHideNonExternalBusinessError() {
        enableSearch();
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(candidates);
        given(recommendationService.recommend(candidates)).willReturn(basic);
        var failure = new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        given(openAiRecommendationClient.extractCondition(request.prompt())).willThrow(failure);

        assertThatThrownBy(() -> service.find(userId, request)).isSameAs(failure);
    }

    @Test
    void doesNotHideUnexpectedProgrammingError() {
        enableSearch();
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(candidates);
        given(recommendationService.recommend(candidates)).willReturn(basic);
        var failure = new IllegalStateException("unexpected error");
        given(openAiRecommendationClient.extractCondition(request.prompt())).willThrow(failure);

        assertThatThrownBy(() -> service.find(userId, request)).isSameAs(failure);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsInvalidPromptBeforeReadingMysql(String prompt) {
        assertInvalidRequest(new RecommendationAiRequest(weatherId, prompt));
    }

    @Test
    void rejectsPromptOver100Characters() {
        assertInvalidRequest(new RecommendationAiRequest(weatherId, "가".repeat(101)));
    }

    @Test
    void rejectsMissingRequest() {
        assertInvalidRequest(null);
    }

    @Test
    void rejectsMissingWeatherId() {
        assertInvalidRequest(new RecommendationAiRequest(null, "추천해줘"));
    }

    private void assertInvalidRequest(RecommendationAiRequest invalidRequest) {
        assertThatThrownBy(() -> service.find(userId, invalidRequest))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(recommendationService, openAiRecommendationClient, queryEmbeddingService);
    }

    private void enableSearch() {
        given(vectorSearch.getIfAvailable()).willReturn(mock(RecommendationClothesVectorSearch.class));
    }
}
