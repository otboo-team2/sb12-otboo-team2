package com.otboo.dm.dto;

import java.time.Instant;
import java.util.UUID;

public record DmConversationDto(
    UUID messageId,
    Instant lastMessageAt,
    String lastMessageContent,
    UserSummary partner
) {
}
