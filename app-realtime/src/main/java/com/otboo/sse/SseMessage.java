package com.otboo.sse;

import com.otboo.notification.broadcast.NotificationBroadcastMessage;
import java.util.UUID;

public record SseMessage(
    UUID eventId,
    UUID receiverId,
    NotificationBroadcastMessage data
) {
    public static SseMessage of(NotificationBroadcastMessage data) {
        return new SseMessage(data.id(), data.receiverId(), data);
    }
}
