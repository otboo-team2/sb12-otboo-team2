package com.otboo.recommendation.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.storage.ImageStorage;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationClothesMetadataAnalyzerTest {

    private final OpenAiRecommendationClothesMetadataClient client =
            mock(OpenAiRecommendationClothesMetadataClient.class);
    private final ImageStorage imageStorage = mock(ImageStorage.class);
    private final RecommendationClothesMetadataAnalyzer analyzer =
            new RecommendationClothesMetadataAnalyzer(client, imageStorage);

    @Test
    void imageIsReadAsDataUriBeforeAnalysis() {
        var clothes = clothes("/images/clothes/test.jpg");
        var expected = new RecommendationClothesMetadata(
                List.of("캐주얼"), RecommendationFormality.LOW, List.of(RecommendationOccasion.DAILY));
        when(imageStorage.readAsDataUri(clothes.imageUrl())).thenReturn("data:image/jpeg;base64,AA==");
        when(client.analyze(clothes, "data:image/jpeg;base64,AA==")).thenReturn(expected);

        assertThat(analyzer.analyze(clothes)).isEqualTo(expected);
    }

    @Test
    void missingImageUsesTextOnlyAnalysis() {
        var clothes = clothes(null);
        when(client.analyze(clothes, null)).thenReturn(RecommendationClothesMetadata.EMPTY);

        assertThat(analyzer.analyze(clothes)).isEqualTo(RecommendationClothesMetadata.EMPTY);
        verify(client).analyze(clothes, null);
    }

    @Test
    void externalFailureReturnsEmptyMetadataSoIndexingCanContinue() {
        var clothes = clothes(null);
        when(client.analyze(clothes, null)).thenThrow(new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR));

        assertThat(analyzer.analyze(clothes)).isEqualTo(RecommendationClothesMetadata.EMPTY);
    }

    private ClothesDto clothes(String imageUrl) {
        return new ClothesDto(UUID.randomUUID(), UUID.randomUUID(), "셔츠", imageUrl,
                ClothesType.TOP, false, List.of());
    }
}
