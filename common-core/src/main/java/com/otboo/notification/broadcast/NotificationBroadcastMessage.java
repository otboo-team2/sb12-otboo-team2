package com.otboo.notification.broadcast;

import com.otboo.notification.entity.Notification;
import com.otboo.notification.entity.NotificationLevel;
import com.otboo.notification.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

public record NotificationBroadcastMessage(
    UUID id,
    Instant createdAt,
    UUID receiverId,
    UUID actorId,
    String title,
    String content,
    NotificationLevel level,
    NotificationType type,
    String relatedEntityId
) {
    public static NotificationBroadcastMessage from(Notification notification) {
        return new NotificationBroadcastMessage(
            notification.getId(),
            notification.getCreatedAt(),
            notification.getReceiver().getId(),
            notification.getActor() != null ? notification.getActor().getId() : null,
            notification.getTitle(),
            notification.getContent(),
            notification.getLevel(),
            notification.getType(),
            notification.getRelatedEntityId());
    }
}
