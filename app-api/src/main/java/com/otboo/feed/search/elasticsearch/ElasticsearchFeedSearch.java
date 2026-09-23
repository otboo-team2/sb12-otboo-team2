package com.otboo.feed.search.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.otboo.common.pagination.CursorCodec;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.feed.dto.FeedSearchCondition;
import com.otboo.feed.search.FeedSearchPort;
import com.otboo.feed.search.SearchKeyword;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Elasticsearch 피드 검색.
 *   <li><b>형태소 분석(nori)</b> — "코트" 로 "겨울코트를 꺼냈다" 가 걸린다. ngram+LIKE 조합은
 *       두 조건을 AND 로 걸어 재현율을 붙잡았지만, 그 대가로 <b>어절 경계를 넘는 검색</b>
 *       ("코트 겨울" 처럼 순서가 다른 입력)을 놓쳤다.</li>
 *   <li><b>작성자 이름 검색</b> — 본문만 뒤지던 것을 작성자까지 넓혔다.
 *       검색창 문구("피드 내 검색하기")가 기대하는 동작에 더 가깝다.</li>
 *   <li><b>전체 스캔이 사라졌다</b> — 한 글자 검색어일 때 MySQL 구현은 {@code LIKE '%x%'} 로
 *       내려가 피드 전체를 읽었다. 역색인에는 그런 경로가 없다.</li>
 *   <li><b>{@code COUNT(*)} 가 사라졌다</b> — 총 건수를 검색과 같은 요청에서 받는다.
 *       MySQL 은 목록과 집계로 쿼리를 두 번 돌렸다.</li>
 * </ol>
 *
 * <h2>정렬은 관련도(score) 순이 아니다</h2>
 * 스펙이 {@code createdAt} / {@code likeCount} 만 허용하고 프론트도 그 둘만 노출한다.
 * 검색 엔진을 바꾼다고 응답 계약을 바꾸지 않는다. 대신 keyset 페이지네이션을 ES 의
 * {@code search_after} 로 그대로 옮겼다 — {@code from + size} 는 뒤 페이지로 갈수록
 * 건너뛴 문서까지 각 샤드가 모아야 해서 느려지고, 기본 상한(10,000)에 막힌다.
 */
@RequiredArgsConstructor
public class ElasticsearchFeedSearch implements FeedSearchPort {

    /** 본문이 작성자 이름보다 중요하다. 같은 점수면 본문에서 걸린 피드가 위로 온다. */
    private static final float CONTENT_BOOST = 3.0f;
    private static final float AUTHOR_BOOST = 1.0f;

    private final ElasticsearchClient client;
    private final FeedIndexManager indexManager;

    @Override
    public FeedSearchResult search(FeedSearchCondition condition) {
        SearchRequest request = buildRequest(condition);
        SearchResponse<Void> response;
        try {
            response = client.search(request, Void.class);
        } catch (IOException e) {
            // 여기서 삼키면 "검색 결과 0건" 으로 보인다. 장애를 정상 응답으로 위장하지 않는다.
            // 폴백은 FallbackFeedSearch 가 의도적으로 판단해서 한다.
            throw new UncheckedIOException("피드 검색 요청에 실패했다", e);
        }

        List<UUID> ids = response.hits().hits().stream()
                .map(Hit::id)
                .map(UUID::fromString)
                .toList();

        long total = response.hits().total() == null ? ids.size() : response.hits().total().value();
        return new FeedSearchResult(ids, total);
    }

    private SearchRequest buildRequest(FeedSearchCondition condition) {
        CursorRequest page = condition.page();
        SortOrder order = page.sortDirection().isAscending() ? SortOrder.Asc : SortOrder.Desc;
        String sortField = condition.sortsByLikeCount() ? "likeCount" : "createdAt";

        SearchRequest.Builder request = new SearchRequest.Builder()
                .index(indexManager.alias())
                .query(buildQuery(condition))
                // 다음 페이지가 있는지 보려고 한 건 더 가져온다. MySQL 구현과 같은 방식.
                .size(page.fetchSize())
                // 기본값은 10,000 에서 집계를 멈춘다. totalCount 가 스펙 필드라 정확해야 한다.
                .trackTotalHits(t -> t.enabled(true))
                // 문서 본문은 사용x. id 만 있으면 FeedViewLoader 가 MySQL 에서 읽는다.
                // 끄지 않으면 페이지마다 본문 20건을 네트워크로 transfer.
                .source(s -> s.fetch(false))
                .sort(s -> s.field(f -> f.field(sortField).order(order)))
                // 정렬 키가 같은 문서의 순서를 고정하는 tiebreaker. 빠지면 search_after 의미 x.
                .sort(s -> s.field(f -> f.field("id").order(order)));

        List<FieldValue> searchAfter = buildSearchAfter(condition);
        if (searchAfter != null) {
            request.searchAfter(searchAfter);
        }
        return request.build();
    }

