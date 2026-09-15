package com.otboo.feed.search.elasticsearch;

import com.otboo.feed.search.SearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/**
 * Run -> 인덱스를 준비한다.
 *
 * 인덱스 x -> create
 * 없는 인덱스에 문서를 넣으면 ES 가 <b>매핑을 마음대로 추론하므로
 * 먼저 만들어놓는 방법을 선택.
 *
 * Empty -> Fill
 * ES 를 처음 붙이는 시점에는 MySQL 에만 피드가 있다. 이걸 사람이 따로 실행해야 한다면
 * "검색이 0건인데 원인을 모르는" 상태로 시작한다. 다만 <b>비어 있을 때만</b> 한다 —
 * 재기동마다 전량 재색인을 하면 배포가 느려지고 클러스터에 부하만 준다.
 *
 * <h2>실패해도 앱은 뜬다</h2>
 * 검색이 안 되는 것과 서비스 전체가 안 뜨는 것은 무게가 다르다.
 * 검색은 {@code FallbackFeedSearch} 가 MySQL 로 받아 주고, 색인은 나중에 재색인으로 복구한다.
 */
@Slf4j
@RequiredArgsConstructor
public class FeedIndexInitializer implements ApplicationRunner {

    private final FeedIndexManager indexManager;
    private final SearchProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        try {
            boolean exists = indexManager.aliasExists();
            // 비어 있는지 보려면 인덱스가 있어야 한다. 없으면 당연히 0 건이다.
            long indexed = exists ? indexManager.count() : 0;

            if (properties.indexOnStartup() && indexed == 0) {
                // 인덱스를 따로 만들지 않는다. reindexAll 이 새 인덱스를 만들고 별칭까지 붙인다.
                // 여기서 createIndexIfMissing 을 먼저 부르면 빈 v1 을 만들고 곧바로 v2 로 갈아치운다.
                long total = indexManager.reindexAll();
                log.info("피드 초기 색인을 마쳤다. documents={}", total);
                return;
            }
            if (indexManager.createIndexIfMissing()) {
                return;
            }
            log.info("피드 색인이 준비돼 있다. documents={}", indexed);
        } catch (Exception e) {
            log.error("피드 검색 색인 준비에 실패했다. 검색은 MySQL 로 동작하고, "
                    + "ES 복구 후 POST /api/admin/search/feeds/reindex 로 재색인해야 한다.", e);
        }
    }
}
