package com.otboo.notification.dto;

import com.otboo.notification.entity.Notification;
import com.otboo.notification.entity.NotificationLevel;

import java.time.Instant;
import java.util.UUID;

public record NotificationDto(
    UUID id,
    Instant createdAt,
    UUID receiverId,
    String title,
    String content,
    NotificationLevel level
) {

    public static NotificationDto from(Notification notification) {
        return new NotificationDto(
            notification.getId(),
            notification.getCreatedAt(),
            notification.getReceiver().getId(),
            notification.getTitle(),
            notification.getContent(),
            notification.getLevel()
        );
    }
}
