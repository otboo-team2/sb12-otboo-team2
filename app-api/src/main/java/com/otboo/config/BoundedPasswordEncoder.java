package com.otboo.config;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 해싱의 동시 실행 수를 제한한다.
 *
 * <p>BCrypt 는 호출 하나가 코어 하나를 약 0.1초 쓴다. 상한이 없으면 요청 스레드(최대 200)가
 * 전부 해싱에 들어가 코어를 나눠 먹고, 연결 수락·헬스체크처럼 가벼운 일까지 CPU 차례가 오지 않는다.
 * 부하테스트(1 vCPU)에서 로그인 30 RPS 때 ALB 가 새 연결을 못 맺어 502 가 났고 헬스체크도 실패했다.
 *
 * <p>상한을 넘는 호출은 거절하지 않고 기다린다. 기다리는 스레드는 CPU 를 쓰지 않는다.
 */
public class BoundedPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;
    private final Semaphore permits;

    public BoundedPasswordEncoder(PasswordEncoder delegate, int maxConcurrency) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be >= 1: " + maxConcurrency);
        }
        this.delegate = delegate;
        this.permits = new Semaphore(maxConcurrency, true); // 먼저 온 요청부터. 꼬리 지연을 막는다
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

    private <T> T bounded(Supplier<T> task) {
        try {
            permits.acquire();
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
}
