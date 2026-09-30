package com.otboo.config;

import com.otboo.auth.exception.AuthErrorCode;
import com.otboo.common.exception.BusinessException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해싱의 동시 실행 수를 제한한다.
 *
 * <p>BCrypt 는 호출 하나가 코어 하나를 약 0.1초 쓴다. 상한이 없으면 요청 스레드(최대 200)가
 * 전부 해싱에 들어가 코어를 나눠 먹고, 짧은 트랜잭션도 도중에 CPU 를 뺏겨 커넥션을 오래 쥔다.
 * 부하테스트(1 vCPU, 로그인 20 RPS)에서 커넥션 평균 점유가 515 ms → 상한 1 을 두자 8 ms.
 *
 * <p>상한을 넘는 호출은 {@code maxWait} 까지 기다린다(기다리는 스레드는 CPU 를 쓰지 않는다).
 * 그래도 차례가 안 오면 503 으로 바로 돌려준다. 끝없이 줄 세우면 과부하 때 로그인이 수십 초 걸리고,
 * 사용자는 이미 떠난 요청을 서버가 계속 처리한다.
 *
 * <p>{@code maxQueue} 가 있으면 줄이 그만큼 차 있을 때 기다리지 않고 즉시 돌려준다. 어차피 거절될 요청이
 * 3초 동안 스레드를 쥐고 있을 이유가 없다.
 */
public class BoundedPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;
    private final Semaphore permits;
    private final Duration maxWait;
    private final int maxQueue;
    private final AtomicLong rejected = new AtomicLong();

    public BoundedPasswordEncoder(PasswordEncoder delegate, int maxConcurrency, Duration maxWait) {
        this(delegate, maxConcurrency, maxWait, Integer.MAX_VALUE);
    }

    /** @param maxQueue 이만큼 줄이 차 있으면 기다리지 않고 즉시 503 */
    public BoundedPasswordEncoder(PasswordEncoder delegate, int maxConcurrency, Duration maxWait, int maxQueue) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be >= 1: " + maxConcurrency);
        }
        this.delegate = delegate;
        this.permits = new Semaphore(maxConcurrency, true); // 먼저 온 요청부터. 꼬리 지연을 막는다
        this.maxWait = maxWait;
        this.maxQueue = maxQueue;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return bounded(() -> delegate.encode(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return bounded(() -> delegate.matches(rawPassword, encodedPassword));
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }

    /** 해싱 차례를 기다리는 스레드 수 */
    public int waiting() {
        return permits.getQueueLength();
    }

    /** 기다리다 포기하고 503 을 돌려준 누적 횟수 */
    public long rejected() {
        return rejected.get();
    }

    private <T> T bounded(Supplier<T> task) {
        if (permits.getQueueLength() >= maxQueue) {
            throw reject();
        }
        try {
            if (!permits.tryAcquire(maxWait.toMillis(), TimeUnit.MILLISECONDS)) {
                throw reject();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for password hashing", e);
        }
        try {
            return task.get();
        } finally {
            permits.release();
        }
    }

    private BusinessException reject() {
        rejected.incrementAndGet();
        return new BusinessException(AuthErrorCode.PASSWORD_HASH_BUSY);
    }
}
