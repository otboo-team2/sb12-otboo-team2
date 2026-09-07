package com.otboo.clothes.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ClothesAttributeDefCreateRequest(
        @NotBlank
        @Size(max = 100)
        String name,
        @NotEmpty
        List<@Valid @NotBlank @Size(max = 100) String> selectableValues
) {
}
