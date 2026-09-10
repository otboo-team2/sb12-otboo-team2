package com.otboo.clothes.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.clothes.entity.ClothesType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClothesExtractionContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesExtractionCandidateWithoutPersistenceFields() {
        UUID definitionId = UUID.randomUUID();
        ClothesExtractionDto dto = new ClothesExtractionDto(
                "세미 와이드 데님",
                ClothesType.BOTTOM,
                List.of(new ExtractedClothesAttributeDto(
                        definitionId,
                        "핏",
                        "세미와이드",
                        "세미 와이드 핏",
                        ClothesExtractionSource.DETAIL_IMAGE)),
                "https://cdn.example.com/product.jpg",
                List.of());

        JsonNode json = objectMapper.valueToTree(dto);

        assertThat(json.has("id")).isFalse();
        assertThat(json.has("ownerId")).isFalse();
        assertThat(json.at("/attributes/0/definitionId").asText())
                .isEqualTo(definitionId.toString());
        assertThat(json.at("/attributes/0/source").asText()).isEqualTo("DETAIL_IMAGE");
    }

    @Test
    void oldCreateRequestConstructorLeavesSourceImageUrlEmpty() {
        ClothesCreateRequest request = new ClothesCreateRequest(
                UUID.randomUUID(), "티셔츠", ClothesType.TOP, List.of());

        assertThat(request.sourceImageUrl()).isNull();
    }

    @Test
    void extractionCandidateNormalizesNullableCollections() {
        ClothesExtractionDto dto = new ClothesExtractionDto(
                "셔츠", ClothesType.TOP, null, null, null);

        assertThat(dto.attributes()).isEmpty();
        assertThat(dto.failures()).isEmpty();
    }

    @Test
    void sourceImageUrlIsTrimmedAndBlankValueBecomesNull() {
        ClothesCreateRequest trimmed = new ClothesCreateRequest(
                UUID.randomUUID(), "셔츠", ClothesType.TOP, List.of(),
                "  https://cdn.example.com/shirt.jpg  ");
        ClothesCreateRequest blank = new ClothesCreateRequest(
                UUID.randomUUID(), "셔츠", ClothesType.TOP, List.of(), "   ");

        assertThat(trimmed.sourceImageUrl()).isEqualTo("https://cdn.example.com/shirt.jpg");
        assertThat(blank.sourceImageUrl()).isNull();
    }
}
