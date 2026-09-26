package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ClothesExtractionMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ClothesExtractionMetrics metrics = new ClothesExtractionMetrics(registry);

    @Test
    void recordsExtractionElapsedTimeAndAcceptedAttributesWithBoundedTags() {
        Timer.Sample sample = metrics.startTimer();

        metrics.recordExtraction(
                sample,
                URI.create("https://www.musinsa.com/products/1"),
                ClothesExtractionMetrics.Outcome.PARTIAL,
                3);

        assertThat(timer(
                ClothesExtractionMetrics.EXTRACTION_METRIC,
                "musinsa",
                "partial").count()).isEqualTo(1);
        assertThat(summary(
                ClothesExtractionMetrics.ATTRIBUTE_COUNT_METRIC,
                "shop",
                "musinsa").totalAmount()).isEqualTo(3);
    }

    @Test
    void recordsGeminiElapsedTimeImagesAndTokens() {
        Timer.Sample sample = metrics.startTimer();

        metrics.recordGemini(
                sample,
                URI.create("https://www.29cm.co.kr/products/1"),
                ClothesExtractionMetrics.Outcome.SUCCESS,
                5,
                12_345,
                100,
                20,
                120);

        assertThat(timer(
                ClothesExtractionMetrics.GEMINI_METRIC,
                "29cm",
                "success").count()).isEqualTo(1);
        assertThat(summary(
                ClothesExtractionMetrics.IMAGE_COUNT_METRIC,
                "shop",
                "29cm").totalAmount()).isEqualTo(5);
        assertThat(summary(
                ClothesExtractionMetrics.IMAGE_BYTES_METRIC,
                "shop",
                "29cm").totalAmount()).isEqualTo(12_345);
        assertThat(tokenSummary("prompt").totalAmount()).isEqualTo(100);
        assertThat(tokenSummary("candidate").totalAmount()).isEqualTo(20);
        assertThat(tokenSummary("total").totalAmount()).isEqualTo(120);
    }

    @Test
    void mapsUnknownHostsToOtherAndDoesNotRecordUnavailableTokens() {
        Timer.Sample sample = metrics.startTimer();

        metrics.recordGemini(
                sample,
                URI.create("https://shop.example.com/products/1"),
                ClothesExtractionMetrics.Outcome.ERROR,
                2,
                500,
                null,
                null,
                null);

        assertThat(timer(
                ClothesExtractionMetrics.GEMINI_METRIC,
                "other",
                "error").count()).isEqualTo(1);
        assertThat(registry.find(ClothesExtractionMetrics.TOKEN_METRIC).summary()).isNull();
    }

    @Test
    void recordsDifferentCountsForEachCStageWithoutHighCardinalityTags() {
        metrics.recordStage("ably", "c", "db18", "success", Duration.ofMillis(300));
        metrics.recordImageFlow("ably", "c", 30, 20, 8, 5, 6, 12_000_000);

        assertThat(registry.get("otboo_clothes_extraction_stage")
                .tag("mode", "c").tag("stage", "db18").timer().count()).isEqualTo(1);
        assertThat(registry.get("otboo_clothes_extraction_stage")
                .tag("stage", "db18")
                .tag("model_version", ClothesExtractionMetrics.DB18_MODEL_VERSION)
                .timer()
                .count()).isEqualTo(1);
        assertThat(stageImageCount("collection")).isEqualTo(30);
        assertThat(stageImageCount("download")).isEqualTo(20);
        assertThat(stageImageCount("selector")).isEqualTo(8);
        assertThat(stageImageCount("db18")).isEqualTo(5);
        assertThat(stageImageCount("gemini")).isEqualTo(6);
        assertThat(registry.get("otboo_clothes_extraction_stage_bytes")
                .tag("shop", "ably")
                .tag("mode", "c")
                .tag("stage", "gemini")
                .summary()
                .totalAmount()).isEqualTo(12_000_000);
        assertThat(registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(io.micrometer.core.instrument.Tag::getKey))
                .doesNotContain("url", "product_id", "candidate", "path");
        assertThat(ClothesExtractionMetrics.EXTRACTION_METRIC).isEqualTo("otboo_clothes_extraction");
        assertThat(ClothesExtractionMetrics.GEMINI_METRIC).isEqualTo("otboo_clothes_gemini");
    }

    @Test
    void exportsDb18StageMetricsToPrometheusAlongsideOtherStages() throws Exception {
        Class<?> prometheusConfigType =
                Class.forName("io.micrometer.prometheusmetrics.PrometheusConfig");
        Class<?> prometheusRegistryType =
                Class.forName("io.micrometer.prometheusmetrics.PrometheusMeterRegistry");
        Object prometheusRegistry = prometheusRegistryType
                .getConstructor(prometheusConfigType)
                .newInstance(prometheusConfigType.getField("DEFAULT").get(null));
        ClothesExtractionMetrics prometheusMetrics = new ClothesExtractionMetrics(
                (MeterRegistry) prometheusRegistry);

        prometheusMetrics.recordStage("musinsa", "c", "selector", "success", Duration.ofMillis(100));
        prometheusMetrics.recordStage("musinsa", "c", "db18", "success", Duration.ofMillis(250));
        prometheusMetrics.recordStageImageCount("musinsa", "c", "selector", 8);
        prometheusMetrics.recordStageImageCount("musinsa", "c", "db18", 3);
        prometheusMetrics.recordStageImageBytes("musinsa", "c", "selector", 1_000);
        prometheusMetrics.recordStageImageBytes("musinsa", "c", "db18", 300);
        prometheusMetrics.recordFailureReason("musinsa", "c", "selector", "external_error");
        prometheusMetrics.recordFailureReason("musinsa", "c", "db18", "model_error");

        String scrape = (String) prometheusRegistryType.getMethod("scrape").invoke(prometheusRegistry);
        String[] metricLines = scrape.split("\\R");

        assertThat(metricLines).anyMatch(line ->
                line.startsWith("otboo_clothes_extraction_stage_seconds_count{")
                        && line.contains("stage=\"db18\""));
        assertThat(metricLines).anyMatch(line ->
                line.startsWith("otboo_clothes_extraction_stage_images_sum{")
                        && line.contains("stage=\"db18\"")
                        && line.endsWith(" 3.0"));
        assertThat(metricLines).anyMatch(line ->
                line.startsWith("otboo_clothes_extraction_stage_bytes_sum{")
                        && line.contains("stage=\"db18\"")
                        && line.endsWith(" 300.0"));
        assertThat(metricLines).anyMatch(line ->
                line.startsWith("otboo_clothes_extraction_stage_failures_total{")
                        && line.contains("stage=\"db18\""));
    }

    @Test
    void restrictsMetricTagValuesAndFailureReasonsToFixedCategories() {
        metrics.recordStage(
                "https://shop.example.com/products/98765",
                "product-98765",
                "candidate-23",
                "unexpected",
                Duration.ofMillis(1));
        metrics.recordFailureReason(
                "https://shop.example.com/products/98765",
                "product-98765",
                "candidate-23",
                "raw-exception-message");

        assertThat(registry.get("otboo_clothes_extraction_stage")
                .tag("shop", "other")
                .tag("mode", "other")
                .tag("stage", "other")
                .tag("outcome", "error")
                .timer()
                .count()).isEqualTo(1);
        assertThat(registry.get("otboo_clothes_extraction_stage_failures")
                .tag("shop", "other")
                .tag("mode", "other")
                .tag("stage", "other")
                .tag("reason", "model_error")
                .counter()
                .count()).isEqualTo(1);
    }

    private double stageImageCount(String stage) {
        return registry.get("otboo_clothes_extraction_stage_images")
                .tag("shop", "ably")
                .tag("mode", "c")
                .tag("stage", stage)
                .summary()
                .totalAmount();
    }

    private Timer timer(String metricName, String shop, String outcome) {
        return registry.get(metricName)
                .tag("shop", shop)
                .tag("outcome", outcome)
                .timer();
    }

    private DistributionSummary summary(String metricName, String tag, String value) {
        return registry.get(metricName)
                .tag(tag, value)
                .summary();
    }

    private DistributionSummary tokenSummary(String kind) {
        return registry.get(ClothesExtractionMetrics.TOKEN_METRIC)
                .tag("shop", "29cm")
                .tag("kind", kind)
                .summary();
    }
}
