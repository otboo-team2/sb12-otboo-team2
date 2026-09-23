package com.otboo.clothes.dto;

import java.util.List;
import java.util.UUID;

/** 의상 응답에서 속성 정의 정보까지 함께 보여주는 형태다. */
public record ClothesAttributeWithDefDto(
        UUID definitionId,
        String definitionName,
        List<String> selectableValues,
        String value
) {

    public ClothesAttributeWithDefDto {
        selectableValues = List.copyOf(selectableValues);
    }
}
