package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.otboo.clothes.extraction.TextRegionDetector.TextBox;
import com.otboo.clothes.extraction.TextRegionDetector.TextDetectionResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.LoggerFactory;

class CImageSelectionServiceTest {

    private static final URI PRODUCT_URL = URI.create("https://shop.example.com/products/1");
    private static final URI IMAGE_URL = URI.create("https://cdn.example.com/primary.png");

    @TempDir
    Path tempRoot;

    @Mock SafeRemoteResourceClient remoteResourceClient;
    @Mock CImageFeatureScorer featureScorer;

    private AutoCloseable mocks;
    private CImageAnalysisExecutor analysisExecutor;
    private CImageSelectionService service;
    private SimpleMeterRegistry meterRegistry;
    private FixtureTextDetector textDetector;
    private Logger logger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        CImageSelectionProperties properties = new CImageSelectionProperties(
                CImageSelectionProperties.Mode.C,
                null,
                "",
                tempRoot.resolve("spool"),
                8,
                1024 * 1024,
                200_000_000,
                1,
                Duration.ofMillis(50),
                Duration.ofSeconds(3));
        ClothesExtractionProperties clothesProperties = new ClothesExtractionProperties(
                "test-key", "test-model", 3, 1024, 1024, 25 * 1024 * 1024, 6, 1000, 200);
        analysisExecutor = new CImageAnalysisExecutor(properties);
        meterRegistry = new SimpleMeterRegistry();
        textDetector = new FixtureTextDetector();
        service = new CImageSelectionService(
                remoteResourceClient,
                featureScorer,
                new CImageSelector(8),
                new CImageFilter(textDetector),
                analysisExecutor,
                properties,
                clothesProperties,
                new ClothesExtractionMetrics(meterRegistry));
        logger = (Logger) LoggerFactory.getLogger(CImageSelectionService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() throws Exception {
        logger.detachAppender(logAppender);
        logAppender.stop();
        analysisExecutor.close();
        mocks.close();
    }

    @Test
    void downloadsInDiscoveryOrderAndReturnsOnlyFilteredOriginalImages() throws Exception {
        ProductPageData page = pageWithDetails(8);
        given(remoteResourceClient.getImage(any(URI.class)))
                .willAnswer(invocation -> resource(invocation.getArgument(0)));
        given(featureScorer.score(any(CImageCandidate.class)))
                .willAnswer(invocation -> score(invocation.getArgument(0)));

        CImageSelectionResult result = service.selectDetails(page, Set.of(IMAGE_URL), 10_000);

        assertThat(result.discoveredCandidateCount()).isEqualTo(8);
        assertThat(result.downloadedCandidateCount()).isEqualTo(8);
        assertThat(stageImageCount("download")).isEqualTo(8);
        assertThat(stageImageCount("selector")).isEqualTo(8);
        assertThat(stageImageCount("db18")).isEqualTo(2);
        assertThat(textDetector.invocationCount()).isEqualTo(8);
        assertThat(meterRegistry.get("otboo_clothes_extraction_stage_bytes")
                .tag("shop", "other")
                .tag("mode", "c")
                .tag("stage", "db18")
                .tag("model_version", ClothesExtractionMetrics.DB18_MODEL_VERSION)
                .summary()
                .totalAmount()).isPositive();
        assertThat(result.selectedCandidateIndexes()).containsExactly(1, 7);
        assertThat(result.images()).extracting(RemoteResource::finalUri)
                .containsExactly(detailUrl(2), detailUrl(8));
        assertThat(result.failedDownloadCount()).isZero();
        assertThat(meterRegistry.get("otboo_clothes_extraction_stage")
                .tag("shop", "other")
                .tag("mode", "c")
                .tag("stage", "download")
                .tag("outcome", "success")
                .timer()
                .count()).isEqualTo(1);
        assertThat(meterRegistry.get("otboo_clothes_extraction_stage")
                .tag("shop", "other")
                .tag("mode", "c")
                .tag("stage", "selector")
                .tag("outcome", "success")
                .timer()
                .count()).isEqualTo(1);
        assertThat(meterRegistry.get("otboo_clothes_extraction_stage")
                .tag("shop", "other")
                .tag("mode", "c")
                .tag("stage", "db18")
                .tag("outcome", "success")
                .timer()
                .count()).isEqualTo(1);
        InOrder order = inOrder(remoteResourceClient);
        for (int index = 1; index <= 8; index++) {
            order.verify(remoteResourceClient).getImage(detailUrl(index));
        }
        assertThat(tempRoot.resolve("spool")).exists();
        try (var children = java.nio.file.Files.list(tempRoot.resolve("spool"))) {
            assertThat(children.count()).isZero();
        }
    }

    @Test
    void recordsOneDownloadFailureAndContinuesWithLaterImages() throws Exception {
        ProductPageData page = pageWithDetails(8);
        given(remoteResourceClient.getImage(detailUrl(1))).willReturn(resource(detailUrl(1)));
        given(remoteResourceClient.getImage(detailUrl(3))).willThrow(
                new IllegalStateException("remote failed"));
        for (int index = 2; index <= 8; index++) {
            if (index == 3) {
                continue;
            }
            given(remoteResourceClient.getImage(detailUrl(index))).willReturn(resource(detailUrl(index)));
        }
        given(featureScorer.score(any(CImageCandidate.class)))
                .willAnswer(invocation -> score(invocation.getArgument(0)));

        CImageSelectionResult result = service.selectDetails(page, Set.of(IMAGE_URL), 10_000);

        assertThat(result.failedDownloadCount()).isEqualTo(1);
        assertThat(result.downloadFailures()).hasSize(1);
        assertThat(result.images()).hasSize(2);
        verify(remoteResourceClient).getImage(detailUrl(3));
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("c_image_selection_completed discovered_count=8 downloaded_count=7"));
    }

