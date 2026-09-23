package com.otboo.feed.query;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 조회 전용 SQL 의 컬럼 읽기. 피드 응답·댓글 응답·색인 문서가 같은 규칙으로 읽어야 한다.
 *
 * <p>같은 컬럼을 곳곳에서 제각기 읽으면 <b>한 곳만 타임존이 어긋나는</b> 사고가 난다.
 * 실제로 잡기 어려운 종류의 버그라 읽는 방법 자체를 한 곳에 고정한다.
 */
public final class JdbcColumns {

    private JdbcColumns() {
    }

    /** PK/FK 는 CHAR(36) 이라 문자열로 온다. */
    public static UUID uuid(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    /**
     * DATETIME(6) 을 UTC 로 읽는다.
     *
     * <p>{@code getTimestamp} 는 JVM 기본 타임존으로 값을 해석하므로 배포 장비의 TZ 설정에 따라
     * 결과가 달라진다. {@code LocalDateTime} 으로 꺼내 UTC 로 못박으면 어디서 돌려도 같은 값이다.
     * DB·JVM·응답을 전부 UTC 로 맞춘다는 팀 규칙과 같은 이야기다.
     */
    public static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    /**
     * VARCHAR 로 저장된 enum 컬럼.
     *
     * <p>DB 에 CHECK 제약이 걸려 있어 여기서 {@link IllegalArgumentException} 이 난다면
     * <b>스키마와 enum 이 어긋난 것</b>이다(예: 날씨 파트가 값을 추가하고 마이그레이션을 안 했거나
     * 그 반대). 조용히 null 로 넘기면 응답에서 필드만 비어 원인을 찾기 어려우므로 그대로 터뜨린다.
     */
    public static <E extends Enum<E>> E enumOf(ResultSet rs, String column, Class<E> type)
            throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : Enum.valueOf(type, value);
    }

    /** DECIMAL 컬럼. NULL 이면 {@code null} 을 그대로 돌려준다(전날 대비 값은 없을 수 있다). */
    public static Double decimal(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return value == null ? null : value.doubleValue();
    }
}
