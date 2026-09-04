package com.otboo.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * DM 을 받았다. 알림 대상 = {@code receiverId}.
 *
 * <p>규약 초안에 빠져 있던 이벤트다. 스펙상 알림 대상인데 목록에 없었다.
 *
 * <p>⚠️ 대화창을 열어둔 사람에게는 알림이 필요 없다. 그 판단은 소비자가 한다.
 */
public record DirectMessageReceivedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID senderId,
        UUID receiverId,
        UUID directMessageId
) implements DomainEvent {

    public static DirectMessageReceivedEvent of(UUID senderId, UUID receiverId,
                                                UUID directMessageId) {
        return new DirectMessageReceivedEvent(
                UUID.randomUUID(), Instant.now(), senderId, receiverId, directMessageId);
    }

    @Override
    public NotificationType type() {
        return NotificationType.DM_RECEIVED;
    }
}
