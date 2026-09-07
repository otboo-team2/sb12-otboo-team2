package com.otboo.clothes.dto;

import com.otboo.clothes.entity.ClothesType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.List;

/** null 필드는 유지, 빈 attributes 배열은 기존 속성 전체 제거를 뜻한다. */
public record ClothesUpdateRequest(
        @Size(min = 1, max = 500, message = "의상 이름은 1~500자여야 합니다.")
        String name,
        ClothesType type,
        List<@jakarta.validation.constraints.NotNull(
                message = "속성 항목은 비어 있을 수 없습니다.") @Valid ClothesAttributeDto> attributes
) {

    public ClothesUpdateRequest {
        name = name == null ? null : name.trim();
        attributes = attributes == null ? null : Collections.unmodifiableList(attributes);
    }

    public boolean hasChanges() {
        return name != null || type != null || attributes != null;
    }
}
