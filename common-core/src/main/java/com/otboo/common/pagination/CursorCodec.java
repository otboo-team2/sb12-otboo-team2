package com.otboo.common.pagination;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * 커서 값을 문자열로 바꾸고 되돌린다.
 *
 * <p>Swagger 스펙이 {@code cursor}(정렬 키 값)와 {@code idAfter}(동점 처리용 UUID)를
 * <b>이미 두 필드로 나눠놨기 때문에</b> Base64 로 감쌀 필요가 없다. 값을 그대로 문자열로 넣는다.
 * 감싸면 디버깅할 때 커서를 눈으로 못 읽는다.
 *
 * <p>정렬 키 타입이 도메인마다 다르다 — 사용자는 {@code email}(문자열),
 * 피드는 {@code createdAt}(시각)·{@code likeCount}(숫자). 그래서 타입별 해석 메서드를 둔다.
 */
public final class CursorCodec {

    private CursorCodec() {
    }

    /** 정렬 키 값을 커서 문자열로 만든다. {@code null} 은 {@code null} 그대로 둔다. */
    public static String encode(Object value) {
        return switch (value) {
            case null -> null;
            case String s -> s;
            case Instant instant -> instant.toString();      // ISO-8601, 항상 UTC
            case LocalDate date -> date.toString();
            case UUID uuid -> uuid.toString();
            default -> String.valueOf(value);
        };
    }

    public static Instant asInstant(String cursor) {
        if (isBlank(cursor)) {
            return null;
        }
        try {
            return Instant.parse(cursor);
        } catch (DateTimeParseException e) {
            throw invalid(cursor, "시각(ISO-8601)");
        }
    }

    public static Long asLong(String cursor) {
        if (isBlank(cursor)) {
            return null;
        }
        try {
            return Long.valueOf(cursor);
        } catch (NumberFormatException e) {
            throw invalid(cursor, "숫자");
        }
    }

    public static UUID asUuid(String cursor) {
        if (isBlank(cursor)) {
            return null;
        }
        try {
            return UUID.fromString(cursor);
        } catch (IllegalArgumentException e) {
            throw invalid(cursor, "UUID");
        }
    }

    public static String asString(String cursor) {
        return isBlank(cursor) ? null : cursor;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static BusinessException invalid(String cursor, String expected) {
        return new BusinessException(CommonErrorCode.INVALID_CURSOR)
                .addDetail("cursor", cursor)
                .addDetail("expected", expected);
    }
}
