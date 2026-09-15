package com.otboo.feed.search;

import com.otboo.feed.dto.FeedSearchCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 검색 -> Elasticsearch 로 하되, 실패하면 MySQL 로 내려간다.
 * 검색은 피드 <b>목록 화면 전체</b>다. ES 가 죽으면 검색창뿐 아니라 첫 화면이 통째로 500 이 된다.
 * MySQL 구현이 그대로 남아 있으니 느려도 화면은 뜨게 한다.
 * 로그 -> 폴백이 조용하면 ES 반응 x. 형태소 검색이 사라져 검색 품질만 떨어진 채로 돌아감. ERROR 로 남겨 알림에 걸리게 한다.
 *limit
 * 커서는 엔진과 무관하게 {@code (정렬키, id)} 라 페이지 도중에 엔진이 바뀌어도 이어진다.
 * 다만 <b>검색어 조건의 해석이 달라</b>(형태소 vs ngram+LIKE) 결과 집합이 조금 달라질 수 있다.
 */
@Slf4j
@RequiredArgsConstructor
public class FallbackFeedSearch implements FeedSearchPort {

    private final FeedSearchPort primary;
    private final FeedSearchPort fallback;

    @Override
    public FeedSearchResult search(FeedSearchCondition condition) {
        try {
            return primary.search(condition);
        } catch (RuntimeException e) {
            log.error("Elasticsearch 검색에 실패해 MySQL 로 폴백한다. 색인·클러스터 상태를 확인해야 한다.", e);
            return fallback.search(condition);
        }
    }
}
