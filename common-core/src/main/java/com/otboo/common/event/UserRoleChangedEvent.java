package com.otboo.common.event;

import com.otboo.user.entity.Role;
import java.time.Instant;
import java.util.UUID;

/** 어드민이 계정 권한을 바꿨다. 알림 대상 = {@code userId}. */
public record UserRoleChangedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID userId,
        Role newRole
) implements DomainEvent {

    public static UserRoleChangedEvent of(UUID userId, Role newRole) {
        return new UserRoleChangedEvent(UUID.randomUUID(), Instant.now(), userId, newRole);
    }

    @Override
    public NotificationType type() {
        return NotificationType.ROLE_CHANGED;
    }
}
