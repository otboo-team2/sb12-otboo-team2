package com.otboo.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 누군가 피드에 좋아요를 눌렀다. 알림 대상 = {@code feedOwnerId}.
 *
 * <p>자기 피드에 자기가 누른 경우는 소비자가 걸러낸다. 자기 행동을 자기에게 알리지 않는다.
 */
public record FeedLikedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID feedOwnerId,
        UUID likerId,
        UUID feedId
) implements DomainEvent {

    public static FeedLikedEvent of(UUID feedOwnerId, UUID likerId, UUID feedId) {
        return new FeedLikedEvent(UUID.randomUUID(), Instant.now(), feedOwnerId, likerId, feedId);
    }

    @Override
    public NotificationType type() {
        return NotificationType.FEED_LIKED;
    }
}
