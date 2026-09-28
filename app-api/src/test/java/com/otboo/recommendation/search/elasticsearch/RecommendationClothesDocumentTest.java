package com.otboo.recommendation.search.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.clothes.dto.ClothesAttributeWithDefDto;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.recommendation.ai.RecommendationClothesMetadata;
import com.otboo.recommendation.ai.RecommendationFormality;
import com.otboo.recommendation.ai.RecommendationOccasion;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationClothesDocumentTest {
    @Test
    void contentIsDeterministicAndDocumentRetainsOnlyMappedFields() throws Exception {
        var color = new ClothesAttributeWithDefDto(UUID.randomUUID(), "색상", List.of("파랑"), "파랑");
        var style = new ClothesAttributeWithDefDto(UUID.randomUUID(), "스타일", List.of("캐주얼"), "캐주얼");
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        var first = new ClothesDto(id, owner, "셔츠", null, ClothesType.TOP, false, List.of(style, color));
        var second = new ClothesDto(id, owner, "셔츠", "changed.jpg", ClothesType.TOP, true, List.of(color, style));
        var document = RecommendationClothesDocument.of(first);
        assertThat(document.content()).isEqualTo("셔츠 타입:TOP 속성:색상=파랑 스타일=캐주얼");
        assertThat(RecommendationClothesDocument.of(second)).isEqualTo(document);
        assertThat(document.clothesId()).isEqualTo(id.toString());
        assertThat(document.ownerId()).isEqualTo(owner.toString());
        assertThat(document.embedding()).isNull();
        var json = new ObjectMapper().valueToTree(document);
        assertThat(json.size()).isEqualTo(8);
        assertThat(json.has("clothesId") && json.has("ownerId") && json.has("type")
                && json.has("content") && json.has("inferredStyles") && json.has("formality")
                && json.has("occasions") && json.has("embedding")).isTrue();
    }

    @Test
    void emptyAttributesKeepContentWithoutAttributeSuffix() {
        var clothes = new ClothesDto(UUID.randomUUID(), UUID.randomUUID(), "셔츠", null,
                ClothesType.TOP, false, List.of());
        assertThat(RecommendationClothesDocument.contentOf(clothes)).isEqualTo("셔츠 타입:TOP");
    }

    @Test
    void metadataIsStoredAndAddedToEmbeddingContentOnlyWhenPresent() {
        var clothes = new ClothesDto(UUID.randomUUID(), UUID.randomUUID(), "로퍼", null,
                ClothesType.SHOES, false, List.of());
        var metadata = new RecommendationClothesMetadata(
                List.of("포멀", "클래식"), RecommendationFormality.HIGH,
                List.of(RecommendationOccasion.FORMAL, RecommendationOccasion.WORK));

        var document = RecommendationClothesDocument.of(clothes, metadata);

        assertThat(document.inferredStyles()).containsExactly("포멀", "클래식");
        assertThat(document.formality()).isEqualTo(RecommendationFormality.HIGH);
        assertThat(document.occasions()).containsExactly(RecommendationOccasion.FORMAL, RecommendationOccasion.WORK);
        assertThat(document.content()).isEqualTo(
                "로퍼 타입:SHOES 추론스타일:클래식,포멀 격식도:HIGH 적합상황:WORK,FORMAL");
    }
}
