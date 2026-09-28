package com.otboo.recommendation.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** 자연어 기반 추천 요청. 기존 기본 추천 요청과 분리한다. */
public record RecommendationAiRequest(
        @NotNull(message = "날씨 ID를 입력해주세요.")
        UUID weatherId,

        @NotBlank(message = "추천 요청을 입력해주세요.")
        @Size(max = 100, message = "추천 요청은 100자 이하여야 합니다.")
        String prompt,

        List<UUID> excludeClothesIds,

        List<@NotEmpty(message = "제외할 코디에는 의상이 필요합니다.")
                List<@NotNull(message = "제외할 의상 ID를 입력해주세요.") UUID>> excludedOutfits
) {

    public RecommendationAiRequest {
        prompt = prompt == null ? null : prompt.trim();
        excludeClothesIds = excludeClothesIds == null ? List.of() : List.copyOf(excludeClothesIds);
        excludedOutfits = excludedOutfits == null ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(excludedOutfits));
    }

    public RecommendationAiRequest(UUID weatherId, String prompt) {
        this(weatherId, prompt, List.of(), List.of());
    }

    public RecommendationAiRequest(UUID weatherId, String prompt, List<UUID> excludeClothesIds) {
        this(weatherId, prompt, excludeClothesIds, List.of());
    }
}
