package com.otboo.recommendation.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.recommendation.search.elasticsearch.RecommendationClothesDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationClothesEmbeddingServiceTest {

    private final OpenAiEmbeddingClient client = mock(OpenAiEmbeddingClient.class);
    private final RecommendationClothesEmbeddingService service =
            new RecommendationClothesEmbeddingService(client);

    @Test
    void embedsDocumentContent() {
        var document = new RecommendationClothesDocument(
                "clothes-id", "owner-id", "TOP", "셔츠 타입:TOP",
                List.of(), null, List.of(), null);
        given(client.embed(document.content())).willReturn(List.of(1.0f, 2.0f));

        assertThat(service.embed(document)).containsExactly(1.0f, 2.0f);
    }

    @Test
    void rejectsNullDocument() {
        assertThatThrownBy(() -> service.embed(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE));
    }
}
