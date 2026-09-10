package com.otboo.common.logging;

/**
 * MDC 키와 로그 이벤트 이름. <b>문자열을 직접 쓰지 말고 여기 상수를 쓴다.</b>
 *
 * <p>오타가 나면 로그는 그대로 찍히고 검색만 안 된다. 컴파일러가 잡아주는 편이 낫다.
 */
public final class LogKeys {

    // ── MDC (요청 하나 동안 모든 로그 줄에 자동으로 붙는 값) ──────────────────

    /** 요청 하나를 식별한다. 프론트가 {@code X-Request-Id} 를 보내면 그 값을 그대로 쓴다. */
    public static final String REQUEST_ID = "requestId";

    /** 인증된 사용자 id. 비로그인 요청에는 없다. */
    public static final String USER_ID = "userId";

    // ── 이벤트 이름 (로그 한 줄의 첫 토큰) ───────────────────────────────────

    /** HTTP 요청 １건의 처리 결과. {@link RequestLoggingFilter} 만 찍는다. */
    public static final String EVENT_HTTP = "http";

    /** 외부 API 호출 １건. {@code ExternalApiClient} 만 찍는다. */
    public static final String EVENT_EXTERNAL_CALL = "external_call";

    private LogKeys() {
    }
}
