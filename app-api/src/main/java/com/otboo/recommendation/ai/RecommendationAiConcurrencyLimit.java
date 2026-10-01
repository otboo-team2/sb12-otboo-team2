package com.otboo.recommendation.ai;

import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * OpenAI 를 기다리는 추천 요청 수의 상한.
 *
 * <p>추천은 OpenAI 를 요청 스레드에서 동기로 3번 부른다. 운영 로그상 요청 시간의 95% 가 그 대기였고
 * (16.2 s 중 15.4 s), OpenAI 가 느려진 날은 호출 하나가 10~45 s 걸리다 60 s 에서 끊겼다.
 * 상한이 없으면 그런 날 추천 요청이 몰리는 만큼 톰캣 스레드가 묶여 AI 와 무관한 API 까지 밀린다.
 *
 * <p>상한을 넘는 요청은 기다리지 않는다. 대체 응답(기본 추천)이 이미 있으니 바로 그쪽으로 보낸다.
 * 그래서 OpenAI 가 아무리 느려도 묶이는 스레드는 최대 {@code maxConcurrent} 개다.
 */
@Component
public class RecommendationAiConcurrencyLimit {

    static final String IN_FLIGHT_METRIC = "otboo_recommendation_ai_in_flight";
    static final String REJECTED_METRIC = "otboo_recommendation_ai_rejected";

    private final int maxConcurrent;
    private final Semaphore permits;
    private final AtomicLong rejected = new AtomicLong();

    public RecommendationAiConcurrencyLimit(int maxConcurrent) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("maxConcurrent must be >= 1: " + maxConcurrent);
        }
        this.maxConcurrent = maxConcurrent;
        this.permits = new Semaphore(maxConcurrent);
    }

    @Autowired
    public RecommendationAiConcurrencyLimit(
            @Value("${otboo.recommendation.ai.max-concurrent:20}") int maxConcurrent,
            MeterRegistry registry
    ) {
        this(maxConcurrent);
        Gauge.builder(IN_FLIGHT_METRIC, this, RecommendationAiConcurrencyLimit::inFlight)
                .description("OpenAI 를 기다리고 있는 추천 요청 수")
                .register(registry);
        FunctionCounter.builder(REJECTED_METRIC, this, RecommendationAiConcurrencyLimit::rejected)
                .description("상한에 걸려 기본 추천으로 보낸 요청 수")
                .register(registry);
    }

    /** 자리가 있으면 들어가고 {@code true}. 없으면 기다리지 않고 {@code false} */
    public boolean tryEnter() {
        if (permits.tryAcquire()) {
            return true;
        }
        rejected.incrementAndGet();
        return false;
    }

    public void exit() {
        permits.release();
    }

    public int inFlight() {
        return maxConcurrent - permits.availablePermits();
    }

    public long rejected() {
        return rejected.get();
    }
}