    @Test
    void logsSelectedCandidateIndexesWithoutRemoteImageData() throws Exception {
        ProductPageData page = pageWithDetails(8);
        given(remoteResourceClient.getImage(any(URI.class)))
                .willAnswer(invocation -> resource(invocation.getArgument(0)));
        given(featureScorer.score(any(CImageCandidate.class)))
                .willAnswer(invocation -> score(invocation.getArgument(0)));

        service.selectDetails(page, Set.of(IMAGE_URL), 10_000);

        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> {
                    assertThat(message).contains(
                        "c_image_selection_completed discovered_count=8 "
                                + "downloaded_count=8 selector_count=8 db18_count=2 "
                                + "failed_download_count=0 selected_candidate_indexes=[1, 7] "
                                + "route=opencv_db18 selector_executed=true "
                                + "selector_input_count=8 db18_executed=true db18_input_count=8");
                    assertThat(message).doesNotContain("cdn.example.com");
                });
    }

    @Test
    void sendsAtMostSixDownloadedCandidatesDirectlyToGemini() throws Exception {
        given(remoteResourceClient.getImage(any(URI.class)))
                .willAnswer(invocation -> {
                    URI uri = invocation.getArgument(0);
                    if (uri.equals(detailUrl(7)) || uri.equals(detailUrl(8))) {
                        throw new IllegalStateException("remote failed");
                    }
                    return resource(uri);
                });
        given(featureScorer.score(any(CImageCandidate.class)))
                .willAnswer(invocation -> score(invocation.getArgument(0)));

        CImageSelectionResult result = service.selectDetails(
                pageWithDetails(8), Set.of(IMAGE_URL), 10_000);

        assertThat(result.downloadedCandidateCount()).isEqualTo(6);
        assertThat(result.selectedCandidateIndexes()).containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(result.images()).extracting(RemoteResource::finalUri)
                .containsExactly(
                        detailUrl(1), detailUrl(2), detailUrl(3),
                        detailUrl(4), detailUrl(5), detailUrl(6));
        assertThat(stageImageCount("selector")).isZero();
        assertThat(stageImageCount("db18")).isZero();
        assertThat(textDetector.invocationCount()).isZero();
        verify(featureScorer, never()).score(any(CImageCandidate.class));
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> {
                    assertThat(message).contains(
                            "route=gemini_direct", "db18_count=0", "selector_executed=false",
                            "db18_executed=false", "downloaded_count=6");
                    assertThat(message).doesNotContain("cdn.example.com");
                });
    }

    @Test
    void routesSevenDownloadedCandidatesThroughOpenCvAndDb18AfterOneDownloadFails()
            throws Exception {
        given(remoteResourceClient.getImage(any(URI.class)))
                .willAnswer(invocation -> {
                    URI uri = invocation.getArgument(0);
                    if (uri.equals(detailUrl(8))) {
                        throw new IllegalStateException("remote failed");
                    }
                    return resource(uri);
                });
        given(featureScorer.score(any(CImageCandidate.class)))
                .willAnswer(invocation -> score(invocation.getArgument(0)));

        CImageSelectionResult result = service.selectDetails(
                pageWithDetails(8), Set.of(IMAGE_URL), 10_000);

        assertThat(result.downloadedCandidateCount()).isEqualTo(7);
        assertThat(result.selectedCandidateIndexes()).containsExactly(1);
        assertThat(result.images()).extracting(RemoteResource::finalUri)
                .containsExactly(detailUrl(2));
        assertThat(stageImageCount("selector")).isEqualTo(7);
        assertThat(stageImageCount("db18")).isEqualTo(1);
        assertThat(textDetector.invocationCount()).isEqualTo(7);
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains(
                        "route=opencv_db18", "selector_executed=true",
                        "selector_input_count=7", "db18_executed=true",
                        "db18_input_count=7"));
    }

    @Test
    void routesEightDownloadedCandidatesThroughOpenCvAndDb18() throws Exception {
        given(remoteResourceClient.getImage(any(URI.class)))
                .willAnswer(invocation -> resource(invocation.getArgument(0)));
        given(featureScorer.score(any(CImageCandidate.class)))
                .willAnswer(invocation -> score(invocation.getArgument(0)));

        CImageSelectionResult result = service.selectDetails(
                pageWithDetails(8), Set.of(IMAGE_URL), 10_000);

        assertThat(result.downloadedCandidateCount()).isEqualTo(8);
        assertThat(result.selectedCandidateIndexes()).containsExactly(1, 7);
        assertThat(result.images()).extracting(RemoteResource::finalUri)
                .containsExactly(detailUrl(2), detailUrl(8));
        assertThat(stageImageCount("selector")).isEqualTo(8);
        assertThat(stageImageCount("db18")).isEqualTo(2);
        assertThat(textDetector.invocationCount()).isEqualTo(8);
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains(
                        "route=opencv_db18", "selector_executed=true",
                        "selector_input_count=8", "db18_executed=true",
                        "db18_input_count=8"));
    }

    @Test
    void keepsOpenCvAndDb18ForMoreThanEightDownloadedCandidates() throws Exception {
        given(remoteResourceClient.getImage(any(URI.class)))
                .willAnswer(invocation -> resource(invocation.getArgument(0)));
        given(featureScorer.score(any(CImageCandidate.class)))
                .willAnswer(invocation -> score(invocation.getArgument(0)));

        CImageSelectionResult result = service.selectDetails(
                pageWithDetails(9), Set.of(IMAGE_URL), 10_000);

        assertThat(result.downloadedCandidateCount()).isEqualTo(9);
        assertThat(result.selectedCandidateIndexes()).containsExactly(1, 7);
        assertThat(stageImageCount("selector")).isEqualTo(8);
        assertThat(stageImageCount("db18")).isEqualTo(2);
        assertThat(textDetector.invocationCount()).isEqualTo(8);
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains(
                        "route=opencv_db18", "selector_executed=true",
                        "db18_executed=true", "db18_input_count=8"));
    }

    @Test
    void skipsAnalysisWhenThereAreNoDetailImageUrls() {
        CImageSelectionResult result = service.selectDetails(
                new ProductPageData(PRODUCT_URL, "상품", "설명", IMAGE_URL, List.of(), List.of()),
                Set.of(IMAGE_URL),
                10_000);

        assertThat(result.images()).isEmpty();
        assertThat(result.discoveredCandidateCount()).isZero();
        verify(remoteResourceClient, never()).getImage(any(URI.class));
        verify(featureScorer, never()).score(any(CImageCandidate.class));
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains(
                        "c_image_selection_completed discovered_count=0 downloaded_count=0",
                        "route=gemini_direct", "selector_executed=false",
                        "db18_executed=false"));
    }

    @Test
    void allowsRepresentativeOnlyWhenEveryDetailDownloadFails() {
        given(remoteResourceClient.getImage(any(URI.class)))
                .willThrow(new IllegalStateException("remote failed"));

        CImageSelectionResult result = service.selectDetails(
                pageWithDetails(3), Set.of(IMAGE_URL), 10_000);

        assertThat(result.images()).isEmpty();
        assertThat(result.failedDownloadCount()).isEqualTo(3);
        assertThat(result.downloadFailures()).hasSize(3);
        verify(featureScorer, never()).score(any(CImageCandidate.class));
        assertThat(logAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message).contains(
                        "c_image_selection_completed discovered_count=3 downloaded_count=0",
                        "failed_download_count=3", "route=gemini_direct",
                        "selector_executed=false", "db18_executed=false"));
    }

    @Test
    void continuesWithoutDetailImagesWhenDownloadedCandidatesAreAllFilteredOut() throws Exception {
        ProductPageData page = pageWithDetails(7);
        textDetector.suppressText();
        given(remoteResourceClient.getImage(any(URI.class)))
                .willAnswer(invocation -> resource(invocation.getArgument(0)));
        given(featureScorer.score(any(CImageCandidate.class)))
                .willAnswer(invocation -> score(invocation.getArgument(0)));

        CImageSelectionResult result = service.selectDetails(page, Set.of(IMAGE_URL), 10_000);

        assertThat(result.selectedCandidateIndexes()).isEmpty();
        assertThat(result.images()).isEmpty();
        assertThat(meterRegistry.get("otboo_clothes_extraction_stage")
                .tag("shop", "other")
                .tag("mode", "c")
                .tag("stage", "db18")
                .tag("outcome", "partial")
                .timer()
                .count()).isEqualTo(1);
        assertThat(meterRegistry.getMeters())
                .noneMatch(meter -> meter.getId().getName()
                        .equals("otboo_clothes_extraction_stage_failures"));
        assertThat(textDetector.invocationCount()).isEqualTo(7);
    }

    @Test
    void rejectsMoreThanTwoHundredDiscoveredImagesBeforeDownloading() {
        ProductPageData page = pageWithDetails(201);

        assertThatThrownBy(() -> service.selectDetails(page, Set.of(), 10_000))
                .isInstanceOfSatisfying(CImageAnalysisException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(CImageAnalysisException.Reason.SCAN_LIMIT));
        verify(remoteResourceClient, never()).getImage(any(URI.class));
    }

    @Test
    void rejectsSelectedImagesThatExceedRemainingGeminiByteBudget() throws Exception {
        ProductPageData page = pageWithDetails(2);
        given(remoteResourceClient.getImage(any(URI.class)))
                .willAnswer(invocation -> resource(invocation.getArgument(0)));
        given(featureScorer.score(any(CImageCandidate.class)))
                .willAnswer(invocation -> score(invocation.getArgument(0)));

        assertThatThrownBy(() -> service.selectDetails(page, Set.of(), 1))
                .isInstanceOfSatisfying(CImageAnalysisException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(CImageAnalysisException.Reason.SCAN_LIMIT));
    }

    @Test
    void keepsRequestFilesUntilTimedOutNativeAnalysisActuallyReturns() throws Exception {
        CountDownLatch detectorStarted = new CountDownLatch(1);
        CountDownLatch allowDetectorToFinish = new CountDownLatch(1);
        CImageSelectionProperties shortTimeoutProperties = new CImageSelectionProperties(
                CImageSelectionProperties.Mode.C,
                null,
                "",
                tempRoot.resolve("timeout-spool"),
                8,
                1024 * 1024,
                200_000_000,
                1,
                Duration.ofMillis(50),
                Duration.ofMillis(150));
        ClothesExtractionProperties clothesProperties = new ClothesExtractionProperties(
                "test-key", "test-model", 3, 1024, 1024, 25 * 1024 * 1024, 6, 1000, 200);
        given(remoteResourceClient.getImage(any(URI.class)))
                .willAnswer(invocation -> resource(invocation.getArgument(0)));
        given(featureScorer.score(any(CImageCandidate.class)))
                .willAnswer(invocation -> score(invocation.getArgument(0)));
        TextRegionDetector slowDetector = candidate -> {
            detectorStarted.countDown();
            boolean finished = false;
            while (!finished) {
                try {
                    finished = allowDetectorToFinish.await(10, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    // Native analysis may not stop immediately when the caller times out.
                }
            }
            return new TextDetectionResult(List.of(new TextBox(List.of(
                    new TextRegionDetector.TextPoint(0, 0),
                    new TextRegionDetector.TextPoint(4, 0),
                    new TextRegionDetector.TextPoint(4, 4),
                    new TextRegionDetector.TextPoint(0, 4)), 0.9)), Duration.ZERO);
        };
        try (CImageAnalysisExecutor timeoutExecutor = new CImageAnalysisExecutor(shortTimeoutProperties)) {
            CImageSelectionService timeoutService = new CImageSelectionService(
                    remoteResourceClient,
                    featureScorer,
                    new CImageSelector(8),
                    new CImageFilter(slowDetector),
                    timeoutExecutor,
                    shortTimeoutProperties,
                    clothesProperties,
                    new ClothesExtractionMetrics(new SimpleMeterRegistry()));

            assertThatThrownBy(() -> timeoutService.selectDetails(
                    pageWithDetails(7), Set.of(), 10_000))
                    .isInstanceOfSatisfying(CImageAnalysisException.class, exception ->
                            assertThat(exception.reason())
                                    .isEqualTo(CImageAnalysisException.Reason.TIMEOUT));
            assertThat(detectorStarted.await(1, TimeUnit.SECONDS)).isTrue();
            Path spoolRoot = shortTimeoutProperties.tempDirectory();
            try (var requestDirectories = java.nio.file.Files.list(spoolRoot)) {
                assertThat(requestDirectories.count()).isEqualTo(1);
            }

            allowDetectorToFinish.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            long remaining;
            do {
                try (var requestDirectories = java.nio.file.Files.list(spoolRoot)) {
                    remaining = requestDirectories.count();
                }
                if (remaining == 0) {
                    break;
                }
                Thread.sleep(10);
            } while (System.nanoTime() < deadline);
            assertThat(remaining).isZero();
        } finally {
            allowDetectorToFinish.countDown();
        }
    }

    private double stageImageCount(String stage) {
        return meterRegistry.get("otboo_clothes_extraction_stage_images")
                .tag("shop", "other")
                .tag("mode", "c")
                .tag("stage", stage)
                .summary()
                .totalAmount();
    }

    private ProductPageData pageWithDetails(int count) {
        List<URI> details = java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(CImageSelectionServiceTest::detailUrl)
                .toList();
        return new ProductPageData(PRODUCT_URL, "상품", "설명", IMAGE_URL, details, List.of());
    }

    private CImageFeatureScore score(CImageCandidate candidate) {
        return new CImageFeatureScore(candidate, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 1);
    }

    private static URI detailUrl(int index) {
        return URI.create("https://cdn.example.com/detail-" + index + ".png");
    }

    private static RemoteResource resource(URI uri) throws Exception {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        image.flush();
        return new RemoteResource(uri, "image/png", output.toByteArray());
    }

    private static final class FixtureTextDetector implements TextRegionDetector {

        private final AtomicInteger invocations = new AtomicInteger();
        private volatile boolean textSuppressed;

        @Override
        public TextDetectionResult detect(CImageCandidate candidate) {
            invocations.incrementAndGet();
            if (textSuppressed
                    || candidate.candidateIndex() != 1 && candidate.candidateIndex() != 7) {
                return new TextDetectionResult(List.of(), Duration.ZERO);
            }
            return new TextDetectionResult(List.of(new TextBox(List.of(
                    new TextRegionDetector.TextPoint(0, 0),
                    new TextRegionDetector.TextPoint(4, 0),
                    new TextRegionDetector.TextPoint(4, 4),
                    new TextRegionDetector.TextPoint(0, 4)), 0.9)), Duration.ZERO);
        }

        private int invocationCount() {
            return invocations.get();
        }

        private void suppressText() {
            textSuppressed = true;
        }
    }
}