    /**
     * 커서를 {@code search_after} 값으로 바꾼다. {@code sort} 절과 <b>순서·개수가 정확히 같아야</b>
     * 하고, 값의 형식도 ES 가 정렬값으로 내보내는 형식이어야 한다.
     *
     * <p>{@code createdAt} 은 {@code date_nanos} 라 정렬값이 <b>epoch 나노초</b>다.
     * 밀리초로 넘기면 같은 밀리초 안의 피드들이 통째로 밀린다.
     */
    private List<FieldValue> buildSearchAfter(FeedSearchCondition condition) {
        CursorRequest page = condition.page();
        if (page.isFirstPage() || page.idAfter() == null) {
            return null;
        }
        List<FieldValue> values = new ArrayList<>(2);
        if (condition.sortsByLikeCount()) {
            Long likeCount = CursorCodec.asLong(page.cursor());
            if (likeCount == null) {
                return null;
            }
            values.add(FieldValue.of(likeCount));
        } else {
            Instant createdAt = CursorCodec.asInstant(page.cursor());
            if (createdAt == null) {
                return null;
            }
            values.add(FieldValue.of(toEpochNanos(createdAt)));
        }
        values.add(FieldValue.of(page.idAfter().toString()));
        return values;
    }

    private Query buildQuery(FeedSearchCondition condition) {
        BoolQuery.Builder bool = new BoolQuery.Builder();

        // 필터는 점수 계산을 하지 않는다(filter context). 캐시도 된다.
        if (condition.authorIdEqual() != null) {
            String authorId = condition.authorIdEqual().toString();
            bool.filter(f -> f.term(t -> t.field("authorId").value(authorId)));
        }
        if (condition.skyStatusEqual() != null) {
            String skyStatus = condition.skyStatusEqual().name();
            bool.filter(f -> f.term(t -> t.field("skyStatus").value(skyStatus)));
        }
        if (condition.precipitationTypeEqual() != null) {
            String precipitationType = condition.precipitationTypeEqual().name();
            bool.filter(f -> f.term(t -> t.field("precipitationType").value(precipitationType)));
        }

        String keyword = condition.hasKeyword()
                ? SearchKeyword.sanitize(condition.keywordLike())
                : "";
        if (keyword.isEmpty()) {
            // 검색어 없이 필터만 있는 경우. must 를 비워 두면 필터만으로 전체를 훑는다.
            bool.must(m -> m.matchAll(all -> all));
        } else {
            bool.must(keywordQuery(keyword));
        }
        return Query.of(q -> q.bool(bool.build()));
    }

    /**
     * 검색어 조건. 형태소와 부분 문자열을 <b>OR 로 묶는다</b>(둘 중 하나만 맞아도 결과).
     *
     * <p>둘을 AND 로 묶었던 것이 MySQL 구현의 한계였다. 형태소 분석은 "겨울 코트" 처럼
     * 띄어 쓴 검색어를 잘 잡고, ngram 은 "울코트" 같은 어중간한 부분 문자열을 잡는다.
     * 서로 못 잡는 걸 메우는 관계라 OR 이 맞다. 정확도는 {@code boost} 로 조절한다 —
     * 형태소로 걸린 쪽이 위에 온다.
     *
     * <p>{@code operator(And)} 는 <b>낱말 전부 포함</b>을 뜻한다. 기본값(OR)로 두면
     * "겨울 코트" 가 "겨울" 만 든 피드까지 다 끌어와 검색이 필터 구실을 못 한다.
     *
     * <p>{@code match_phrase} 를 쓰는 이유 — ngram 토큰은 위치를 갖는다. 구문 검색이면
     * 토큰이 <b>연속으로</b> 나와야 하므로 진짜 부분 문자열만 걸린다.
     * 그냥 {@code match} 면 "코"·"트" 조각이 흩어져 있는 문서까지 걸린다.
     */
    private Query keywordQuery(String keyword) {
        return Query.of(q -> q.bool(b -> b
                .should(s -> s.match(m -> m
                        .field("content").query(keyword)
                        .operator(Operator.And).boost(CONTENT_BOOST)))
                .should(s -> s.match(m -> m
                        .field("authorName").query(keyword)
                        .operator(Operator.And).boost(AUTHOR_BOOST)))
                .should(s -> s.matchPhrase(m -> m
                        .field("content.substring").query(keyword)
                        .boost(CONTENT_BOOST)))
                .should(s -> s.matchPhrase(m -> m
                        .field("authorName.substring").query(keyword)
                        .boost(AUTHOR_BOOST)))
                // should 만 있는 bool 은 기본이 "하나 이상" 이지만, 나중에 filter 가 추가되면
                // 기본값이 0 으로 바뀌어 조건이 조용히 사라진다. 명시해 둔다.
                .minimumShouldMatch("1")));
    }

    /** {@code date_nanos} 정렬값과 같은 단위(epoch nanos). 2262년까지 long 에 들어간다. */
    private long toEpochNanos(Instant instant) {
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }
}
