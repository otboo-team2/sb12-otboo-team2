package com.otboo.feed.query;

import static com.otboo.feed.query.JdbcColumns.uuid;

import com.otboo.common.pagination.CursorCodec;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.feed.dto.FollowDto;
import com.otboo.feed.dto.FollowSummaryDto;
import com.otboo.feed.dto.UserSummary;
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
 * 팔로우 조회. 응답에 양쪽 사용자의 이름·프로필 이미지가 다 실려야 해서
 * {@code users}·{@code profiles} 를 <b>두 번씩</b> 조인.
 *
 * <p>엔티티로 풀면 {@code Follow → User → Profile} 을 양방향으로 타면서 N+1 이 두 배로 난다.
 * 댓글 목록과 같은 이유로 조회는 JDBC 로 직접 읽는다.
 *
 * <h2>정렬은 서버가 정한다</h2>
 * 스펙의 팔로잉·팔로워 목록에 {@code sortBy} 가 없음.
 * 최근 팔로우가 위로 오도록 {@code created_at DESC, id DESC} 로 고정.
 *
 * <p><b>인덱스 정렬 X, {@code idx_follows_follower} 가
 * {@code (follower_id, id)} 라 {@code created_at} 정렬에는 filesort 추가.
 * 한 사람의 팔로우 수는 많아야 수천 건이라 지금은 문제가 없지만, 느려지면
 * {@code (follower_id, created_at, id)} 로 인덱스로 변경.
 */
@Component
@RequiredArgsConstructor
public class FollowViewLoader {

    private final NamedParameterJdbcTemplate jdbc;

    /** 팔로우 한 건에 사용자가 둘이라 users·profiles 를 각각 두 번 조인한다. */
    private static final String SELECT_SQL = """
            SELECT f.id                 AS follow_id,
                   f.created_at         AS created_at,
                   fe.id                AS followee_id,
                   fe.name              AS followee_name,
                   pe.profile_image_url AS followee_image_url,
                   fr.id                AS follower_id,
                   fr.name              AS follower_name,
                   pr.profile_image_url AS follower_image_url
            FROM follows f
                     JOIN users fe ON fe.id = f.followee_id
                     LEFT JOIN profiles pe ON pe.user_id = fe.id
                     JOIN users fr ON fr.id = f.follower_id
                     LEFT JOIN profiles pr ON pr.user_id = fr.id
            """;

    // (created_at, id) 를 함께 비교한다. 같은 시각에 생긴 팔로우가 중복·누락되지 않게 하는 조건.
    private static final String KEYSET_SQL = """
            AND (f.created_at < :cursor
                 OR (f.created_at = :cursor AND f.id < :idAfter))
            """;

    private static final String KEYSET_WITHOUT_ID_SQL = """
            AND f.created_at < :cursor
            """;

    private static final String ORDER_SQL = """
            ORDER BY f.created_at DESC, f.id DESC
            LIMIT :limit
            """;

    /**
     * 한 행 = 응답 DTO + 커서로 쓸 정렬 키.
     */
    public record FollowRow(FollowDto follow, Instant createdAt) {
    }

    private static final RowMapper<FollowRow> MAPPER = (rs, i) -> new FollowRow(
            new FollowDto(
                    uuid(rs, "follow_id"),
                    new UserSummary(uuid(rs, "followee_id"), rs.getString("followee_name"),
                            rs.getString("followee_image_url")),
                    new UserSummary(uuid(rs, "follower_id"), rs.getString("follower_name"),
                            rs.getString("follower_image_url"))),
            JdbcColumns.instant(rs, "created_at"));

    /**
     * 팔로잉 목록
     * {@code nameLike} -> <b>상대방(followee) 이름에 추가
     *
     * @return {@link CursorRequest#fetchSize()} 만큼(= limit + 1) 조회한 결과
     */
    @Transactional(readOnly = true)
    public List<FollowRow> loadFollowingsSlice(UUID followerId, CursorRequest page,
            String nameLike) {
        return loadSlice("f.follower_id", "fe.name", followerId, page, nameLike);
    }

    @Transactional(readOnly = true)
    public long countFollowings(UUID followerId, String nameLike) {
        return count("f.follower_id", "fe.name", followerId, nameLike);
    }

    /**
     * 팔로워 목록
     * {@code nameLike} -> <b>상대방(followee) 이름에 추가
     */
    @Transactional(readOnly = true)
    public List<FollowRow> loadFollowersSlice(UUID followeeId, CursorRequest page,
            String nameLike) {
        return loadSlice("f.followee_id", "fr.name", followeeId, page, nameLike);
    }

    @Transactional(readOnly = true)
    public long countFollowers(UUID followeeId, String nameLike) {
        return count("f.followee_id", "fr.name", followeeId, nameLike);
    }

