package com.otboo.feed.dto;

import java.util.List;
import java.util.UUID;

/**
 * 의상에 적용된 속성 + 그 속성의 정의.
 *
 * <p>의상 파트의 {@code ClothesDto} 도 같은 모양을 쓴다. {@link AuthorDto} 와 같은 이유로
 *
 * @param selectableValues 그 속성이 가질 수 있는 값 전체.
 */
public record ClothesAttributeWithDefDto(
        UUID definitionId,
        String definitionName,
        List<String> selectableValues,
        String value
) {
}
