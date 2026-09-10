package com.otboo.clothes.dto;

import com.otboo.clothes.entity.ClothesType;
import java.util.List;

/** 구매 링크에서 추출했지만 아직 저장되지 않은 의상 후보 정보다. */
public record ClothesExtractionDto(
        String name,
        ClothesType type,
        List<ExtractedClothesAttributeDto> attributes,
        String imageUrl,
        List<ClothesExtractionFailureDto> failures
) {

    public ClothesExtractionDto {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
        failures = failures == null ? List.of() : List.copyOf(failures);
    }
}
