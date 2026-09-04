package com.otboo.common.event;

import java.time.Instant;
import java.util.UUID;

/** 누군가 나를 팔로우했다. 알림 대상 = {@code followeeId}. */
public record FollowCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID followerId,
        UUID followeeId
) implements DomainEvent {

    public static FollowCreatedEvent of(UUID followerId, UUID followeeId) {
        return new FollowCreatedEvent(UUID.randomUUID(), Instant.now(), followerId, followeeId);
    }

    @Override
    public NotificationType type() {
        return NotificationType.FOLLOW_CREATED;
    }
}
