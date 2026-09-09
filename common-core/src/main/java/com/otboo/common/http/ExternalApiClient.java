package com.otboo.common.http;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.logging.LogKeys;
import java.time.Duration;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 외부 API 호출을 감싸는 공통 클라이언트.
 *
 * <p>이 클래스가 책임지는 것은 <b>"어떻게 부르는가"</b> 뿐이다.
 * 어떤 URL 을 부를지, 응답을 어떻게 해석할지, 실패했을 때 사용자에게 무엇을 보여줄지는
 * 전부 각 도메인의 몫이다.
 *
 * <table border="1">
 *   <caption>책임 구분</caption>
 *   <tr><th>여기서 함</th><th>도메인에서 함</th></tr>
 *   <tr><td>타임아웃</td><td>URL · API 키</td></tr>
 *   <tr><td>재시도 · 백오프</td><td>응답 DTO · 파싱</td></tr>
 *   <tr><td>에러 → ErrorCode 변환</td><td>실패 시 폴백 · 화면 처리</td></tr>
 *   <tr><td>호출 로그</td><td>캐시 여부 · 캐시 키</td></tr>
 *   <tr><td>하루 호출 상한</td><td>도메인 로직 전부</td></tr>
 * </table>
 *
 * <h2>재시도 기본값이 0 인 이유</h2>
 * 재시도해도 안전한지는 <b>API 담당자만 안다</b>. 이미지 생성처럼 호출 １회가 곧 과금이고
 * 결과물이 하나 더 생기는 API 에 재시도를 걸면, 타임아웃 한 번에 돈이 두 번 나가고
 * 결과물이 중복 생성된다. 그래서 모르는 상태에서는 재시도하지 않는 쪽으로 실패한다.
 * <b>안전한 API 만 담당자가 {@code max-retries} 를 올린다.</b>
 *
 * <h2>무엇을 재시도하는가</h2>
 * 횟수는 API 마다 다르지만 판단 규칙은 모든 API 가 같다.
 * <ul>
 *   <li>타임아웃 · 연결 실패 → 재시도. 일시적일 수 있다</li>
 *   <li>5xx → 재시도. 상대 서버 문제다</li>
 *   <li>429 → 재시도. 다만 백오프가 붙는다</li>
 *   <li><b>그 밖의 4xx → 재시도하지 않는다.</b> 우리 요청이 틀린 것이라 100번 해도 같다</li>
 * </ul>
 */
@Slf4j
public class ExternalApiClient {

    private final String apiName;
    private final RestClient restClient;
    private final ApiSettings settings;
    private final DailyCallCounter counter;

    ExternalApiClient(String apiName, RestClient restClient, ApiSettings settings,
            DailyCallCounter counter) {
        this.apiName = apiName;
        this.restClient = restClient;
        this.settings = settings;
        this.counter = counter;
    }

    public <T> T get(String uri, Class<T> responseType) {
        return exchange("GET " + uri, client -> client.get().uri(uri).retrieve().body(responseType));
    }

    public <T> T get(String uri, ParameterizedTypeReference<T> responseType) {
        return exchange("GET " + uri, client -> client.get().uri(uri).retrieve().body(responseType));
    }

    public <T> T post(String uri, Object body, Class<T> responseType) {
        return exchange("POST " + uri,
                client -> client.post().uri(uri).body(body).retrieve().body(responseType));
    }

    /**
     * 위 편의 메서드로 표현되지 않는 호출(헤더 추가, 멀티파트, 쿼리 빌더 등)에 쓴다.
     * 이 경로로 가도 타임아웃 · 재시도 · 에러 변환 · 로깅은 똑같이 적용된다.
     *
     * @param endpoint 로그에 남길 이름. 예: {@code "POST /v1/messages"}
     */
    public <T> T exchange(String endpoint, Function<RestClient, T> call) {
        String logged = UriMasker.mask(endpoint);
        if (!counter.tryAcquire(settings.dailyLimit())) {
            log.warn("{} api={} endpoint={} result=limit_exceeded limit={}",
                    LogKeys.EVENT_EXTERNAL_CALL, apiName, logged, settings.dailyLimit());
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_LIMIT_EXCEEDED)
                    .addDetail("api", apiName)
                    .addDetail("dailyLimit", String.valueOf(settings.dailyLimit()));
        }
        return executeWithRetry(logged, call);
    }

    private <T> T executeWithRetry(String endpoint, Function<RestClient, T> call) {
        int maxAttempts = settings.maxRetries() + 1;
        Duration wait = settings.backoff();
        RuntimeException last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            long startedAt = System.nanoTime();
            try {
                T result = call.apply(restClient);
                log.info("{} api={} endpoint={} result=ok attempt={} elapsed_ms={}",
                        LogKeys.EVENT_EXTERNAL_CALL, apiName, endpoint, attempt, elapsedMs(startedAt));
                return result;
            } catch (RestClientResponseException | ResourceAccessException e) {
                last = e;
                log.warn("{} api={} endpoint={} result=fail attempt={} elapsed_ms={} "
                                + "status={} retryable={}",
                        LogKeys.EVENT_EXTERNAL_CALL, apiName, endpoint, attempt, elapsedMs(startedAt), statusOf(e), retryable(e));
                if (!retryable(e) || attempt == maxAttempts) {
                    break;
                }
                sleep(wait);
                wait = wait.multipliedBy(2);
            }
        }
        throw translate(last, endpoint);
    }

    private static boolean retryable(RuntimeException e) {
        if (e instanceof ResourceAccessException) {
            return true;            // 읽기 타임아웃 · 연결 실패 등 I/O 문제
        }
        if (e instanceof HttpServerErrorException) {
            return true;            // 5xx
        }
        return e instanceof HttpClientErrorException.TooManyRequests;   // 429
    }

    private BusinessException translate(RuntimeException cause, String endpoint) {
        boolean timeout = cause instanceof ResourceAccessException;
        BusinessException translated = new BusinessException(
                timeout ? CommonErrorCode.EXTERNAL_API_TIMEOUT : CommonErrorCode.EXTERNAL_API_ERROR,
                cause);
        return translated
                .addDetail("api", apiName)
                .addDetail("endpoint", endpoint)
                .addDetail("status", statusOf(cause));
    }

    private static String statusOf(RuntimeException e) {
        return e instanceof RestClientResponseException response
                ? String.valueOf(response.getStatusCode().value())
                : "none";
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private static void sleep(Duration wait) {
        try {
            Thread.sleep(wait.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();     // 인터럽트 상태를 삼키지 않는다
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_TIMEOUT, e);
        }
    }
}
