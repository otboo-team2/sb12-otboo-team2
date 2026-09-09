package com.otboo.notification.broadcast;

import com.otboo.notification.entity.Notification;
import com.otboo.notification.entity.NotificationLevel;
import java.time.Instant;
import java.util.UUID;

public record NotificationBroadcastMessage(
    UUID id,
    Instant createdAt,
    UUID receiverId,
    String title,
    String content,
    NotificationLevel level
) {
    public static NotificationBroadcastMessage from(Notification notification) {
        return new NotificationBroadcastMessage(
            notification.getId(),
            notification.getCreatedAt(),
            notification.getReceiver().getId(),
            notification.getTitle(),
            notification.getContent(),
            notification.getLevel());
    }
}
