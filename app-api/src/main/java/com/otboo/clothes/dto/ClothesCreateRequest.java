package com.otboo.clothes.dto;

import com.otboo.clothes.entity.ClothesType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record ClothesCreateRequest(

        /** 기존 프론트 계약 호환을 위해 받지만 서버는 로그인 사용자와 비교한다. */
        @NotNull(message = "소유자 ID를 입력해주세요.")
        UUID ownerId,

        @NotBlank(message = "의상 이름을 입력해주세요.")
        @Size(max = 500, message = "의상 이름은 500자 이하여야 합니다.")
        String name,

        @NotNull(message = "의상 타입을 선택해주세요.")
        ClothesType type,

        List<@NotNull(message = "속성 항목은 비어 있을 수 없습니다.") @Valid ClothesAttributeDto> attributes,

        @Size(max = 2048, message = "원격 이미지 주소는 2048자 이하여야 합니다.")
        String sourceImageUrl
) {

    public ClothesCreateRequest {
        name = name == null ? null : name.trim();
        attributes = attributes == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(attributes));
        sourceImageUrl = sourceImageUrl == null || sourceImageUrl.isBlank()
                ? null
                : sourceImageUrl.trim();
    }

    public ClothesCreateRequest(
            UUID ownerId,
            String name,
            ClothesType type,
            List<ClothesAttributeDto> attributes
    ) {
        this(ownerId, name, type, attributes, null);
    }
}
