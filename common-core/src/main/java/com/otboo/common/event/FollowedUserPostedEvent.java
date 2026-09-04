package com.otboo.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 내가 팔로우한 사람이 새 피드를 올렸다.
 *
 * <p>⚠️ <b>이벤트 1개 → 알림 N개</b>인 유일한 건이다. 알림 대상은 {@code authorId} 의 팔로워 전원이다.
 * 팔로워 목록 조회와 알림 생성은 <b>소비자(알림 파트)가 한다.</b>
 * 피드 파트에서 팔로워를 조회해 for 문을 돌리면 안 된다 — 도메인이 남의 도메인을 알게 되고,
 * 저장 트랜잭션이 팔로워 수만큼 길어진다.
 */
public record FollowedUserPostedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID authorId,
        UUID feedId
) implements DomainEvent {

    public static FollowedUserPostedEvent of(UUID authorId, UUID feedId) {
        return new FollowedUserPostedEvent(UUID.randomUUID(), Instant.now(), authorId, feedId);
    }

    @Override
    public NotificationType type() {
        return NotificationType.FEED_CREATED;
    }
}
