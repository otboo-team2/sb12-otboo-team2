package com.otboo.clothes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** 의상 등록 요청에서 선택한 속성 정의와 선택값을 표현한다. */
public record ClothesAttributeDto(

        @NotNull(message = "속성 정의를 선택해주세요.")
        UUID definitionId,

        @NotBlank(message = "속성 선택값을 입력해주세요.")
        String value
) {

    public ClothesAttributeDto {
        value = value == null ? null : value.trim();
    }
}
