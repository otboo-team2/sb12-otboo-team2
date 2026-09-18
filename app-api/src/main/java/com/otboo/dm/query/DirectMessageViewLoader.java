package com.otboo.dm.query;

import static com.otboo.feed.query.JdbcColumns.instant;
import static com.otboo.feed.query.JdbcColumns.uuid;

import com.otboo.common.pagination.CursorCodec;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.dm.dto.DirectMessageDto;
import com.otboo.dm.dto.DmConversationDto;
import com.otboo.dm.dto.UserSummary;
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

    private static final String CONVERSATIONS_SELECT_SQL = """
        SELECT ranked.message_id, ranked.created_at, ranked.content,
               ranked.partner_id, ranked.partner_name, ranked.partner_image_url
        FROM (
            SELECT dm.id                AS message_id,
                   dm.created_at        AS created_at,
                   dm.content           AS content,
                   CASE WHEN dm.sender_id = :userId THEN r.id ELSE s.id END AS partner_id,
                   CASE WHEN dm.sender_id = :userId THEN r.name ELSE s.name END AS partner_name,
                   CASE WHEN dm.sender_id = :userId THEN pr.profile_image_url ELSE ps.profile_image_url END AS partner_image_url,
                   ROW_NUMBER() OVER (PARTITION BY dm.dm_key ORDER BY dm.created_at DESC, dm.id DESC) AS rn
            FROM direct_messages dm
                     JOIN users s ON s.id = dm.sender_id
                     LEFT JOIN profiles ps ON ps.user_id = s.id
                     JOIN users r ON r.id = dm.receiver_id
                     LEFT JOIN profiles pr ON pr.user_id = r.id
            WHERE dm.sender_id = :userId OR dm.receiver_id = :userId
        ) ranked
        WHERE ranked.rn = 1
        """;

    private static final String CONVERSATIONS_KEYSET_SQL = """
            AND (ranked.created_at < :cursor
                 OR (ranked.created_at = :cursor AND ranked.message_id < :idAfter))
            """;

    private static final String CONVERSATIONS_KEYSET_WITHOUT_ID_SQL = """
            AND ranked.created_at < :cursor
            """;

    private static final String CONVERSATIONS_ORDER_SQL = """
            ORDER BY ranked.created_at DESC, ranked.message_id DESC
            LIMIT :limit
            """;

    private static final RowMapper<DmConversationDto> CONVERSATION_MAPPER = (rs, i) -> new DmConversationDto(
        uuid(rs, "message_id"),
        instant(rs, "created_at"),
        rs.getString("content"),
        new UserSummary(uuid(rs, "partner_id"), rs.getString("partner_name"),
            rs.getString("partner_image_url")));

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

    @Transactional(readOnly = true)
    public List<DmConversationDto> loadConversations(UUID userId, CursorRequest page) {
        Instant cursor = CursorCodec.asInstant(page.cursor());

        StringBuilder sql = new StringBuilder(CONVERSATIONS_SELECT_SQL);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("userId", userId.toString())
            .addValue("limit", page.fetchSize());

        if (cursor != null) {
            sql.append(page.idAfter() == null ? CONVERSATIONS_KEYSET_WITHOUT_ID_SQL : CONVERSATIONS_KEYSET_SQL);
            params.addValue("cursor", LocalDateTime.ofInstant(cursor, ZoneOffset.UTC));
            if (page.idAfter() != null) {
                params.addValue("idAfter", page.idAfter().toString());
            }
        }
        sql.append(CONVERSATIONS_ORDER_SQL);

        return jdbc.query(sql.toString(), params, CONVERSATION_MAPPER);
    }
}
