package com.otboo.virtualtryon.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record VirtualTryOnRequest(
    @NotNull UUID topClothesId,
    @NotNull UUID bottomClothesId,
    UUID additionalClothesId
) {}
