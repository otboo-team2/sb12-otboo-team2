package com.otboo.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 피드 수정 요청. */
public record FeedUpdateRequest(
        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 2000, message = "내용은 2000자를 넘을 수 없습니다.")
        String content
) {
}
