package com.otboo.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 가상 피팅 생성이 끝났다. 알림 대상 = {@code requesterId}.
 *
 * <p>규약 초안에 빠져 있던 이벤트다. 생성에 10초~2분이 걸려 요청과 응답을 붙일 수 없으므로,
 * 완료를 알릴 방법이 반드시 필요하다.
 *
 * <p>실패한 경우도 알려야 한다. 사용자가 결과를 계속 기다리게 두면 안 된다.
 */
public record VirtualTryOnCompletedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID requesterId,
        UUID jobId,
        boolean succeeded
) implements DomainEvent {

    public static VirtualTryOnCompletedEvent succeeded(UUID requesterId, UUID jobId) {
        return new VirtualTryOnCompletedEvent(
                UUID.randomUUID(), Instant.now(), requesterId, jobId, true);
    }

    public static VirtualTryOnCompletedEvent failed(UUID requesterId, UUID jobId) {
        return new VirtualTryOnCompletedEvent(
                UUID.randomUUID(), Instant.now(), requesterId, jobId, false);
    }

    @Override
    public NotificationType type() {
        return NotificationType.VIRTUAL_TRY_ON_COMPLETED;
    }
}
