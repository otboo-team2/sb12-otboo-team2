package com.otboo.feed.query;

import static com.otboo.feed.query.JdbcColumns.instant;
import static com.otboo.feed.query.JdbcColumns.uuid;

import com.otboo.common.pagination.CursorCodec;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.feed.dto.AuthorDto;
import com.otboo.feed.dto.CommentDto;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 댓글 목록 조회. 작성자 이름·프로필 이미지가 필요해 {@code users}·{@code profiles} 를 함께 읽는다.
 *
 * <h2>Recently posted descending</h2>
 * 스펙에 {@code sortBy} 가 없어 서버가 순서를 정한다.
 * 프론트 새 댓글을 <b>목록 맨 앞</b>에 INSERT({@code actions.ts} 의 add)
 * Ascending 시 오류
 * {@code created_at DESC, id DESC} 로 고정한다.
 */
@Component
@RequiredArgsConstructor
public class CommentViewLoader {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String SELECT_SQL = """
            SELECT c.id                AS comment_id,
                   c.created_at        AS created_at,
                   c.feed_id           AS feed_id,
                   c.content           AS content,
                   u.id                AS author_id,
                   u.name              AS author_name,
                   p.profile_image_url AS author_image_url
            FROM comments c
                     JOIN users u ON u.id = c.author_id
                     LEFT JOIN profiles p ON p.user_id = u.id
            """;

    // (created_at, id) 두 개를 함께 비교. 같은 시각에 달린 댓글이 중복·누락되지 않게 하는 조건.
    private static final String KEYSET_SQL = """
            AND (c.created_at < :cursor
                 OR (c.created_at = :cursor AND c.id < :idAfter))
            """;

    private static final String KEYSET_WITHOUT_ID_SQL = """
            AND c.created_at < :cursor
            """;

    private static final String ORDER_SQL = """
            ORDER BY c.created_at DESC, c.id DESC
            LIMIT :limit
            """;

    private static final RowMapper<CommentDto> MAPPER = (rs, i) -> new CommentDto(
            uuid(rs, "comment_id"),
            instant(rs, "created_at"),
            uuid(rs, "feed_id"),
            new AuthorDto(uuid(rs, "author_id"), rs.getString("author_name"),
                    rs.getString("author_image_url")),
            rs.getString("content"));

    /** @return {@link CursorRequest#fetchSize()} 만큼(= limit + 1) 조회한 결과 */
    @Transactional(readOnly = true)
    public List<CommentDto> loadSlice(UUID feedId, CursorRequest page) {
        Instant cursor = CursorCodec.asInstant(page.cursor());

        StringBuilder sql = new StringBuilder(SELECT_SQL).append("WHERE c.feed_id = :feedId ");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("feedId", feedId.toString())
                .addValue("limit", page.fetchSize());

        if (cursor != null) {
            // idAfter 없이 cursor 만 온 경우까지. null = 동점 처리를 포기하고 시각만 비교.
            sql.append(page.idAfter() == null ? KEYSET_WITHOUT_ID_SQL : KEYSET_SQL);
            params.addValue("cursor", LocalDateTime.ofInstant(cursor, ZoneOffset.UTC));
            if (page.idAfter() != null) {
                params.addValue("idAfter", page.idAfter().toString());
            }
        }
        sql.append(ORDER_SQL);

        return jdbc.query(sql.toString(), params, MAPPER);
    }

    /**
     * 단건 조회. 등록 응답도 목록과 같은 SQL (필드 누락 방지)
     * 엔티티에서 따로 DTO 를 만들면 그때만 {@code profileImageUrl} 이 null 로 나가는 식으로 어긋난다.
     */
    @Transactional(readOnly = true)
    public CommentDto loadOne(UUID commentId) {
        List<CommentDto> found = jdbc.query(SELECT_SQL + "WHERE c.id = :commentId",
                new MapSqlParameterSource("commentId", commentId.toString()), MAPPER);
        return found.isEmpty() ? null : found.getFirst();
    }
}
