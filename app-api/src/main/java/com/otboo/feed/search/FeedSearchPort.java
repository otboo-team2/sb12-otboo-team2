package com.otboo.feed.search;

import com.otboo.feed.dto.FeedSearchCondition;
import java.util.List;
import java.util.UUID;

/**
 * 피드 검색. 지금 구현은 {@link MysqlFeedSearch} 하나다.
 *
 * <h2>사용 시점</h2>
 * 검색 엔진의 책임을 "조건에 맞는 피드를 <b>순서대로 골라내는 것</b>" 하나로 좁혀 두기 위해서다.
 * 응답 본문 조립은 {@code FeedViewLoader} 가 전담하므로, 나중에 검색을 OpenSearch 같은
 * 외부 엔진으로 옮기더라도 <b>이 인터페이스만 새로 구현하면 되고 응답 모양은 손대지 않는다.</b>
 * 본문의 정답은 언제나 MySQL -> 잘못된 응답 X
 */
public interface FeedSearchPort {

    FeedSearchResult search(FeedSearchCondition condition);

    /**
     * @param feedIds    정렬된 피드 id. {@code limit + 1} 건까지 담긴다(다음 페이지 존재 판정용)
     * @param totalCount 조건에 맞는 전체 건수
     */
    record FeedSearchResult(List<UUID> feedIds, long totalCount) {

        public static FeedSearchResult empty() {
            return new FeedSearchResult(List.of(), 0L);
        }
    }
}
