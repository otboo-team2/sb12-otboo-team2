package com.otboo.clothes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ClothesAttributeDefUpdateRequest(
        @Size(max = 100)
        String name,
        List<@Valid @NotBlank @Size(max = 100) String> selectableValues
) {

    public boolean hasChanges() {
        return name != null || selectableValues != null;
    }
}
