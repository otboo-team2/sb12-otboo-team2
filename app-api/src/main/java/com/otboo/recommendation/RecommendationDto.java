package com.otboo.recommendation;

import com.otboo.feed.dto.OotdDto;
import java.util.List;
import java.util.UUID;

public record RecommendationDto(UUID weatherId, UUID userId, List<OotdDto> clothes) {
    public RecommendationDto {
        clothes = List.copyOf(clothes);
    }
}
