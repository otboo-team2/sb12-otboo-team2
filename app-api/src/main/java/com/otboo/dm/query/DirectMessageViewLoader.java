package com.otboo.dm.query;

import static com.otboo.feed.query.JdbcColumns.instant;
import static com.otboo.feed.query.JdbcColumns.uuid;

import com.otboo.common.pagination.CursorCodec;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.dm.dto.DirectMessageDto;
import com.otboo.dm.dto.UserSummary;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DirectMessageViewLoader {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String SELECT_SQL = """
            SELECT dm.id                AS message_id,
                   dm.created_at        AS created_at,
                   dm.content           AS content,
                   s.id                 AS sender_id,
                   s.name               AS sender_name,
                   ps.profile_image_url AS sender_image_url,
                   r.id                 AS receiver_id,
                   r.name               AS receiver_name,
                   pr.profile_image_url AS receiver_image_url
            FROM direct_messages dm
                     JOIN users s ON s.id = dm.sender_id
                     LEFT JOIN profiles ps ON ps.user_id = s.id
                     JOIN users r ON r.id = dm.receiver_id
                     LEFT JOIN profiles pr ON pr.user_id = r.id
            WHERE dm.dm_key = :dmKey
            """;

    private static final String KEYSET_SQL = """
            AND (dm.created_at < :cursor
                 OR (dm.created_at = :cursor AND dm.id < :idAfter))
            """;

    private static final String KEYSET_WITHOUT_ID_SQL = """
            AND dm.created_at < :cursor
            """;

    private static final String ORDER_SQL = """
            ORDER BY dm.created_at DESC, dm.id DESC
            LIMIT :limit
            """;

    private static final RowMapper<DirectMessageDto> MAPPER = (rs, i) -> new DirectMessageDto(
        uuid(rs, "message_id"),
        instant(rs, "created_at"),
        new UserSummary(uuid(rs, "sender_id"), rs.getString("sender_name"),
            rs.getString("sender_image_url")),
        new UserSummary(uuid(rs, "receiver_id"), rs.getString("receiver_name"),
            rs.getString("receiver_image_url")),
        rs.getString("content"));

    @Transactional(readOnly = true)
    public List<DirectMessageDto> loadSlice(String dmKey, CursorRequest page) {
        Instant cursor = CursorCodec.asInstant(page.cursor());

        StringBuilder sql = new StringBuilder(SELECT_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("dmKey", dmKey)
            .addValue("limit", page.fetchSize());

        if (cursor != null) {
            sql.append(page.idAfter() == null ? KEYSET_WITHOUT_ID_SQL : KEYSET_SQL);
            params.addValue("cursor", LocalDateTime.ofInstant(cursor, ZoneOffset.UTC));
            if (page.idAfter() != null) {
                params.addValue("idAfter", page.idAfter().toString());
            }
        }
        sql.append(ORDER_SQL);

        return jdbc.query(sql.toString(), params, MAPPER);
    }
}
