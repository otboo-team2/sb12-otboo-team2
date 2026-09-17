package com.otboo.clothes.extraction;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import org.springframework.stereotype.Component;

/** 의상 링크 자동입력의 처리 시간과 외부 AI 사용량을 낮은 카디널리티 지표로 기록한다. */
@Component
public class ClothesExtractionMetrics {

    static final String EXTRACTION_METRIC = "otboo_clothes_extraction";
    static final String GEMINI_METRIC = "otboo_clothes_gemini";
    static final String IMAGE_COUNT_METRIC = "otboo_clothes_extraction_images";
    static final String IMAGE_BYTES_METRIC = "otboo_clothes_extraction_image_bytes";
    static final String TOKEN_METRIC = "otboo_clothes_gemini_tokens";
    static final String ATTRIBUTE_COUNT_METRIC = "otboo_clothes_extraction_attributes";

    private final MeterRegistry registry;

    public ClothesExtractionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordExtraction(
            Timer.Sample sample,
            URI productUrl,
            Outcome outcome,
            int acceptedAttributeCount
    ) {
        String shop = shop(productUrl);
        sample.stop(timer(
                EXTRACTION_METRIC,
                "의상 링크 자동입력 전체 처리 시간",
                shop,
                outcome));
        if (outcome != Outcome.ERROR) {
            summary(
                    ATTRIBUTE_COUNT_METRIC,
                    "자동입력에서 확정한 의상 속성 수",
                    shop)
                    .record(Math.max(0, acceptedAttributeCount));
        }
    }

    public void recordGemini(
            Timer.Sample sample,
            URI productUrl,
            Outcome outcome,
            int imageCount,
            long imageBytes,
            Integer promptTokens,
            Integer candidateTokens,
            Integer totalTokens
    ) {
        String shop = shop(productUrl);
        sample.stop(timer(
                GEMINI_METRIC,
                "Gemini 의상 정보 분석 시간",
                shop,
                outcome));
        summary(
                IMAGE_COUNT_METRIC,
                "Gemini 요청에 포함한 이미지 수",
                shop)
                .record(Math.max(0, imageCount));
        summary(
                IMAGE_BYTES_METRIC,
                "Gemini 요청에 포함한 이미지 전체 바이트",
                shop)
                .record(Math.max(0, imageBytes));
        recordTokens(shop, "prompt", promptTokens);
        recordTokens(shop, "candidate", candidateTokens);
        recordTokens(shop, "total", totalTokens);
    }

    private Timer timer(String name, String description, String shop, Outcome outcome) {
        return Timer.builder(name)
                .description(description)
                .tag("shop", shop)
                .tag("outcome", outcome.tagValue)
                .register(registry);
    }

    private DistributionSummary summary(String name, String description, String shop) {
        return DistributionSummary.builder(name)
                .description(description)
                .tag("shop", shop)
                .register(registry);
    }

    private void recordTokens(String shop, String kind, Integer tokens) {
        if (tokens == null) {
            return;
        }
        DistributionSummary.builder(TOKEN_METRIC)
                .description("Gemini 요청과 응답의 토큰 수")
                .tag("shop", shop)
                .tag("kind", kind)
                .register(registry)
                .record(Math.max(0, tokens));
    }

    private String shop(URI productUrl) {
        if (productUrl == null || productUrl.getHost() == null) {
            return "other";
        }
        String host = productUrl.getHost().toLowerCase(java.util.Locale.ROOT);
        if (isDomain(host, "musinsa.com")) {
            return "musinsa";
        }
        if (isDomain(host, "29cm.co.kr")) {
            return "29cm";
        }
        if (isDomain(host, "a-bly.com")) {
            return "ably";
        }
        return "other";
    }

    private boolean isDomain(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    public enum Outcome {
        SUCCESS("success"),
        PARTIAL("partial"),
        ERROR("error");

        private final String tagValue;

        Outcome(String tagValue) {
            this.tagValue = tagValue;
        }
    }
}
