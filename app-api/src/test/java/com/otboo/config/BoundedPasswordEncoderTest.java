package com.otboo.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.auth.exception.AuthErrorCode;
import com.otboo.common.exception.BusinessException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class BoundedPasswordEncoderTest {

    @Test
    @DisplayName("동시에 몰려도 상한 수만큼만 해싱하고, 나머지는 기다렸다가 전부 처리된다")
    void limitsConcurrency() throws Exception {
        SlowEncoder slow = new SlowEncoder();
        BoundedPasswordEncoder encoder = new BoundedPasswordEncoder(slow, 2, Duration.ofSeconds(10));
        int callers = 16;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger matched = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(callers);
        for (int i = 0; i < callers; i++) {
            pool.submit(() -> {
                start.await();
                if (encoder.matches("pw", "pw")) {
                    matched.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(slow.maxRunning.get()).isEqualTo(2);
        assertThat(matched).hasValue(callers);
        assertThat(encoder.waiting()).isZero();
    }

    @Test
    @DisplayName("대기 한도를 넘기면 해싱하지 않고 503 으로 돌려준다")
    void rejectsAfterMaxWait() throws Exception {
        SlowEncoder slow = new SlowEncoder(500);
        BoundedPasswordEncoder encoder = new BoundedPasswordEncoder(slow, 1, Duration.ofMillis(50));
        Thread holder = new Thread(() -> encoder.matches("pw", "pw"));
        holder.start();
        Thread.sleep(100); // holder 가 자리를 잡을 때까지

        assertThatThrownBy(() -> encoder.matches("pw", "pw"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(AuthErrorCode.PASSWORD_HASH_BUSY));
        assertThat(encoder.rejected()).isEqualTo(1);
        holder.join();
        assertThat(slow.maxRunning.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("상한은 1 이상이어야 한다")
    void rejectsZero() {
        assertThatThrownBy(() -> new BoundedPasswordEncoder(new SlowEncoder(), 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 실행 중인 호출 수의 최댓값을 기록하는 느린 인코더 */
    private static class SlowEncoder implements PasswordEncoder {

        final AtomicInteger running = new AtomicInteger();
        final AtomicInteger maxRunning = new AtomicInteger();
        private final long sleepMillis;

        SlowEncoder() {
            this(50);
        }

        SlowEncoder(long sleepMillis) {
            this.sleepMillis = sleepMillis;
        }

        @Override
        public String encode(CharSequence rawPassword) {
            return rawPassword.toString();
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                running.decrementAndGet();
            }
            return rawPassword.toString().equals(encodedPassword);
        }
    }
}
