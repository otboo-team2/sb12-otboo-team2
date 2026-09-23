package com.otboo.common.http;

import java.time.Duration;

/**
 * 외부 API 한 곳에 대한 호출 정책.
 *
 * <p>값이 {@code null} 이면 "지정하지 않음"이고 상위 기본값으로 채워진다.
 * 그래서 원시 타입(int)이 아니라 래퍼 타입을 쓴다.
 *
 * @param connectTimeout 연결 수립까지 기다리는 시간. 이걸 넘기면 네트워크·DNS 문제이므로 더 기다려도 소용없다.
 * @param readTimeout    응답 본문을 다 받을 때까지 기다리는 시간. API 성격에 따라 크게 다르다.
 * @param maxRetries     <b>재시도 횟수</b>. 0 이면 호출은 1번만 한다. 기본값이 0 인 이유는 {@link ExternalApiClient} 참고.
 * @param backoff        첫 재시도까지 기다리는 시간. 재시도마다 2배로 늘어난다.
 * @param dailyLimit     하루 호출 상한. {@code null} 이면 무제한. LLM 처럼 과금되는 API 에 건다.
 */
public record ApiSettings(
        Duration connectTimeout,
        Duration readTimeout,
        Integer maxRetries,
        Duration backoff,
        Long dailyLimit
) {

    /**
     * 아무 설정도 없을 때 적용되는 값.
     *
     * <p>재시도 0 은 의도적이다. 재시도해도 안전한지는 API 담당자만 알 수 있으므로,
     * 모르는 상태에서는 "안 하는 쪽"으로 실패한다.
     */
    public static final ApiSettings DEFAULTS = new ApiSettings(
            Duration.ofSeconds(3),
            Duration.ofSeconds(10),
            0,
            Duration.ofSeconds(1),
            null);

    public ApiSettings {
        if (maxRetries != null && maxRetries < 0) {
            maxRetries = 0;
        }
        if (dailyLimit != null && dailyLimit < 0) {
            dailyLimit = 0L;
        }
    }

    /** 비어 있는 항목을 {@code fallback} 값으로 채운다. */
    public ApiSettings withFallback(ApiSettings fallback) {
        return new ApiSettings(
                connectTimeout != null ? connectTimeout : fallback.connectTimeout(),
                readTimeout != null ? readTimeout : fallback.readTimeout(),
                maxRetries != null ? maxRetries : fallback.maxRetries(),
                backoff != null ? backoff : fallback.backoff(),
                dailyLimit != null ? dailyLimit : fallback.dailyLimit());
    }
}
