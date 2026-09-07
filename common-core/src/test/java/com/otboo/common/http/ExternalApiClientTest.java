package com.otboo.common.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ExternalApiClientTest {

    private static final String URI = "https://example.com/data";

    /** 테스트마다 재시도 정책이 달라야 해서 클라이언트를 그때그때 만든다. */
    private record Fixture(ExternalApiClient client, MockRestServiceServer server) {
    }

    private Fixture fixture(int maxRetries, Long dailyLimit) {
        return fixture(maxRetries, dailyLimit, Clock.systemUTC());
    }

    private Fixture fixture(int maxRetries, Long dailyLimit, Clock clock) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ApiSettings settings = new ApiSettings(
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                maxRetries,
                Duration.ofMillis(1),       // 테스트가 백오프만큼 느려지지 않게
                dailyLimit);
        ExternalApiClient client = new ExternalApiClient(
                "test-api", builder.build(), settings, new DailyCallCounter(clock));
        return new Fixture(client, server);
    }

    @Nested
    @DisplayName("재시도")
    class Retry {

        @Test
        @DisplayName("기본값 0 이면 5xx 여도 한 번만 호출한다")
        void 재시도_기본값은_0() {
            Fixture f = fixture(0, null);
            f.server().expect(ExpectedCount.once(), requestTo(URI)).andRespond(withServerError());

            assertThatThrownBy(() -> f.client().get(URI, String.class))
                    .isInstanceOf(BusinessException.class);

            f.server().verify();    // 두 번 불렀다면 여기서 깨진다
        }

        @Test
        @DisplayName("5xx 는 max-retries 만큼 더 시도한다")
        void 서버오류는_재시도한다() {
            Fixture f = fixture(2, null);
            f.server().expect(ExpectedCount.times(3), requestTo(URI)).andRespond(withServerError());

            assertThatThrownBy(() -> f.client().get(URI, String.class))
                    .isInstanceOf(BusinessException.class);

            f.server().verify();    // 최초 1회 + 재시도 2회
        }

        @Test
        @DisplayName("재시도 중 성공하면 그 결과를 돌려준다")
        void 재시도_중_성공() {
            Fixture f = fixture(2, null);
            f.server().expect(ExpectedCount.once(), requestTo(URI)).andRespond(withServerError());
            f.server().expect(ExpectedCount.once(), requestTo(URI))
                    .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

            assertThat(f.client().get(URI, String.class)).isEqualTo("ok");

            f.server().verify();
        }

        @Test
        @DisplayName("4xx 는 재시도하지 않는다 — 다시 보내도 결과가 같다")
        void 클라이언트오류는_재시도하지_않는다() {
            Fixture f = fixture(3, null);
            f.server().expect(ExpectedCount.once(), requestTo(URI))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST));

            assertThatThrownBy(() -> f.client().get(URI, String.class))
                    .isInstanceOf(BusinessException.class);

            f.server().verify();
        }

        @Test
        @DisplayName("429 는 재시도한다")
        void 요청과다는_재시도한다() {
            Fixture f = fixture(1, null);
            f.server().expect(ExpectedCount.times(2), requestTo(URI))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

            assertThatThrownBy(() -> f.client().get(URI, String.class))
                    .isInstanceOf(BusinessException.class);

            f.server().verify();
        }
    }

    @Nested
    @DisplayName("에러 변환")
    class Translate {

        @Test
        @DisplayName("외부 오류는 500 이 아니라 COMMON_901 로 나간다")
        void 외부오류는_502() {
            Fixture f = fixture(0, null);
            f.server().expect(requestTo(URI)).andRespond(withServerError());

            assertThatThrownBy(() -> f.client().get(URI, String.class))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR);
        }

        @Test
        @DisplayName("어느 API 가 실패했는지 details 에 남는다")
        void 실패한_api_이름이_남는다() {
            Fixture f = fixture(0, null);
            f.server().expect(requestTo(URI)).andRespond(withServerError());

            assertThatThrownBy(() -> f.client().get(URI, String.class))
                    .isInstanceOfSatisfying(BusinessException.class, e ->
                            assertThat(e.getDetails())
                                    .containsEntry("api", "test-api")
                                    .containsEntry("status", "500"));
        }
    }

    @Nested
    @DisplayName("하루 호출 상한")
    class DailyLimit {

        @Test
        @DisplayName("상한을 넘으면 호출 자체를 안 한다")
        void 상한초과시_호출하지_않는다() {
            Fixture f = fixture(0, 1L);
            f.server().expect(ExpectedCount.once(), requestTo(URI))
                    .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

            assertThat(f.client().get(URI, String.class)).isEqualTo("ok");

            assertThatThrownBy(() -> f.client().get(URI, String.class))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(CommonErrorCode.EXTERNAL_API_LIMIT_EXCEEDED);

            f.server().verify();    // 두 번째는 네트워크로 나가지 않았다
        }

        @Test
        @DisplayName("날짜가 바뀌면 상한이 초기화된다")
        void 날짜가_바뀌면_초기화() {
            MutableClock clock = new MutableClock(Instant.parse("2026-09-07T23:59:00Z"));
            DailyCallCounter counter = new DailyCallCounter(clock);

            assertThat(counter.tryAcquire(1L)).isTrue();
            assertThat(counter.tryAcquire(1L)).isFalse();

            clock.moveTo(Instant.parse("2026-09-08T00:01:00Z"));

            assertThat(counter.tryAcquire(1L)).isTrue();
            assertThat(counter.currentCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("상한이 없으면 무제한이다")
        void 상한_없으면_무제한() {
            DailyCallCounter counter = new DailyCallCounter(Clock.systemUTC());
            for (int i = 0; i < 1000; i++) {
                assertThat(counter.tryAcquire(null)).isTrue();
            }
        }
    }

    /** 날짜 경과를 재현하기 위한 테스트용 시계. */
    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void moveTo(Instant next) {
            this.instant = next;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
