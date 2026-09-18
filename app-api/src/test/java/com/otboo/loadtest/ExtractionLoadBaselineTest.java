package com.otboo.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.clothes.extraction.AttributeDefinitionSnapshot;
import com.otboo.clothes.extraction.ClothesExtractionMetrics;
import com.otboo.clothes.extraction.ClothesExtractionProperties;
import com.otboo.clothes.extraction.GeminiClothesExtractionClient;
import com.otboo.clothes.extraction.ProductPageData;
import com.otboo.clothes.extraction.RemoteResource;
import com.otboo.common.http.ApiSettings;
import com.otboo.common.http.ExternalApiClientFactory;
import com.otboo.common.http.ExternalApiProperties;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** 실제 Gemini 호출 없이 의상 Gemini 구간의 동시 처리 기준선을 측정한다. */
class ExtractionLoadBaselineTest {

    private static final String MODEL = "gemini-test";
    private static final String PRODUCT_URL = "https://www.musinsa.com/products/load-test";

    @Test
    void measuresConcurrentGeminiCallsAgainstFakeServer()
            throws IOException, InterruptedException, ExecutionException {
        Assumptions.assumeTrue(
                loadEnabled(),
                "Set CLOTHES_LOAD_ENABLED=true to execute the load baseline");

        int requestCount = positiveProperty(
                "clothes.load.requests", "CLOTHES_LOAD_REQUESTS", 20);
        int concurrency = Math.min(
                requestCount,
                positiveProperty("clothes.load.concurrency", "CLOTHES_LOAD_CONCURRENCY", 5));
        int fakeDelayMs = nonNegativeProperty(
                "clothes.load.fake-delay-ms", "CLOTHES_LOAD_FAKE_DELAY_MS", 50);
        AtomicInteger fakeCallCount = new AtomicInteger();
        HttpServer fakeGemini = startFakeGemini(fakeCallCount, fakeDelayMs);
        ExecutorService workers = Executors.newFixedThreadPool(concurrency);

        try {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            GeminiClothesExtractionClient client = new GeminiClothesExtractionClient(
                    new ExternalApiClientFactory(testApiProperties()),
                    new ObjectMapper(),
                    properties(),
                    new ClothesExtractionMetrics(registry),
                    "http://127.0.0.1:" + fakeGemini.getAddress().getPort());
            ProductPageData page = page();
            List<RemoteResource> images = images();
            warmUp(client, page, images, Math.min(5, requestCount));
            registry.clear();
            fakeCallCount.set(0);
            CountDownLatch ready = new CountDownLatch(concurrency);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger requestIndex = new AtomicInteger();
            List<Long> elapsedMillis = Collections.synchronizedList(new ArrayList<>());
            List<Future<?>> tasks = new ArrayList<>();

            for (int worker = 0; worker < concurrency; worker++) {
                tasks.add(workers.submit(() -> runRequests(
                        client,
                        page,
                        images,
                        requestCount,
                        requestIndex,
                        ready,
                        start,
                        elapsedMillis)));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            long benchmarkStartedAt = System.nanoTime();
            start.countDown();
            for (Future<?> task : tasks) {
                task.get();
            }
            long benchmarkElapsedMs = elapsedMillis(benchmarkStartedAt);

            Collections.sort(elapsedMillis);
            long p50 = percentile(elapsedMillis, 0.50);
            long p95 = percentile(elapsedMillis, 0.95);
            double averageTokens = registry.get("otboo_clothes_gemini_tokens")
                    .tag("shop", "musinsa")
                    .tag("kind", "total")
                    .summary()
                    .mean();
            double averageImages = registry.get("otboo_clothes_extraction_images")
                    .tag("shop", "musinsa")
                    .summary()
                    .mean();
            System.out.printf(
                    "clothes_load_baseline requests=%d concurrency=%d fake_delay_ms=%d "
                            + "elapsed_ms=%d p50_ms=%d p95_ms=%d avg_total_tokens=%.0f "
                            + "avg_images=%.1f%n",
                    requestCount,
                    concurrency,
                    fakeDelayMs,
                    benchmarkElapsedMs,
                    p50,
                    p95,
                    averageTokens,
                    averageImages);

            assertThat(fakeCallCount).hasValue(requestCount);
            assertThat(elapsedMillis).hasSize(requestCount);
            assertThat(registry.get("otboo_clothes_gemini")
                    .tag("shop", "musinsa")
                    .tag("outcome", "success")
                    .timer()
                    .count()).isEqualTo(requestCount);
        } finally {
            workers.shutdownNow();
            fakeGemini.stop(0);
        }
    }

    private void warmUp(
            GeminiClothesExtractionClient client,
            ProductPageData page,
            List<RemoteResource> images,
            int requestCount
    ) {
        for (int request = 0; request < requestCount; request++) {
            client.extract(page, images, List.<AttributeDefinitionSnapshot>of());
        }
    }

    private void runRequests(
            GeminiClothesExtractionClient client,
            ProductPageData page,
            List<RemoteResource> images,
            int requestCount,
            AtomicInteger requestIndex,
            CountDownLatch ready,
            CountDownLatch start,
            List<Long> elapsedMillis
    ) {
        ready.countDown();
        await(start);
        for (int request = requestIndex.getAndIncrement(); request < requestCount;
                request = requestIndex.getAndIncrement()) {
            long startedAt = System.nanoTime();
            client.extract(page, images, List.<AttributeDefinitionSnapshot>of());
            elapsedMillis.add(elapsedMillis(startedAt));
        }
    }

    private HttpServer startFakeGemini(AtomicInteger callCount, int delayMs) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1beta/models/" + MODEL + ":generateContent", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                callCount.incrementAndGet();
                if (delayMs > 0) {
                    Thread.sleep(delayMs);
                }
                byte[] response = response().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                exchange.sendResponseHeaders(503, -1);
            } finally {
                exchange.close();
            }
        });
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return server;
    }

    private String response() {
        return """
                {
                  "candidates": [{
                    "content": {"parts": [{"text": "{\\"name\\":\\"테스트 상품\\",\\"type\\":\\"TOP\\",\\"attributes\\":[],\\"ambiguities\\":[]}"}]}
                  }],
                  "usageMetadata": {
                    "promptTokenCount": 100,
                    "candidatesTokenCount": 20,
                    "totalTokenCount": 120
                  }
                }
                """;
    }

    private ExternalApiProperties testApiProperties() {
        ApiSettings defaults = new ApiSettings(
                Duration.ofSeconds(3), Duration.ofSeconds(10), 0, Duration.ZERO, null);
        return new ExternalApiProperties(defaults, Map.of());
    }

    private ClothesExtractionProperties properties() {
        return new ClothesExtractionProperties(
                "test-key", MODEL, 3, 2 * 1024 * 1024,
                10 * 1024 * 1024, 25 * 1024 * 1024, 4, 15_000, 200);
    }

    private ProductPageData page() {
        return new ProductPageData(
                URI.create(PRODUCT_URL),
                "테스트 상품",
                "테스트 설명",
                URI.create("https://cdn.example.com/main.jpg"),
                List.of(),
                List.of());
    }

    private List<RemoteResource> images() {
        return List.of(new RemoteResource(
                URI.create("https://cdn.example.com/main.jpg"),
                "image/jpeg",
                "test-image".getBytes(StandardCharsets.UTF_8)));
    }

    private void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("load test was interrupted", exception);
        }
    }

    private long percentile(List<Long> sorted, double quantile) {
        int index = (int) Math.ceil(quantile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private boolean loadEnabled() {
        return Boolean.parseBoolean(setting(
                "clothes.load.enabled", "CLOTHES_LOAD_ENABLED", "false"));
    }

    private int positiveProperty(String propertyName, String environmentName, int defaultValue) {
        return Math.max(1, integerSetting(propertyName, environmentName, defaultValue));
    }

    private int nonNegativeProperty(
            String propertyName,
            String environmentName,
            int defaultValue
    ) {
        return Math.max(0, integerSetting(propertyName, environmentName, defaultValue));
    }

    private int integerSetting(String propertyName, String environmentName, int defaultValue) {
        try {
            return Integer.parseInt(setting(propertyName, environmentName, String.valueOf(defaultValue)));
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private String setting(String propertyName, String environmentName, String defaultValue) {
        String systemValue = System.getProperty(propertyName);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null || environmentValue.isBlank()
                ? defaultValue
                : environmentValue;
    }
}
