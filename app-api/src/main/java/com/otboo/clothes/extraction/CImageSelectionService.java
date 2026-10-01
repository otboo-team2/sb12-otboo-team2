package com.otboo.clothes.extraction;

import com.otboo.clothes.dto.ClothesExtractionFailureDto;
import com.otboo.clothes.extraction.CImageFilter.CFilterResult;
import com.otboo.clothes.extraction.CImageSelector.CSelectorResult;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 안전하게 다운로드된 상세 이미지를 C selector와 DB18로 선별한다. */
public class CImageSelectionService {

    private static final Logger log = LoggerFactory.getLogger(CImageSelectionService.class);
    private static final String DETAIL_IMAGE_FIELD = "detailImage";
    private static final int MAX_DB18_CANDIDATES = 8;

    private final SafeRemoteResourceClient remoteResourceClient;
    private final CImageFeatureScorer featureScorer;
    private final CImageSelector selector;
    private final CImageFilter filter;
    private final CImageAnalysisExecutor analysisExecutor;
    private final CImageSelectionProperties selectionProperties;
    private final ClothesExtractionProperties extractionProperties;
    private final ClothesExtractionMetrics metrics;

    public CImageSelectionService(
            SafeRemoteResourceClient remoteResourceClient,
            CImageFeatureScorer featureScorer,
            CImageSelector selector,
            CImageFilter filter,
            CImageAnalysisExecutor analysisExecutor,
            CImageSelectionProperties selectionProperties,
            ClothesExtractionProperties extractionProperties,
            ClothesExtractionMetrics metrics
    ) {
        this.remoteResourceClient = remoteResourceClient;
        this.featureScorer = featureScorer;
        this.selector = selector;
        this.filter = filter;
        this.analysisExecutor = analysisExecutor;
        this.selectionProperties = selectionProperties;
        this.extractionProperties = extractionProperties;
        this.metrics = metrics;
        if (selectionProperties.maxSelectedImages() <= 0
                || selectionProperties.maxSelectedImages() > MAX_DB18_CANDIDATES) {
            throw new IllegalArgumentException("C selector can send at most eight images to DB18");
        }
    }

    public CImageSelectionResult selectDetails(
            ProductPageData page,
            Set<URI> alreadyDownloadedUris,
            long remainingGeminiBytes
    ) {
        List<URI> detailImageUrls = page.detailImageUrls();
        if (detailImageUrls.size() > extractionProperties.maxDiscoveredImageCandidates()) {
            recordCFailure(page.productUrl(), "selector", "scan_limit", Duration.ZERO);
            throw new CImageAnalysisException(CImageAnalysisException.Reason.SCAN_LIMIT);
        }
        if (remainingGeminiBytes < 0) {
            recordCFailure(page.productUrl(), "selector", "scan_limit", Duration.ZERO);
            throw new CImageAnalysisException(CImageAnalysisException.Reason.SCAN_LIMIT);
        }
        if (detailImageUrls.isEmpty()) {
            metrics.recordStage(
                    page.productUrl(), "c", "download", "success", Duration.ZERO);
            metrics.recordStageImageCount(page.productUrl(), "c", "download", 0);
            metrics.recordStageImageCount(page.productUrl(), "c", "selector", 0);
            metrics.recordStageImageCount(page.productUrl(), "c", "db18", 0);
            metrics.recordStageImageBytes(page.productUrl(), "c", "download", 0);
            metrics.recordStageImageBytes(page.productUrl(), "c", "db18", 0);
            logDirectGeminiRoute(0, 0, 0);
            return emptyResult(0, 0, Duration.ZERO, Duration.ZERO);
        }

        long analysisStart = System.nanoTime();
        try {
            return analysisExecutor.execute(() -> selectDetailsWithinLimit(
                    page.productUrl(),
                    detailImageUrls,
                    alreadyDownloadedUris,
                    remainingGeminiBytes));
        } catch (CImageAnalysisException exception) {
            if (isExecutorFailure(exception.reason())) {
                recordCFailure(
                        page.productUrl(),
                        "selector",
                        failureReason(exception),
                        elapsedSince(analysisStart));
                metrics.recordStageImageCount(page.productUrl(), "c", "selector", 0);
                metrics.recordStageImageCount(page.productUrl(), "c", "db18", 0);
            }
            throw exception;
        }
    }

