package com.otboo.feed.search.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 피드 한 건의 색인 · 삭제.
 *
 * <h2>실패해도 예외를 밖으로 던지지 않는다</h2>
 * 색인은 피드 등록·좋아요의 <b>부수 효과</b>다. ES 가 잠깐 죽었다고 사용자의 글쓰기가 실패하면
 * 안 된다. 원본(MySQL)은 이미 커밋됐고, 어긋난 색인은 재색인으로 언제든 복구할 수 있다.
 *
 * <p>대신 실패를 <b>반드시 로그로 남긴다.</b> 조용히 삼키면 "검색에 안 잡히는 피드" 가 생겨도
 * 아무도 모른다. 로그 레벨이 WARN 인 것은 알림을 띄울 만큼 급하진 않지만
 * 쌓이면 재색인이 필요하다는 신호이기 때문이다.
 */
@Slf4j
@RequiredArgsConstructor
public class FeedIndexer {

    private final ElasticsearchClient client;
    private final FeedDocumentLoader documentLoader;
    private final FeedIndexManager indexManager;

    /**
     * 피드를 색인한다. MySQL 에서 현재 값을 다시 읽으므로 <b>이벤트에 값을 실어 보낼 필요가 없다</b>
     * — 이벤트가 순서를 바꿔 도착해도 마지막 색인이 항상 최신 상태를 넣는다.
     */
    public void index(UUID feedId) {
        try {
            List<FeedDocument> documents = documentLoader.loadByIds(List.of(feedId));
            if (documents.isEmpty()) {
                // 색인 직전에 지워졌다. 색인에 남지 않도록 삭제로 처리한다.
                delete(feedId);
                return;
            }
            FeedDocument document = documents.getFirst();
            client.index(request -> request
                    .index(indexManager.alias())
                    .id(document.id())
                    .document(document));
        } catch (IOException | RuntimeException e) {
            log.warn("피드 색인에 실패했다. 검색 결과가 원본과 어긋난다. feedId={}", feedId, e);
        }
    }

    public void delete(UUID feedId) {
        try {
            client.delete(request -> request
                    .index(indexManager.alias())
                    .id(feedId.toString()));
        } catch (IOException | RuntimeException e) {
            log.warn("피드 색인 삭제에 실패했다. 지워진 피드가 검색에 남는다. feedId={}", feedId, e);
        }
    }
}
