package com.otboo.recommendation.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** 자연어 기반 추천 요청. 기존 기본 추천 요청과 분리한다. */
public record RecommendationAiRequest(
        @NotNull(message = "날씨 ID를 입력해주세요.")
        UUID weatherId,

        @NotBlank(message = "추천 요청을 입력해주세요.")
        @Size(max = 100, message = "추천 요청은 100자 이하여야 합니다.")
        String prompt
) {

    public RecommendationAiRequest {
        prompt = prompt == null ? null : prompt.trim();
    }
}
