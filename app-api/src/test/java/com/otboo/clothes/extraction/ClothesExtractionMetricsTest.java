package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
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
