package com.otboo.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청마다 {@code requestId} 를 붙이고, 끝나면 결과를 한 줄로 남긴다.
 *
 * <h2>왜 requestId 가 필요한가</h2>
 * 요청 하나가 컨트롤러 → 서비스 → 외부 API 를 거치며 로그를 여러 줄 남긴다.
 * 동시에 여러 요청이 들어오면 그 줄들이 뒤섞여서, "이 500 에러가 어떤 요청에서 났는지"를
 * 시각만 보고는 못 고른다. 같은 id 로 묶어두면 한 번에 뽑을 수 있다.
 *
 * <pre>
 * grep 'requestId=3f2a...' app.log
 * </pre>
 *
 * <p>프론트가 {@code X-Request-Id} 를 보내면 그 값을 쓴다. 안 보내면 서버가 만든다.
 * 응답에도 같은 헤더로 돌려주므로, 사용자가 화면에서 본 오류를 로그에서 찾을 수 있다.
 *
 * <h2>컨트롤러에서 로그를 찍지 않는 이유</h2>
 * "요청 들어옴 / 나감" 로그를 각 컨트롤러가 찍으면 같은 코드가 도메인마다 복사된다.
 * 여기서 한 번만 찍고, 각 파트는 <b>비즈니스 사건</b>만 남긴다.
 *
 * <p>필터 순서는 가장 바깥이다. 인증 필터보다 먼저 돌아야 인증 실패(401)도 기록된다.
 * 사용자 id 는 {@link #logCompletion}에서 MDC 를 다시 읽어 채운다 — 그 시점에는
 * 인증 필터가 이미 값을 넣어뒀다.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** 헬스체크·메트릭은 초 단위로 들어와서 로그를 덮는다. */
    private static final String[] SKIPPED_PREFIXES = {"/actuator", "/images"};

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);
        MDC.put(LogKeys.REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long startedAt = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            logCompletion(request, response, startedAt);
            // 톰캣은 스레드를 재사용한다. 안 지우면 다음 요청에 남의 requestId 가 붙는다.
            MDC.clear();
        }
    }

    /**
     * 로그를 남기지 않을 경로. <b>필터 자체는 건너뛰지 않는다.</b>
     *
     * <p>{@code shouldNotFilter} 로 통째로 건너뛰면 MDC 정리도 안 돌아서, 그 경로에 토큰을
     * 실어 보낸 요청의 userId 가 스레드에 남아 다음 요청 로그에 붙는다.
     */
    static boolean isSkipped(String path) {
        for (String prefix : SKIPPED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private void logCompletion(HttpServletRequest request, HttpServletResponse response,
            long startedAt) {
        if (isSkipped(request.getRequestURI())) {
            return;
        }
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        int status = response.getStatus();
        String userId = MDC.get(LogKeys.USER_ID);

        // 5xx 는 우리 문제라 WARN 이상으로 올린다. 4xx 는 클라이언트 문제라 INFO 로 둔다.
        if (status >= 500) {
            log.warn("{} method={} path={} status={} elapsed_ms={} user={}",
                    LogKeys.EVENT_HTTP, request.getMethod(), request.getRequestURI(),
                    status, elapsedMs, userId);
        } else {
            log.info("{} method={} path={} status={} elapsed_ms={} user={}",
                    LogKeys.EVENT_HTTP, request.getMethod(), request.getRequestURI(),
                    status, elapsedMs, userId);
        }
    }

    /**
     * 클라이언트가 준 값은 그대로 믿지 않는다. 로그 파일에 개행이나 제어문자가 섞이면
     * 한 줄을 여러 줄로 위조할 수 있고(로그 인젝션), 길이 제한이 없으면 로그가 부풀어 오른다.
     */
    private static String resolveRequestId(HttpServletRequest request) {
        String given = request.getHeader(REQUEST_ID_HEADER);
        if (given == null || given.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String sanitized = given.replaceAll("[^A-Za-z0-9._-]", "");
        return sanitized.isEmpty()
                ? UUID.randomUUID().toString()
                : sanitized.substring(0, Math.min(64, sanitized.length()));
    }
}
