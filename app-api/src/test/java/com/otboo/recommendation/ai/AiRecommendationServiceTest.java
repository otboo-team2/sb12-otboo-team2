package com.otboo.recommendation.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.feed.dto.OotdDto;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.recommendation.RecommendationCandidates;
import com.otboo.weather.PrecipitationType;
import com.otboo.recommendation.RecommendationDto;
import com.otboo.recommendation.RecommendationService;
import java.util.List;
import java.util.Set;
import java.util.UUID;
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

@ExtendWith(MockitoExtension.class)
class AiRecommendationServiceTest {

    @Mock RecommendationService recommendationService;
    @Mock OpenAiRecommendationClient openAiRecommendationClient;
    @InjectMocks AiRecommendationService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID weatherId = UUID.randomUUID();
    private final RecommendationAiRequest request = new RecommendationAiRequest(weatherId, "데이트룩 추천해줘");
    private final RecommendationDto basic = new RecommendationDto(weatherId, userId,
            List.of(new OotdDto(UUID.randomUUID(), "셔츠", null, "TOP", List.of())));
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
        verifyNoInteractions(openAiRecommendationClient);
    }

    @Test
    void readsBasicRecommendationBeforeExtractingCondition() {
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(candidates);
        given(recommendationService.recommend(candidates)).willReturn(basic);
        given(openAiRecommendationClient.extractCondition(request.prompt())).willReturn(
                new RecommendationCondition(RecommendationOccasion.DATE,
                        List.of("캐주얼"), List.of(), List.of()));

        assertThat(service.find(userId, request)).isSameAs(basic);

        var order = inOrder(recommendationService, openAiRecommendationClient);
        order.verify(recommendationService).findCandidates(userId, weatherId);
        order.verify(recommendationService).recommend(candidates);
        order.verify(openAiRecommendationClient).extractCondition(request.prompt());
        verifyNoMoreInteractions(recommendationService, openAiRecommendationClient);
    }

    @ParameterizedTest
    @EnumSource(value = CommonErrorCode.class, names = {
            "EXTERNAL_API_ERROR", "EXTERNAL_API_TIMEOUT", "EXTERNAL_API_LIMIT_EXCEEDED"
    })
    void returnsBasicRecommendationWhenExternalAiFails(CommonErrorCode code) {
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(candidates);
        given(recommendationService.recommend(candidates)).willReturn(basic);
        given(openAiRecommendationClient.extractCondition(request.prompt()))
                .willThrow(new BusinessException(code));

        assertThat(service.find(userId, request)).isSameAs(basic);

        var order = inOrder(recommendationService, openAiRecommendationClient);
        order.verify(recommendationService).findCandidates(userId, weatherId);
        order.verify(recommendationService).recommend(candidates);
        order.verify(openAiRecommendationClient).extractCondition(request.prompt());
        verifyNoMoreInteractions(recommendationService, openAiRecommendationClient);
    }

    @Test
    void propagatesMysqlFailureWithoutCallingOpenAi() {
        var failure = new DataAccessResourceFailureException("MySQL unavailable");
        given(recommendationService.findCandidates(userId, weatherId)).willThrow(failure);

        assertThatThrownBy(() -> service.find(userId, request)).isSameAs(failure);
        verifyNoInteractions(openAiRecommendationClient);
    }

    @Test
    void propagatesMissingWeatherWithoutCallingOpenAi() {
        var failure = new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);
        given(recommendationService.findCandidates(userId, weatherId)).willThrow(failure);

        assertThatThrownBy(() -> service.find(userId, request)).isSameAs(failure);
        verifyNoInteractions(openAiRecommendationClient);
    }

    @Test
    void doesNotHideNonExternalBusinessError() {
        given(recommendationService.findCandidates(userId, weatherId)).willReturn(candidates);
        given(recommendationService.recommend(candidates)).willReturn(basic);
        var failure = new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        given(openAiRecommendationClient.extractCondition(request.prompt())).willThrow(failure);

        assertThatThrownBy(() -> service.find(userId, request)).isSameAs(failure);
    }

    @Test
    void doesNotHideUnexpectedProgrammingError() {
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
        verifyNoInteractions(recommendationService, openAiRecommendationClient);
    }
}
