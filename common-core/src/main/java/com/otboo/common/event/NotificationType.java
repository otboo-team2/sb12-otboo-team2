package com.otboo.common.event;

/**
 * 알림 종류. DB {@code notifications.type} 의 CHECK 제약과 값이 일치해야 한다.
 *
 * <p>프론트는 SSE 이벤트 이름 {@code notifications} <b>하나만</b> 듣는다.
 * 알림 종류는 이 값으로 구분하지, SSE 이벤트 이름으로 구분하지 않는다.
 */
public enum NotificationType {

    ROLE_CHANGED,
    CLOTHES_ATTRIBUTE_ADDED,
    FEED_LIKED,
    FEED_COMMENTED,
    FOLLOW_CREATED,
    /** 팔로우한 사람이 새 피드를 올림 */
    FEED_CREATED,
    DM_RECEIVED,
    VIRTUAL_TRY_ON_COMPLETED
}
