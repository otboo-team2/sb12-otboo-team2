package com.otboo.dm.dto;

import java.util.UUID;

public record UserSummary(
    UUID userId,
    String name,
    String profileImageUrl
) {
}