    private CImageSelectionResult selectDetailsWithinLimit(
            URI productUrl,
            List<URI> detailImageUrls,
            Set<URI> alreadyDownloadedUris,
            long remainingGeminiBytes
    ) {
        long downloadStart = System.nanoTime();
        int failedDownloads = 0;
        List<ClothesExtractionFailureDto> downloadFailures = new ArrayList<>();
        Set<URI> attemptedUris = new LinkedHashSet<>(alreadyDownloadedUris);
        try (CImageCandidateSpool spool = new CImageCandidateSpool(
                selectionProperties.tempDirectory(),
                selectionProperties.maxScanBytes(),
                selectionProperties.maxDecodedPixels(),
                extractionProperties.maxDiscoveredImageCandidates(),
                alreadyDownloadedUris)) {
            for (int index = 0; index < detailImageUrls.size(); index++) {
                URI detailUrl = detailImageUrls.get(index);
                if (!attemptedUris.add(detailUrl)) {
                    continue;
                }

                RemoteResource resource;
                try {
                    resource = remoteResourceClient.getImage(detailUrl);
                } catch (RuntimeException exception) {
                    failedDownloads++;
                    downloadFailures.add(imageFailure());
                    continue;
                }
                if (resource == null || resource.finalUri() == null) {
                    failedDownloads++;
                    downloadFailures.add(imageFailure());
                    continue;
                }
                try {
                    spool.store(index, detailUrl, resource);
                } catch (CImageAnalysisException exception) {
                    metrics.recordStageImageCount(
                            productUrl, "c", "download", spool.candidates().size());
                    metrics.recordStageImageBytes(
                            productUrl, "c", "download", spool.scannedBytes());
                    recordCFailure(
                            productUrl, "download", failureReason(exception),
                            elapsedSince(downloadStart));
                    throw exception;
                }
            }
            Duration downloadDuration = elapsedSince(downloadStart);
            metrics.recordStage(
                    productUrl,
                    "c",
                    "download",
                    failedDownloads == 0 ? "success" : "partial",
                    downloadDuration);
            List<CImageCandidate> candidates = spool.candidates();
            metrics.recordStageImageCount(productUrl, "c", "download", candidates.size());
            metrics.recordStageImageBytes(productUrl, "c", "download", spool.scannedBytes());
            if (candidates.isEmpty()) {
                metrics.recordStageImageCount(productUrl, "c", "selector", 0);
                metrics.recordStageImageCount(productUrl, "c", "db18", 0);
                metrics.recordStageImageBytes(productUrl, "c", "db18", 0);
                logDirectGeminiRoute(detailImageUrls.size(), 0, failedDownloads);
                return new CImageSelectionResult(
                        List.of(), List.of(), downloadFailures,
                        detailImageUrls.size(), 0, failedDownloads, spool.scannedBytes(),
                        downloadDuration, Duration.ZERO);
            }

            long analysisStart = System.nanoTime();
            String route = candidates.size() <= extractionProperties.maxDetailImages()
                    ? "gemini_direct"
                    : "opencv_db18";
            boolean selectorExecuted = "opencv_db18".equals(route);
            boolean db18Executed = !"gemini_direct".equals(route);

            List<CImageCandidate> db18Candidates = candidates;
            int selectorCount = 0;
            int selectorInputCount = 0;
            if (selectorExecuted) {
                long selectorStart = System.nanoTime();
                try {
                    List<CImageFeatureScore> scores = candidates.stream()
                            .map(featureScorer::score)
                            .toList();
                    CSelectorResult selectorResult = selector.select(scores);
                    db18Candidates = selectorResult.selected().stream()
                            .map(CImageFeatureScore::candidate)
                            .toList();
                    if (db18Candidates.size() > MAX_DB18_CANDIDATES) {
                        throw new CImageAnalysisException(
                                CImageAnalysisException.Reason.ANALYSIS_ERROR);
                    }
                } catch (RuntimeException exception) {
                    metrics.recordStageImageCount(productUrl, "c", "selector", 0);
                    recordCFailure(
                            productUrl, "selector", failureReason(exception),
                            elapsedSince(selectorStart));
                    throw exception;
                }
                selectorInputCount = candidates.size();
                selectorCount = db18Candidates.size();
                metrics.recordStageImageCount(productUrl, "c", "selector", selectorCount);
                metrics.recordStage(
                        productUrl, "c", "selector", "success", elapsedSince(selectorStart));
            } else {
                metrics.recordStageImageCount(productUrl, "c", "selector", 0);
            }

            int db18InputCount = db18Executed ? db18Candidates.size() : 0;
            long db18InputBytes = db18Executed
                    ? db18Candidates.stream().mapToLong(CImageCandidate::bytes).sum()
                    : 0;
            metrics.recordStageImageBytes(productUrl, "c", "db18", db18InputBytes);
            List<CImageCandidate> selected;
            if (db18Executed) {
                long db18Start = System.nanoTime();
                try {
                    CFilterResult filterResult = filter.filter(db18Candidates);
                    selected = filterResult.selected();
                } catch (RuntimeException exception) {
                    metrics.recordStageImageCount(productUrl, "c", "db18", 0);
                    recordCFailure(
                            productUrl, "db18", failureReason(exception), elapsedSince(db18Start));
                    throw exception;
                }
                metrics.recordStageImageCount(productUrl, "c", "db18", selected.size());
                metrics.recordStage(
                        productUrl,
                        "c",
                        "db18",
                        selected.isEmpty() ? "partial" : "success",
                        elapsedSince(db18Start));
            } else {
                selected = candidates;
                metrics.recordStageImageCount(productUrl, "c", "db18", 0);
            }
            log.info(
                    "c_image_selection_completed discovered_count={} downloaded_count={} "
                            + "selector_count={} db18_count={} failed_download_count={} "
                            + "selected_candidate_indexes={} route={} selector_executed={} "
                            + "selector_input_count={} db18_executed={} db18_input_count={}",
                    detailImageUrls.size(),
                    candidates.size(),
                    selectorCount,
                    db18Executed ? selected.size() : 0,
                    failedDownloads,
                    selected.stream().map(CImageCandidate::candidateIndex).toList(),
                    route,
                    selectorExecuted,
                    selectorInputCount,
                    db18Executed,
                    db18InputCount);
            List<RemoteResource> images = spool.toRemoteResources(selected, remainingGeminiBytes);
            Duration analysisDuration = elapsedSince(analysisStart);
            return new CImageSelectionResult(
                    selected.stream().map(CImageCandidate::candidateIndex).toList(),
                    images,
                    downloadFailures,
                    detailImageUrls.size(),
                    candidates.size(),
                    failedDownloads,
                    spool.scannedBytes(),
                    downloadDuration,
                    analysisDuration);
        }
    }

