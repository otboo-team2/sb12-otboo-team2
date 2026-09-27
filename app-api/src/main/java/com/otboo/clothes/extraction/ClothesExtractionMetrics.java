package com.otboo.clothes.extraction;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
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
    static final String STAGE_METRIC = "otboo_clothes_extraction_stage";
    static final String STAGE_IMAGE_COUNT_METRIC = "otboo_clothes_extraction_stage_images";
    static final String STAGE_IMAGE_BYTES_METRIC = "otboo_clothes_extraction_stage_bytes";
    static final String STAGE_FAILURE_METRIC = "otboo_clothes_extraction_stage_failures";
    static final String DB18_MODEL_VERSION = "db18-td500-r18-v1";
    private static final String NOT_APPLICABLE_MODEL_VERSION = "not_applicable";

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

    public void recordStage(
            String shop,
            String mode,
            String stage,
            String outcome,
            Duration duration
    ) {
        String normalizedStage = normalizeStage(stage);
        Timer.Builder timer = Timer.builder(STAGE_METRIC)
                .description("의상 링크 자동입력 단계별 처리 시간")
                .tag("shop", normalizeShop(shop))
                .tag("mode", normalizeMode(mode))
                .tag("stage", normalizedStage)
                .tag("outcome", normalizeOutcome(outcome))
                .tag("model_version", modelVersion(normalizedStage));
        timer.register(registry).record(nonNegative(duration));
    }

    public void recordStage(
            URI productUrl,
            String mode,
            String stage,
            String outcome,
            Duration duration
    ) {
        recordStage(shop(productUrl), mode, stage, outcome, duration);
    }

    public void recordImageFlow(
            String shop,
            String mode,
            int discoveredImages,
            int downloadedImages,
            int selectorImages,
            int db18Images,
            int geminiImages,
            long geminiBytes
    ) {
        recordStageImageCount(shop, mode, "collection", discoveredImages);
        recordStageImageCount(shop, mode, "download", downloadedImages);
        recordStageImageCount(shop, mode, "selector", selectorImages);
        recordStageImageCount(shop, mode, "db18", db18Images);
        recordStageImageCount(shop, mode, "gemini", geminiImages);
        recordStageImageBytes(shop, mode, "gemini", geminiBytes);
    }

    public void recordImageFlow(
            URI productUrl,
            String mode,
            int discoveredImages,
            int downloadedImages,
            int selectorImages,
            int db18Images,
            int geminiImages,
            long geminiBytes
    ) {
        recordImageFlow(
                shop(productUrl), mode, discoveredImages, downloadedImages,
                selectorImages, db18Images, geminiImages, geminiBytes);
    }

    public void recordStageImageBytes(String shop, String mode, String stage, long bytes) {
        String normalizedStage = normalizeStage(stage);
        DistributionSummary.Builder summary = DistributionSummary.builder(STAGE_IMAGE_BYTES_METRIC)
                .description("의상 링크 자동입력 단계에서 처리한 이미지 바이트")
                .tag("shop", normalizeShop(shop))
                .tag("mode", normalizeMode(mode))
                .tag("stage", normalizedStage)
                .tag("model_version", modelVersion(normalizedStage));
        summary.register(registry).record(Math.max(0, bytes));
    }

    public void recordStageImageBytes(URI productUrl, String mode, String stage, long bytes) {
        recordStageImageBytes(shop(productUrl), mode, stage, bytes);
    }

    public void recordFailureReason(String shop, String mode, String stage, String reason) {
        String normalizedStage = normalizeStage(stage);
        Counter.Builder counter = Counter.builder(STAGE_FAILURE_METRIC)
                .description("의상 링크 자동입력 단계별 고정 원인 실패 수")
                .tag("shop", normalizeShop(shop))
                .tag("mode", normalizeMode(mode))
                .tag("stage", normalizedStage)
                .tag("reason", normalizeReason(reason))
                .tag("model_version", modelVersion(normalizedStage));
        counter.register(registry).increment();
    }

    public void recordFailureReason(URI productUrl, String mode, String stage, String reason) {
        recordFailureReason(shop(productUrl), mode, stage, reason);
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

    public void recordStageImageCount(String shop, String mode, String stage, int imageCount) {
        String normalizedStage = normalizeStage(stage);
        DistributionSummary.Builder summary = DistributionSummary.builder(STAGE_IMAGE_COUNT_METRIC)
                .description("의상 링크 자동입력 단계별 이미지 수")
                .tag("shop", normalizeShop(shop))
                .tag("mode", normalizeMode(mode))
                .tag("stage", normalizedStage)
                .tag("model_version", modelVersion(normalizedStage));
        summary.register(registry).record(Math.max(0, imageCount));
    }

    public void recordStageImageCount(
            URI productUrl,
            String mode,
            String stage,
            int imageCount
    ) {
        recordStageImageCount(shop(productUrl), mode, stage, imageCount);
    }

    private Duration nonNegative(Duration duration) {
        return duration == null || duration.isNegative() ? Duration.ZERO : duration;
    }

    private String modelVersion(String stage) {
        return "db18".equals(stage) ? DB18_MODEL_VERSION : NOT_APPLICABLE_MODEL_VERSION;
    }

    private String normalizeShop(String shop) {
        if (shop == null) {
            return "other";
        }
        String normalized = shop.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "musinsa", "29cm", "ably", "other" -> normalized;
            default -> "other";
        };
    }

    private String normalizeMode(String mode) {
        if (mode == null) {
            return "other";
        }
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "b0" -> "b0";
            case "c" -> "c";
            default -> "other";
        };
    }

    private String normalizeStage(String stage) {
        if (stage == null) {
            return "other";
        }
        String normalized = stage.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "collection", "download", "selector", "db18", "gemini" -> normalized;
            default -> "other";
        };
    }

    private String normalizeOutcome(String outcome) {
        if (outcome == null) {
            return "error";
        }
        String normalized = outcome.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "success", "partial", "error" -> normalized;
            default -> "error";
        };
    }

    private String normalizeReason(String reason) {
        if (reason == null) {
            return "model_error";
        }
        String normalized = reason.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "busy", "timeout", "model_error", "scan_limit", "decode_error" -> normalized;
            default -> "model_error";
        };
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
