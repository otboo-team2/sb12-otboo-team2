package com.otboo.feed.search;

import com.otboo.common.pagination.CursorCodec;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.feed.dto.FeedSearchCondition;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * MySQL 로 피드를 검색한다. {@link FeedSearchPort} 의 유일한 구현이다.
 *
 * <h2>{@code LIKE '%키워드%'} 단독으로 쓰지 않는다</h2>
 * 앞에 와일드카드가 붙으면 인덱스를 탈 수 없어 <b>매번 전체 스캔</b>이다. 피드가 10만 건이면
 * 검색 한 번에 10만 행을 읽는다. 그래서 {@code V2} 에서 만든 ngram FULLTEXT 인덱스로
 * 후보를 먼저 좁히고, 그 후보에만 {@code LIKE} 를 걸어 정확도를 맞춘다.
 * 두 조건을 함께 쓰는 이유는 {@link SearchKeyword} 주석에 실측 근거와 함께 적어 뒀다.
 *
 * <h2>정렬 · 페이지네이션</h2>
 * 관련도(score) 순이 아니라 스펙이 정한 {@code createdAt} / {@code likeCount} 순이다.
 * {@code OFFSET} 을 쓰면 뒤 페이지로 갈수록 건너뛴 행까지 읽어 느려지므로 keyset 으로 간다.
 */
@Component
@RequiredArgsConstructor
public class MysqlFeedSearch implements FeedSearchPort {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional(readOnly = true)
    public FeedSearchResult search(FeedSearchCondition condition) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String from = buildFrom(condition);
        String filters = buildFilters(condition, params);

        Long total = jdbc.queryForObject("SELECT COUNT(*) " + from + filters, params, Long.class);
        if (total == null || total == 0L) {
            return FeedSearchResult.empty();
        }

        // 커서 조건은 개수 집계에 넣지 않는다. totalCount 는 페이지가 아니라 조건 전체의 수다.
        String keyset = buildKeyset(condition, params);
        params.addValue("limit", condition.page().fetchSize());

        List<UUID> ids = jdbc.queryForList(
                        "SELECT f.id " + from + filters + keyset
                                + buildOrderBy(condition) + " LIMIT :limit",
                        params, String.class)
                .stream().map(UUID::fromString).toList();

        return new FeedSearchResult(ids, total);
    }

    /** 날씨 필터가 없으면 조인 자체를 걸지 않는다. 필요 없는 조인은 그대로 비용이다. */
    private String buildFrom(FeedSearchCondition condition) {
        return condition.hasWeatherFilter()
                ? "FROM feeds f JOIN weathers w ON w.id = f.weather_id "
                : "FROM feeds f ";
    }

    private String buildFilters(FeedSearchCondition condition, MapSqlParameterSource params) {
        StringBuilder sql = new StringBuilder("WHERE 1 = 1 ");

        if (condition.authorIdEqual() != null) {
            sql.append("AND f.author_id = :authorId ");
            params.addValue("authorId", condition.authorIdEqual().toString());
        }
        if (condition.skyStatusEqual() != null) {
            sql.append("AND w.sky_status = :skyStatus ");
            // 컬럼이 VARCHAR 라 enum 이름을 넣는다. enum 객체를 그대로 바인딩하면
            // 드라이버가 ordinal 로 보낼 수 있어 조건이 조용히 어긋난다.
            params.addValue("skyStatus", condition.skyStatusEqual().name());
        }
        if (condition.precipitationTypeEqual() != null) {
            sql.append("AND w.precipitation_type = :precipitationType ");
            params.addValue("precipitationType", condition.precipitationTypeEqual().name());
        }
        if (condition.hasKeyword()) {
            appendKeyword(condition.keywordLike(), sql, params);
        }
        return sql.toString();
    }

    /**
     * 검색어 조건.
     *
     * <p>MATCH 는 <b>후보를 좁히는 용도</b>(인덱스 이용), LIKE 는 <b>정확도를 맞추는 용도</b>다.
     * MATCH 의 AND 조건은 부분 문자열을 포함한 문서를 절대 놓치지 않으므로
     * LIKE 를 덧붙여도 정상 결과가 사라지지 않는다.
     */
    private void appendKeyword(String keyword, StringBuilder sql, MapSqlParameterSource params) {
        if (SearchKeyword.isEmpty(keyword)) {
            return; // 연산자 문자만 들어온 경우. 조건을 걸지 않는다(걸면 전체 조회가 된다).
        }
        if (SearchKeyword.isFullTextSearchable(keyword)) {
            sql.append("AND MATCH(f.content) AGAINST (:ftKeyword IN BOOLEAN MODE) ");
            params.addValue("ftKeyword", SearchKeyword.toBooleanAnd(keyword));
        }
        // 한 글자 검색어처럼 색인 토큰이 없는 경우엔 LIKE 만 남는다(느리지만 결과는 정확하다).
        sql.append("AND f.content LIKE :likeKeyword ");
        params.addValue("likeKeyword", SearchKeyword.toLikePattern(keyword));
    }

    /**
     * keyset 조건. 정렬 키가 같은 행에서 순서가 흔들리지 않도록 {@code (정렬키, id)} 를 함께 본다.
     * 이 조건과 {@code ORDER BY} 의 방향·컬럼이 어긋나면 페이지가 통째로 밀린다.
     */
    private String buildKeyset(FeedSearchCondition condition, MapSqlParameterSource params) {
        CursorRequest page = condition.page();
        if (page.isFirstPage()) {
            return "";
        }
        String column = sortColumn(condition);
        String operator = page.sortDirection().comparisonOperator();

        params.addValue("cursor", condition.sortsByLikeCount()
                ? CursorCodec.asLong(page.cursor())
                : toLocalDateTime(CursorCodec.asInstant(page.cursor())));

        if (page.idAfter() == null) {
            return "AND %s %s :cursor ".formatted(column, operator);
        }
        params.addValue("idAfter", page.idAfter().toString());
        return "AND (%s %s :cursor OR (%s = :cursor AND f.id %s :idAfter)) "
                .formatted(column, operator, column, operator);
    }

    /**
     * {@code ORDER BY} 끝에 반드시 {@code f.id} 가 붙는다. 빠뜨리면 정렬 키가 같은 행들의 순서를
     * DB 가 매번 다르게 정해 keyset 조건이 무의미해진다.
     */
    private String buildOrderBy(FeedSearchCondition condition) {
        String direction = condition.page().sortDirection().isAscending() ? "ASC" : "DESC";
        return "ORDER BY %s %s, f.id %s ".formatted(sortColumn(condition), direction, direction);
    }

    private String sortColumn(FeedSearchCondition condition) {
        return condition.sortsByLikeCount() ? "f.like_count" : "f.created_at";
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
