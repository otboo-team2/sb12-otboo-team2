package com.otboo.feed.search.elasticsearch;

import static com.otboo.feed.query.JdbcColumns.instant;
import static com.otboo.feed.query.JdbcColumns.uuid;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 색인할 문서를 MySQL 에서 읽는다. <b>색인의 원본은 언제나 MySQL 이다.</b>
 *
 * <p>엔티티로 읽지 않고 조회 전용 SQL 을 쓰는 이유는 {@code FeedViewLoader} 와 같다.
 * 작성자 이름은 {@code users}, 날씨는 {@code weathers} 에 있어 엔티티 그래프를 타면
 * 문서 하나에 추가 쿼리가 붙는다. 전체 재색인처럼 수만 건을 도는 경로에서는 치명적이다.
 */
@RequiredArgsConstructor
public class FeedDocumentLoader {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String SELECT = """
            SELECT f.id                 AS feed_id,
                   f.content            AS content,
                   f.created_at         AS created_at,
                   f.like_count         AS like_count,
                   u.id                 AS author_id,
                   u.name               AS author_name,
                   w.sky_status         AS sky_status,
                   w.precipitation_type AS precipitation_type
            FROM feeds f
                     JOIN users u ON u.id = f.author_id
                     JOIN weathers w ON w.id = f.weather_id
            """;

    /** 전체 재색인용. id 오름차순 keyset 으로 훑어 {@code OFFSET} 없이 페이지를 넘긴다. */
    @Transactional(readOnly = true)
    public List<FeedDocument> loadBatch(UUID afterId, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("size", size);
        StringBuilder sql = new StringBuilder(SELECT);
        if (afterId != null) {
            sql.append("WHERE f.id > :afterId ");
            params.addValue("afterId", afterId.toString());
        }
        sql.append("ORDER BY f.id ASC LIMIT :size");

        return jdbc.query(sql.toString(), params, (rs, i) -> map(rs));
    }

    /** 단건·소량 색인용. 삭제된 피드는 결과에서 그냥 빠진다(호출부가 그걸로 판단하지 않는다). */
    @Transactional(readOnly = true)
    public List<FeedDocument> loadByIds(List<UUID> feedIds) {
        if (feedIds.isEmpty()) {
            return List.of();
        }
        List<String> ids = feedIds.stream().map(UUID::toString).toList();
        return jdbc.query(SELECT + "WHERE f.id IN (:ids)",
                new MapSqlParameterSource("ids", ids), (rs, i) -> map(rs));
    }

    private static FeedDocument map(ResultSet rs) throws SQLException {
        return FeedDocument.of(
            Objects.requireNonNull(uuid(rs, "feed_id")),
                rs.getString("content"),
            Objects.requireNonNull(uuid(rs, "author_id")),
                rs.getString("author_name"),
                rs.getString("sky_status"),
                rs.getString("precipitation_type"),
                instant(rs, "created_at"),
                rs.getLong("like_count"));
    }
}
