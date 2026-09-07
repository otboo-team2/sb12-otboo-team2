package com.otboo.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 어드민이 새 의상 속성을 정의했다.
 *
 * <p>⚠️ <b>수신자 정책 미정</b> — 전체 사용자로 갈지 어드민만으로 갈지 아직 정해지지 않았다.
 * 8개 이벤트 중 유일하게 받을 사람이 안 정해진 건이다.
 * 전체로 가면 사용자 수만큼 알림이 생기므로 상한이 필요하다.
 *
 * <p>{@code definitionId} 는 알림을 눌렀을 때 어디로 보낼지에 쓴다.
 */
public record ClothesAttributeAddedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID definitionId,
        String attributeName
) implements DomainEvent {

    public static ClothesAttributeAddedEvent of(UUID definitionId, String attributeName) {
        return new ClothesAttributeAddedEvent(
                UUID.randomUUID(), Instant.now(), definitionId, attributeName);
    }

    @Override
    public NotificationType type() {
        return NotificationType.CLOTHES_ATTRIBUTE_ADDED;
    }
}
