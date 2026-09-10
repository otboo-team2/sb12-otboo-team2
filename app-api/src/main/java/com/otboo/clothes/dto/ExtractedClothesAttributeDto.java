package com.otboo.clothes.dto;

import java.util.UUID;

/** 구매 링크에서 추출한 의상 속성 후보 정보다. */
public record ExtractedClothesAttributeDto(
        UUID definitionId,
        String definitionName,
        String value,
        String evidence,
        ClothesExtractionSource source
) {
}