    /**
     * 단건 조회.
     */
    @Transactional(readOnly = true)
    public FollowDto loadOne(UUID followId) {
        List<FollowRow> found = jdbc.query(SELECT_SQL + "WHERE f.id = :followId",
                new MapSqlParameterSource("followId", followId.toString()), MAPPER);
        return found.isEmpty() ? null : found.getFirst().follow();
    }

    /**
     * 프로필 화면의 팔로우 요약.
     *
     * <p>카운트 두 개와 나와의 관계 두 개를 <b>한 번의 왕복</b>으로 읽음.
     * 네 번 나눠 던지면 프로필 화면을 열 때마다 쿼리가 네 개씩 출력
     *
     * @param targetUserId 조회 대상
     * @param meId         로그인한 사용자. 비로그인이면 {@code null} — 관계 필드 null
     */
    @Transactional(readOnly = true)
    public FollowSummaryDto loadSummary(UUID targetUserId, UUID meId) {
        String sql = """
                SELECT (SELECT COUNT(*) FROM follows WHERE followee_id = :targetId)
                           AS follower_count,
                       (SELECT COUNT(*) FROM follows WHERE follower_id = :targetId)
                           AS following_count,
                       (SELECT id FROM follows
                         WHERE follower_id = :meId AND followee_id = :targetId)
                           AS followed_by_me_id,
                       EXISTS(SELECT 1 FROM follows
                               WHERE follower_id = :targetId AND followee_id = :meId)
                           AS following_me
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("targetId", targetUserId.toString())
                // null 이면 두 서브쿼리가 각각 NULL · false 로 떨어짐.
                .addValue("meId", meId == null ? null : meId.toString());

        return jdbc.queryForObject(sql, params, (rs, i) -> {
            UUID followedByMeId = uuid(rs, "followed_by_me_id");
            return new FollowSummaryDto(
                    targetUserId,
                    rs.getLong("follower_count"),
                    rs.getLong("following_count"),
                    followedByMeId != null,
                    followedByMeId,
                    rs.getBoolean("following_me"));
        });
    }

    /**
     * @param ownerColumn 기준이 되는 쪽 컬럼 — {@code f.follower_id} 또는 {@code f.followee_id}
     * @param nameColumn  {@code nameLike} 를 걸 상대방 이름 컬럼
     */
    private List<FollowRow> loadSlice(String ownerColumn, String nameColumn, UUID ownerId,
            CursorRequest page, String nameLike) {
        Instant cursor = CursorCodec.asInstant(page.cursor());

        StringBuilder sql = new StringBuilder(SELECT_SQL)
                .append("WHERE ").append(ownerColumn).append(" = :ownerId ");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ownerId", ownerId.toString())
                .addValue("limit", page.fetchSize());

        appendNameFilter(sql, params, nameColumn, nameLike);

        if (cursor != null) {
            // idAfter 없이 cursor 만 온 경우까지. 그때는 동점 처리를 포기하고 시각만 비교.
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
     * 전체 개수.
     * 페이지가 넘어가도 총 개수 동일.
     */
    private long count(String ownerColumn, String nameColumn, UUID ownerId, String nameLike) {
        // 이름 필터가 없으면 조인 X, follows 만 카운트
        StringBuilder sql = new StringBuilder(
                nameLike == null || nameLike.isBlank()
                        ? "SELECT COUNT(*) FROM follows f "
                        : """
                        SELECT COUNT(*)
                        FROM follows f
                                 JOIN users fe ON fe.id = f.followee_id
                                 JOIN users fr ON fr.id = f.follower_id
                        """)
                .append("WHERE ").append(ownerColumn).append(" = :ownerId ");

        MapSqlParameterSource params = new MapSqlParameterSource("ownerId", ownerId.toString());
        appendNameFilter(sql, params, nameColumn, nameLike);

        Long total = jdbc.queryForObject(sql.toString(), params, Long.class);
        return total == null ? 0L : total;
    }

    private static void appendNameFilter(StringBuilder sql, MapSqlParameterSource params,
            String nameColumn, String nameLike) {
        if (nameLike == null || nameLike.isBlank()) {
            return;
        }
        // ESCAPE 를 붙이지 않으면 이름에 든 % 와 _ 가 와일드카드로 동작.
        sql.append("AND ").append(nameColumn)
                .append(" LIKE CONCAT('%', :nameLike, '%') ESCAPE '!' ");
        params.addValue("nameLike", escapeLike(nameLike.trim()));
    }

    /** {@code !} -> 이스케이프 문자. 역슬래시는 MySQL 문자열 리터럴에서 한 번 더 꼬인다. */
    private static String escapeLike(String value) {
        return value.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
}
