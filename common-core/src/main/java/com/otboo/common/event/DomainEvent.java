package com.otboo.common.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 모든 도메인 이벤트의 상위 타입.
 *
 * <p><b>sealed 인 이유</b> — 알림 파트가 {@code switch} 로 처리할 때 컴파일러가
 * 빠진 이벤트를 잡아준다. 나중에 9번째 이벤트를 추가하면 소비자 코드가
 * <b>컴파일에 실패해서</b> 처리를 빠뜨릴 수 없다.
 *
 * <pre>
 * String message = switch (event) {
 *     case FeedLikedEvent e -> e.likerId() + "님이 회원님의 피드를 좋아합니다.";
 *     case FollowCreatedEvent e -> ...
 *     // 하나라도 빠지면 컴파일 에러
 * };
 * </pre>
 */
public sealed interface DomainEvent permits
        UserRoleChangedEvent,
        ClothesAttributeAddedEvent,
        FeedLikedEvent,
        FeedCommentedEvent,
        FollowCreatedEvent,
        FollowedUserPostedEvent,
        DirectMessageReceivedEvent,
        VirtualTryOnCompletedEvent {

    /**
     * 이 이벤트의 고유 id. <b>멱등성 키다.</b>
     * 전달이 재시도되면 같은 이벤트가 두 번 오는데, 이 값으로 걸러야 같은 알림이 두 번 생기지 않는다.
     */
    UUID eventId();

    /** 발생 시각(UTC). 전달 시각이 아니라 <b>도메인에서 일어난 시각</b>이다. */
    Instant occurredAt();

    /** 알림으로 변환할 때 쓸 종류. 이벤트가 직접 들고 있어 매핑표가 필요 없다. */
    NotificationType type();
}