    private static CImageSelectionResult emptyResult(
            int discoveredCount,
            int failedDownloadCount,
            Duration downloadDuration,
            Duration analysisDuration
    ) {
        return new CImageSelectionResult(
                List.of(), List.of(), List.of(), discoveredCount, 0,
                failedDownloadCount, 0, downloadDuration, analysisDuration);
    }

    private void recordCFailure(
            URI productUrl,
            String stage,
            String reason,
            Duration duration
    ) {
        metrics.recordStage(productUrl, "c", stage, "error", duration);
        metrics.recordFailureReason(productUrl, "c", stage, reason);
    }

    private void logDirectGeminiRoute(
            int discoveredCount,
            int downloadedCount,
            int failedDownloadCount
    ) {
        log.info(
                "c_image_selection_completed discovered_count={} downloaded_count={} "
                        + "selector_count=0 db18_count=0 failed_download_count={} "
                        + "selected_candidate_indexes=[] route=gemini_direct "
                        + "selector_executed=false selector_input_count=0 "
                        + "db18_executed=false db18_input_count=0",
                discoveredCount,
                downloadedCount,
                failedDownloadCount);
    }

    private static boolean isExecutorFailure(CImageAnalysisException.Reason reason) {
        return switch (reason) {
            case BUSY, TIMEOUT, INTERRUPTED, SHUTDOWN -> true;
            default -> false;
        };
    }

    private static String failureReason(Throwable exception) {
        if (!(exception instanceof CImageAnalysisException analysisException)) {
            return "model_error";
        }
        return switch (analysisException.reason()) {
            case BUSY -> "busy";
            case TIMEOUT -> "timeout";
            case SCAN_LIMIT -> "scan_limit";
            case DECODE_ERROR -> "decode_error";
            case MODEL_ERROR, ANALYSIS_ERROR, INTERRUPTED, SHUTDOWN -> "model_error";
        };
    }

    private static ClothesExtractionFailureDto imageFailure() {
        return new ClothesExtractionFailureDto(
                DETAIL_IMAGE_FIELD, "이미지를 자동으로 가져오지 못했습니다.");
    }

    private static Duration elapsedSince(long startNanos) {
        return Duration.ofNanos(Math.max(0, System.nanoTime() - startNanos));
    }
}
