package com.otboo.recommendation.search.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.clothes.dto.ClothesAttributeWithDefDto;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.ClothesType;
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
        assertThat(json.size()).isEqualTo(5);
        assertThat(json.has("clothesId") && json.has("ownerId") && json.has("type")
                && json.has("content") && json.has("embedding")).isTrue();
    }

    @Test
    void emptyAttributesKeepContentWithoutAttributeSuffix() {
        var clothes = new ClothesDto(UUID.randomUUID(), UUID.randomUUID(), "셔츠", null,
                ClothesType.TOP, false, List.of());
        assertThat(RecommendationClothesDocument.contentOf(clothes)).isEqualTo("셔츠 타입:TOP");
    }
}
