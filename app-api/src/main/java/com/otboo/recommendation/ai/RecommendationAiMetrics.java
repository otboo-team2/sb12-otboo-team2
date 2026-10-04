package com.otboo.recommendation.ai;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * AI 추천 한 건이 어떻게 끝났는지(실제 AI 결과 / 기본 추천 / 오류)와 단계별 소요 시간을 남긴다.
 *
 * <p>HTTP 200 만으로는 AI 가 답했는지 기본 추천으로 빠졌는지 구분할 수 없다. 그래서 결과와 전환 사유를
 * 태그로 나눈다. 태그 값은 아래 상수의 고정 집합뿐이라 시계열이 늘지 않는다.
 */
@Component
public class RecommendationAiMetrics {

    static final String DURATION_METRIC = "otboo_recommendation_ai_duration";
    static final String STAGE_METRIC = "otboo_recommendation_ai_stage";

    static final String OUTCOME_AI = "ai";
    static final String OUTCOME_FALLBACK = "fallback";
    static final String OUTCOME_ERROR = "error";

    private final MeterRegistry registry;

    public RecommendationAiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 추천 요청 전체(후보 조회 포함) 소요 시간 */
    void recordRequest(String outcome, String reason, long elapsedNanos) {
        Timer.builder(DURATION_METRIC)
                .description("AI 추천 요청 전체 소요 시간. outcome=ai 만 실제 AI 결과다")
                .tag("outcome", outcome)
                .tag("reason", reason)
                .register(registry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    /** 조건 추출·임베딩·검색·생성 등 단계 하나의 소요 시간. 캐시 적중이면 짧게 찍힌다 */
    void recordStage(String stage, boolean isSuccess, long elapsedNanos) {
        Timer.builder(STAGE_METRIC)
                .description("AI 추천 단계별 소요 시간")
                .tag("stage", stage)
                .tag("result", isSuccess ? "ok" : "fail")
                .register(registry)
                .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }
}
