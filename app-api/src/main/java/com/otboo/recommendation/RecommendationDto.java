package com.otboo.recommendation;

import com.otboo.feed.dto.OotdDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

public record RecommendationDto(UUID weatherId, UUID userId, List<OotdDto> clothes,
                                @JsonInclude(JsonInclude.Include.NON_NULL) String reason) {
    public RecommendationDto {
        clothes = List.copyOf(clothes);
    }

    public RecommendationDto(UUID weatherId, UUID userId, List<OotdDto> clothes) {
        this(weatherId, userId, clothes, null);
    }
}
