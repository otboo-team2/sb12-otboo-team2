package com.otboo.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /** 체인이 도는 "그 시점"의 MDC 값을 잡아둔다. 필터가 끝나면 지워지기 때문이다. */
    private String captureRequestIdDuringChain(MockHttpServletRequest request,
            MockHttpServletResponse response) throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (req, res) -> seen.set(MDC.get(LogKeys.REQUEST_ID));
        filter.doFilter(request, response, chain);
        return seen.get();
    }

    @Nested
    @DisplayName("requestId")
    class RequestId {

        @Test
        @DisplayName("헤더가 없으면 서버가 만들어 넣는다")
        void 없으면_생성한다() throws Exception {
            MockHttpServletResponse response = new MockHttpServletResponse();

            String during = captureRequestIdDuringChain(
                    new MockHttpServletRequest("GET", "/api/feeds"), response);

            assertThat(during).isNotBlank();
            // 사용자가 화면에서 본 오류를 로그에서 찾으려면 응답에도 같은 값이 있어야 한다
            assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER))
                    .isEqualTo(during);
        }

        @Test
        @DisplayName("프론트가 보낸 값을 그대로 이어받는다")
        void 헤더가_있으면_이어받는다() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/feeds");
            request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "front-abc-123");

            assertThat(captureRequestIdDuringChain(request, new MockHttpServletResponse()))
                    .isEqualTo("front-abc-123");
        }

        @Test
        @DisplayName("개행이 섞인 값은 걸러낸다 — 로그 한 줄을 여러 줄로 위조할 수 있다")
        void 로그_인젝션을_막는다() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/feeds");
            request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER,
                    "abc\n2026-01-01 ERROR 가짜로그");

            String during = captureRequestIdDuringChain(request, new MockHttpServletResponse());

            assertThat(during).doesNotContain("\n").doesNotContain(" ");
        }

        @Test
        @DisplayName("긴 값은 잘라낸다")
        void 길이를_제한한다() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/feeds");
            request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "a".repeat(500));

            assertThat(captureRequestIdDuringChain(request, new MockHttpServletResponse()))
                    .hasSize(64);
        }
    }

    @Nested
    @DisplayName("MDC 정리")
    class Cleanup {

        @Test
        @DisplayName("끝나면 지운다 — 톰캣이 스레드를 재사용해서 남의 id 가 붙는다")
        void 요청이_끝나면_비운다() throws Exception {
            filter.doFilter(new MockHttpServletRequest("GET", "/api/feeds"),
                    new MockHttpServletResponse(), (req, res) -> {
                    });

            assertThat(MDC.get(LogKeys.REQUEST_ID)).isNull();
        }

        @Test
        @DisplayName("체인에서 예외가 나도 지운다")
        void 예외가_나도_비운다() {
            FilterChain exploding = (req, res) -> {
                throw new IllegalStateException("boom");
            };

            try {
                filter.doFilter(new MockHttpServletRequest("GET", "/api/feeds"),
                        new MockHttpServletResponse(), exploding);
            } catch (Exception ignored) {
                // 필터는 예외를 삼키지 않는다. 여기서는 정리만 확인한다.
            }

            assertThat(MDC.get(LogKeys.REQUEST_ID)).isNull();
        }
    }

    @Nested
    @DisplayName("건너뛰는 경로")
    class Skipped {

        @Test
        @DisplayName("actuator 는 로그를 남기지 않는다 — 초 단위로 들어와 로그를 덮는다")
        void actuator는_건너뛴다() {
            assertThat(RequestLoggingFilter.isSkipped("/actuator/health")).isTrue();
            assertThat(RequestLoggingFilter.isSkipped("/images/profiles/a.png")).isTrue();
        }

        @Test
        @DisplayName("일반 API 는 남긴다")
        void api는_기록한다() {
            assertThat(RequestLoggingFilter.isSkipped("/api/feeds")).isFalse();
        }

        @Test
        @DisplayName("로그를 건너뛰는 경로에서도 MDC 는 정리한다")
        void 건너뛰어도_mdc는_비운다() throws Exception {
            MDC.put(LogKeys.USER_ID, "leaked-user");

            filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"),
                    new MockHttpServletResponse(), (req, res) -> {
                    });

            assertThat(MDC.get(LogKeys.USER_ID)).isNull();
        }
    }
}
