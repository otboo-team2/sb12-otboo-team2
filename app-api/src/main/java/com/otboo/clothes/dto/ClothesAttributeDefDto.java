package com.otboo.clothes.dto;

import com.otboo.clothes.entity.ClothesAttributeDefinition;
import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ClothesAttributeDefDto(
        UUID id,
        Instant createdAt,
        String name,
        List<String> selectableValues,
        List<UUID> selectableValueIds
) {

    public static ClothesAttributeDefDto from(ClothesAttributeDefinition definition) {
        return new ClothesAttributeDefDto(
                definition.getId(),
                definition.getCreatedAt(),
                definition.getName(),
                definition.getSelectableValues().stream()
                    .map(value -> value.getValue())
                    .toList(), definition
                    .getSelectableValues()
                    .stream()
                    .map(value -> value.getId())
                    .toList()
        );
    }

    public static ClothesAttributeDefDto from(
            ClothesAttributeDefinition definition,
            Collection<com.otboo.clothes.entity.ClothesAttributeSelectableValue> selectableValues
    ) {
        return new ClothesAttributeDefDto(
                definition.getId(),
                definition.getCreatedAt(),
                definition.getName(),
                selectableValues.stream().map(value -> value.getValue()).toList(),
                selectableValues.stream().map(value -> value.getId()).toList()
        );
    }
}
