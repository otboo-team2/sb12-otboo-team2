package com.otboo.virtualtryon.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FashnRunRequest(String modelName, Inputs inputs) {

    public static FashnRunRequest of(String modelImage, String productImage, String prompt) {
        return new FashnRunRequest("tryon-max", new Inputs(
            productImage, modelImage, prompt, "1k", "balanced", 42, 1, "png", false));
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Inputs(
        String productImage, String modelImage, String prompt,
        String resolution, String generationMode, int seed,
        int numImages, String outputFormat, boolean returnBase64
    ) {}
}
