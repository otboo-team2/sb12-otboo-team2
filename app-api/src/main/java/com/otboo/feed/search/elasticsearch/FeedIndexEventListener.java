package com.otboo.feed.search.elasticsearch;

import com.otboo.feed.search.FeedIndexEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 색인 갱신 이벤트를 받아 실제로 색인한다.
 * {@code AFTER_COMMIT}
 * 트랜잭션 안에서 색인하면 <b>롤백된 피드가 검색에 남는다.
 * 잠깐 늦을 뿐이고 재색인으로 복구된다. 없는 것을 보여주는 쪽은 복구가 안 된다.
 * {@code @Async}
 * 커밋 뒤라도 같은 스레드에서 돌면 ES 응답을 기다리는 만큼 <b>사용자의 응답이 늦어진다.</b>
 * 색인은 사용자가 기다릴 이유가 없는 작업이다.
 *
 * <p>⚠️ 그래서 <b>등록 직후 검색하면 아직 안 나올 수 있다.
 * ES 의 기본 refresh 주기(1초)까지 더하면 최대 1~2초.
 * 스펙상 등록 응답은 목록 재조회가 아니라 단건 응답(MySQL)이라
 * 화면에는 곧바로 보이고, 목록 검색만 잠깐 늦는다.
 */
@RequiredArgsConstructor
public class FeedIndexEventListener {

    private final FeedIndexer indexer;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(FeedIndexEvent event) {
        switch (event.operation()) {
            case UPSERT -> indexer.index(event.feedId());
            case DELETE -> indexer.delete(event.feedId());
        }
    }
}
