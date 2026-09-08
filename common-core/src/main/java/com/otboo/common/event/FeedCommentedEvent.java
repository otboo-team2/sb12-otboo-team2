package com.otboo.common.event;

import java.time.Instant;
import java.util.UUID;

/** 누군가 피드에 댓글을 달았다. 알림 대상 = {@code feedOwnerId}. */
public record FeedCommentedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID feedOwnerId,
        UUID commenterId,
        UUID feedId,
        UUID commentId
) implements DomainEvent {

    public static FeedCommentedEvent of(UUID feedOwnerId, UUID commenterId,
                                        UUID feedId, UUID commentId) {
        return new FeedCommentedEvent(
                UUID.randomUUID(), Instant.now(), feedOwnerId, commenterId, feedId, commentId);
    }

    @Override
    public NotificationType type() {
        return NotificationType.FEED_COMMENTED;
    }
}
