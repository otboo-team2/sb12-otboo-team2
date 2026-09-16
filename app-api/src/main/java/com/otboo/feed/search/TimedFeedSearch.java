package com.otboo.feed.search;

import com.otboo.feed.dto.FeedSearchCondition;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 검색 엔진 하나를 감싸 걸린 시간을 -> {@code otboo_feed_search_seconds}.
 *
 * <h2>HTTP 지표만으로는 엔진을 비교할 수 없다</h2>
 * {@code http_server_requests} 는 {@code uri} 단위,
 * {@code /api/feeds} -> 목록 조회/검색어 검색 400 + 응답 조립({@code FeedViewLoader}) 시간이 들어감.
 * "조건에 맞는 id 를 골라내는 시간" only
 *
 * <h2>엔진 구현을 직접 고치지 않고 감싸는 이유</h2>
 * <ul>
 *   <li>구현체의 {@code @Transactional} 프록시 바깥에서 재므로 커넥션 획득 · 커밋 시간까지 들어감.
 *       MySQL 검색의 실제 비용에는 커넥션 풀 대기도 포함된다.</li>
 *   <li>폴백을 두더라도 엔진마다 따로 감싸면 <b>실제로 응답한 엔진</b>에 시간이 기록.
 *       바깥 하나만 감싸면 폴백된 MySQL 시간이 ES 로 집계돼 비교가 틀어진다.</li>
 * </ul>
 *
 * <h2>라벨</h2>
 * 값의 종류가 유한한 것만 쓴다(검색어 자체는 넣지 않는다).
 * <ul>
 *   <li>{@code engine} — {@code mysql} / {@code elasticsearch}</li>
 *   <li>{@code keyword} — 검색어 유무. 검색어가 없으면 엔진 차이가 거의 없는 목록 조회.</li>
 *   <li>{@code sort} — {@code createdAt} / {@code likeCount}. {@link FeedSearchCondition} 이 이미 검증한 값.</li>
 *   <li>{@code outcome} — {@code success} / {@code error}. 타임아웃으로 실패한 호출이 성공 p95 를 끌어올리지 않게 나눈다</li>
 * </ul>
 */
public class TimedFeedSearch implements FeedSearchPort {

    static final String METRIC = "otboo_feed_search";

    private final String engine;
    private final FeedSearchPort delegate;
    private final MeterRegistry registry;

    public TimedFeedSearch(String engine, FeedSearchPort delegate, MeterRegistry registry) {
        this.engine = engine;
        this.delegate = delegate;
        this.registry = registry;
    }

    @Override
    public FeedSearchResult search(FeedSearchCondition condition) {
        Timer.Sample sample = Timer.start(registry);
        String outcome = "error";
        try {
            FeedSearchResult result = delegate.search(condition);
            outcome = "success";
            return result;
        } finally {
            sample.stop(Timer.builder(METRIC)
                    .description("피드 검색 엔진이 id 를 골라내는 데 걸린 시간")
                    .tag("engine", engine)
                    .tag("keyword", String.valueOf(condition.hasKeyword()))
                    .tag("sort", condition.page().sortBy())
                    .tag("outcome", outcome)
                    .register(registry));
        }
    }
}
