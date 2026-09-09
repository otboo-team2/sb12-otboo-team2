package com.otboo.clothes.dto;

import com.otboo.clothes.entity.Clothes;
import com.otboo.clothes.entity.ClothesType;
import java.util.List;
import java.util.UUID;

public record ClothesDto(
        UUID id,
        UUID ownerId,
        String name,
        String imageUrl,
        ClothesType type,
        boolean favorite,
        List<ClothesAttributeWithDefDto> attributes
) {

    public ClothesDto {
        attributes = List.copyOf(attributes);
    }

    public static ClothesDto withoutAttributes(Clothes clothes) {
        return new ClothesDto(
                clothes.getId(),
                clothes.getOwnerId(),
                clothes.getName(),
                clothes.getImageUrl(),
                clothes.getType(),
                clothes.isFavorite(),
                List.of()
        );
    }
}
