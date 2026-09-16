package com.otboo.feed.search;

import com.otboo.feed.search.elasticsearch.FeedIndexManager;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 색인 운영용 엔드포인트. 관리자만 호출가능.({@code SecurityConfig}).
 * 색인 =! 원본 —> ES 가 잠깐 죽은 사이의 글(바뀐 매핑,DB)
 * 그때 <b>앱을 재기동해야만 복구된다면</b> 운영에서 사용 불가.
 *
 * <p>재색인은 동기로 돈다. 피드가 수십만 건이 되면 요청 타임아웃에 걸리므로 그때는
 * 배치(app-batch)로 옮겨야 한다. 지금 규모에서 비동기 job 관리를 먼저 만들 이유 x.
 */
@RestController
@RequestMapping("/api/admin/search/feeds")
@RequiredArgsConstructor
// MySQL 검색에는 색인이 없다. 조건을 빼면 engine=mysql 인 환경에서 FeedIndexManager 를
// 주입받지 못해 애플리케이션이 아예 뜨지 않는다.
@ConditionalOnProperty(prefix = "otboo.search", name = "engine", havingValue = "elasticsearch")
public class FeedSearchAdminController {

    private final FeedIndexManager indexManager;

    /** 색인 상태 확인. 문서 수가 MySQL 의 피드 수와 크게 다르면 재색인 신호다. */
    @GetMapping("/status")
    public Map<String, Object> status() throws IOException {
        Set<String> indices = indexManager.indices();
        return Map.of(
                "alias", indexManager.alias(),
                "indices", indices,
                "documents", indices.isEmpty() ? 0L : indexManager.count());
    }

    /** 전체 재색인. 새 인덱스에 다 넣은 뒤 nickname을 옮기므로 도중에도 검색은 계속된다. */
    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindex() throws IOException {
        long indexed = indexManager.reindexAll();
        return ResponseEntity.ok(Map.of(
                "alias", indexManager.alias(),
                "indices", indexManager.indices(),
                "documents", indexed));
    }
}
