package com.otboo.feed.dto;

import java.util.List;
import java.util.UUID;

/**
 * 피드에 포함된 outfit
 *
 * @param type TOP / BOTTOM / ... / ETC
 */
public record OotdDto(
        UUID clothesId,
        String name,
        String imageUrl,
        String type,
        List<ClothesAttributeWithDefDto> attributes
) {
}
