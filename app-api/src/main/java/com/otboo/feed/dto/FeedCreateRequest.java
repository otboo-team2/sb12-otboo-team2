package com.otboo.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
public record FeedCreateRequest(
        UUID authorId,

        @NotNull(message = "날씨 정보가 필요합니다.")
        UUID weatherId,

        List<UUID> clothesIds,

        @NotBlank(message = "내용을 입력해 주세요.")
        @Size(max = 2000, message = "내용은 2000자를 넘을 수 없습니다.")
        String content
) {

    public List<UUID> clothesIdsOrEmpty() {
        return clothesIds == null ? List.of() : clothesIds;
    }
}
