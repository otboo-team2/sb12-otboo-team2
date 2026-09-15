package com.otboo.feed.search;

import java.util.UUID;

/**
 * 피드의 검색 색인을 갱신해야 한다는 신호.
 *
 * <h2>{@code DomainEvent} 에 넣지 않은 이유</h2>
 * {@code DomainEvent} 는 sealed 이고 <b>알림으로 바뀌는 이벤트</b>만 담는다. 색인 갱신은
 * 사용자에게 보이는 사건이 아니라 내부 정합성 작업이라, 거기에 끼우면 알림 파트의
 * {@code switch} 가 "알림을 만들지 않는 이벤트" 를 처리해야 한다.
 *
 * <h2>값을 싣지 않는다</h2>
 * {@code feedId} 하나만 보낸다. 소비자가 MySQL 에서 현재 값을 다시 읽으므로 이벤트 순서가
 * 뒤바뀌어도 마지막 처리가 최신 상태를 색인한다. 값을 실어 보내면 늦게 도착한 옛 이벤트가
 * <b>새 값을 옛 값으로 덮어쓴다.</b>
 */
public record FeedIndexEvent(UUID feedId, Operation operation) {

    public enum Operation {
        /** 등록 · 수정 · 좋아요 수 변경. 색인에 현재 상태를 덮어쓴다. */
        UPSERT,
        /** 삭제. 색인에서 지운다. */
        DELETE
    }

    public static FeedIndexEvent upsert(UUID feedId) {
        return new FeedIndexEvent(feedId, Operation.UPSERT);
    }

    public static FeedIndexEvent delete(UUID feedId) {
        return new FeedIndexEvent(feedId, Operation.DELETE);
    }
}
