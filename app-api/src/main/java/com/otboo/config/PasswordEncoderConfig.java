package com.otboo.config;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 비밀번호 인코더를 {@code SecurityConfig} 밖에 둔다.
 *
 * <p>안에 두면 순환 참조가 생긴다. 인코더가 필요한 {@code AuthService} 가
 * {@code SecurityConfig} 에 의존하게 되는데, {@code SecurityConfig} 는 소셜 로그인
 * 성공 핸들러를 필요로 하고 그 핸들러가 다시 {@code AuthService} 를 쓴다.
 *
 * <p>애초에 비밀번호 해싱은 웹 보안 설정이 아니라 독립적인 관심사다.
 */
@Configuration
public class PasswordEncoderConfig {

    static final String WAITING_METRIC = "otboo_password_hash_waiting";
    static final String REJECTED_METRIC = "otboo_password_hash_rejected";

    /**
     * @param maxConcurrency 0 이면 JVM 이 보는 코어 수. ⚠️ Fargate 1 vCPU 에서도 JVM 은 2 로 본다 —
     *                       배포 환경에서는 vCPU 수를 명시한다
     * @param maxWait        해싱 차례를 기다리는 최대 시간. 넘으면 503
     */
    @Bean
    public PasswordEncoder passwordEncoder(
            @Value("${otboo.auth.password-hash-concurrency:0}") int maxConcurrency,
            @Value("${otboo.auth.password-hash-max-wait:3s}") Duration maxWait,
            MeterRegistry registry
    ) {
        int limit = maxConcurrency > 0 ? maxConcurrency : Runtime.getRuntime().availableProcessors();
        BoundedPasswordEncoder encoder = new BoundedPasswordEncoder(new BCryptPasswordEncoder(), limit, maxWait);
        Gauge.builder(WAITING_METRIC, encoder, BoundedPasswordEncoder::waiting)
                .description("비밀번호 해싱 차례를 기다리는 스레드 수")
                .register(registry);
        FunctionCounter.builder(REJECTED_METRIC, encoder, BoundedPasswordEncoder::rejected)
                .description("해싱 대기 한도를 넘어 503 으로 돌려준 횟수")
                .register(registry);
        return encoder;
    }
}
