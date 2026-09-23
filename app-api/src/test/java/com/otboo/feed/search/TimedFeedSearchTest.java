package com.otboo.feed.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.SortDirection;
import com.otboo.feed.dto.FeedSearchCondition;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimedFeedSearchTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    @DisplayName("검색 결과는 그대로 돌려주고, 엔진 · 검색어 유무 · 정렬 · 성공 라벨로 시간을 남긴다")
    void recordsSuccess() {
        FeedSearchPort.FeedSearchResult expected =
                new FeedSearchPort.FeedSearchResult(List.of(UUID.randomUUID()), 1L);
        TimedFeedSearch search = new TimedFeedSearch("mysql", condition -> expected, registry);

        FeedSearchPort.FeedSearchResult result = search.search(condition("피드", "likeCount"));

        assertThat(result).isSameAs(expected);
        assertThat(timer("mysql", "true", "likeCount", "success").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("엔진이 예외를 던지면 예외는 그대로 올리고 error 로 남긴다")
    void recordsError() {
        TimedFeedSearch search = new TimedFeedSearch("elasticsearch", condition -> {
            throw new IllegalStateException("timeout");
        }, registry);

        assertThatThrownBy(() -> search.search(condition(null, null)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(timer("elasticsearch", "false", "createdAt", "error").count()).isEqualTo(1);
        assertThat(registry.find(TimedFeedSearch.METRIC).tag("outcome", "success").timer()).isNull();
    }

    private Timer timer(String engine, String keyword, String sort, String outcome) {
        return registry.get(TimedFeedSearch.METRIC)
                .tag("engine", engine)
                .tag("keyword", keyword)
                .tag("sort", sort)
                .tag("outcome", outcome)
                .timer();
    }

    private FeedSearchCondition condition(String keyword, String sortBy) {
        CursorRequest page = new CursorRequest(null, null, 20, sortBy, SortDirection.DESCENDING);
        return FeedSearchCondition.of(page, keyword, null, null, null);
    }
}
