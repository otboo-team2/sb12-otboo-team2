package com.otboo.dm.broadcast;

import com.otboo.dm.entity.DirectMessage;
import java.time.Instant;
import java.util.UUID;

public record DirectMessageBroadcastMessage(
    UUID id,
    Instant createdAt,
    String dmKey,
    UserSummary sender,
    UserSummary receiver,
    String content
) {

    public static DirectMessageBroadcastMessage from(
        DirectMessage message, UserSummary sender, UserSummary receiver
    ) {
        return new DirectMessageBroadcastMessage(
            message.getId(),
            message.getCreatedAt(),
            message.getDmKey(),
            sender,
            receiver,
            message.getContent());
    }

    public record UserSummary(UUID userId, String name, String profileImageUrl) {
    }
}
