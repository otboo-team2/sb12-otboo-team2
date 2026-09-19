package com.otboo.recommendation.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.otboo.clothes.entity.ClothesType;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecommendationQueryEmbeddingServiceTest {

    private final OpenAiEmbeddingClient client = mock(OpenAiEmbeddingClient.class);
    private final RecommendationQueryEmbeddingService service = new RecommendationQueryEmbeddingService(client);

    @Test
    void includesRequestConditionsAndSavedPreferredStylesInStableOrder() {
        var condition = new RecommendationCondition(RecommendationOccasion.DATE,
                List.of("미니멀", "캐주얼"), List.of(ClothesType.TOP), List.of("가벼운", "산책"));
        var preferredStyles = Set.of("스트릿", "캐주얼");
        String query = "요청:데이트룩 추천해줘 상황:DATE 스타일:미니멀, 캐주얼 타입:TOP "
                + "키워드:가벼운, 산책 선호 스타일:스트릿, 캐주얼";
        List<Float> vector = Collections.nCopies(1536, 0.1f);
        given(client.embed(query)).willReturn(vector);

        assertThat(service.embed("  데이트룩 추천해줘  ", condition, preferredStyles))
                .hasSize(1536).isSameAs(vector);
        verify(client).embed(query);
    }

    @Test
    void omitsNullAndEmptyConditions() {
        var empty = new RecommendationCondition(null, null, null, null);

        assertThat(RecommendationQueryEmbeddingService.queryText("추천해줘", empty, null))
                .isEqualTo("요청:추천해줘");
        assertThat(RecommendationQueryEmbeddingService.queryText(" 추천해줘 ", null, Set.of()))
                .isEqualTo("요청:추천해줘");
    }

    @Test
    void sameValuesProduceSameTextRegardlessOfInputOrder() {
        var first = new RecommendationCondition(RecommendationOccasion.DAILY,
                List.of("캐주얼", "미니멀"), List.of(ClothesType.TOP, ClothesType.BOTTOM),
                List.of("산책", "가벼운"));
        var reversed = new RecommendationCondition(RecommendationOccasion.DAILY,
                List.of("미니멀", "캐주얼"), List.of(ClothesType.BOTTOM, ClothesType.TOP),
                List.of("가벼운", "산책"));

        assertThat(RecommendationQueryEmbeddingService.queryText("추천해줘", first, Set.of("캐주얼", "스트릿")))
                .isEqualTo(RecommendationQueryEmbeddingService.queryText(
                        "추천해줘", reversed, Set.of("스트릿", "캐주얼")));
    }

    @Test
    void rejectsBlankRequestBeforeCallingEmbedding() {
        assertThatThrownBy(() -> service.embed(" ", null, Set.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE));
        verifyNoInteractions(client);
    }
}
