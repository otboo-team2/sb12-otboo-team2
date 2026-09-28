package com.otboo.clothes.extraction;

import com.otboo.clothes.extraction.TextRegionDetector.TextBox;
import com.otboo.clothes.extraction.TextRegionDetector.TextDetectionResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CImageFilter {

    private static final int MAX_CANDIDATES = 8;
    private static final double SCORE_THRESHOLD = 0.15;
    private static final double LONG_IMAGE_ASPECT_RATIO = 4.0;
    private static final double LONG_IMAGE_WINDOW_SCALE = 2.0;
    private static final double LONG_IMAGE_WINDOW_OVERLAP = 0.25;

    private final TextRegionDetector detector;

    public CImageFilter(TextRegionDetector detector) {
        this.detector = Objects.requireNonNull(detector, "detector");
    }

    public CFilterResult filter(List<CImageCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        if (candidates.size() > MAX_CANDIDATES) {
            throw new IllegalArgumentException("DB18 accepts at most eight C selector candidates");
        }

        List<CImageCandidate> selected = new ArrayList<>();
        List<CandidateMeasurement> measurements = new ArrayList<>(candidates.size());
        for (CImageCandidate candidate : candidates) {
            validateCandidate(candidate);
            TextDetectionResult detection = Objects.requireNonNull(
                    detector.detect(candidate), "detector result");
            CandidateMeasurement measurement = measure(candidate, detection);
            boolean retained = measurement.adjustedScore() >= SCORE_THRESHOLD;
            measurements.add(measurement.withSelected(retained));
            if (retained) {
                selected.add(candidate);
            }
        }
        return new CFilterResult(selected, measurements);
    }

    private static CandidateMeasurement measure(
            CImageCandidate candidate,
            TextDetectionResult detection
    ) {
        List<TextBox> boxes = detection.boxes();
        int boxCount = boxes.size();
        double imageArea = (double) candidate.width() * candidate.height();
        double coveredArea = boxes.stream().mapToDouble(TextBox::area).sum();
        double globalCoverage = imageArea <= 0.0 ? 0.0 : coveredArea / imageArea;
        double meanConfidence = boxCount == 0
                ? 0.0
                : boxes.stream().mapToDouble(TextBox::confidence).average().orElse(0.0);
        boolean longImage = aspectRatio(candidate) >= LONG_IMAGE_ASPECT_RATIO;
        double localCoverage = longImage
                ? localCoverage(candidate, boxes)
                : globalCoverage;
        double adjustedCoverage = Math.max(globalCoverage, localCoverage);

        return new CandidateMeasurement(
                candidate.candidateIndex(),
                boxCount,
                globalCoverage,
                adjustedCoverage,
                clamp(meanConfidence),
                informationScore(boxCount, globalCoverage, meanConfidence),
                informationScore(boxCount, adjustedCoverage, meanConfidence),
                longImage,
                detection.inferenceDuration(),
                false);
    }

    private static double informationScore(int boxCount, double coverageRatio, double confidence) {
        if (boxCount == 0) {
            return 0.0;
        }
        double boxCountSignal = Math.min(boxCount / 24.0, 1.0);
        double coverageSignal = Math.min(coverageRatio / 0.18, 1.0);
        double score = boxCountSignal * 0.45
                + coverageSignal * 0.40
                + clamp(confidence) * 0.15;
        return Math.rint(score * 1_000_000.0) / 1_000_000.0;
    }

    private static double localCoverage(CImageCandidate candidate, List<TextBox> boxes) {
        if (boxes.isEmpty()) {
            return 0.0;
        }
        int width = candidate.width();
        int height = candidate.height();
        boolean portrait = height >= width;
        int shortSide = Math.min(width, height);
        int longSide = Math.max(width, height);
        int windowLength = (int) Math.min(longSide, (long) shortSide * 2L);
        if (windowLength >= longSide) {
            return coverageInWindow(boxes, portrait, 0, longSide,
                    (double) width * height);
        }

        int stride = Math.max(1, (int) (windowLength * (1.0 - LONG_IMAGE_WINDOW_OVERLAP)));
        int finalStart = longSide - windowLength;
        List<Integer> starts = new ArrayList<>();
        for (int start = 0; start <= finalStart; start += stride) {
            starts.add(start);
        }
        if (starts.getLast() != finalStart) {
            starts.add(finalStart);
        }

        double windowArea = (double) windowLength * shortSide;
        double bestCoverage = 0.0;
        for (int start : starts) {
            bestCoverage = Math.max(bestCoverage, coverageInWindow(
                    boxes, portrait, start, start + windowLength, windowArea));
        }
        return bestCoverage;
    }

    private static double coverageInWindow(
            List<TextBox> boxes,
            boolean portrait,
            int start,
            int end,
            double windowArea
    ) {
        double coveredArea = 0.0;
        for (TextBox box : boxes) {
            double center = portrait ? box.centerY() : box.centerX();
            if (center >= start && center <= end) {
                coveredArea += box.area();
            }
        }
        return windowArea <= 0.0 ? 0.0 : Math.min(coveredArea / windowArea, 1.0);
    }

    private static double aspectRatio(CImageCandidate candidate) {
        return Math.max(candidate.width() / (double) candidate.height(),
                candidate.height() / (double) candidate.width());
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(value, 1.0));
    }

    private static void validateCandidate(CImageCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (candidate.width() <= 0 || candidate.height() <= 0) {
            throw new IllegalArgumentException("Candidate dimensions must be positive");
        }
    }

    public record CFilterResult(
            List<CImageCandidate> selected,
            List<CandidateMeasurement> measurements
    ) {

        public CFilterResult {
            selected = List.copyOf(selected);
            measurements = List.copyOf(measurements);
        }
    }

    public record CandidateMeasurement(
            int candidateIndex,
            int boxCount,
            double globalCoverageRatio,
            double adjustedCoverageRatio,
            double meanConfidence,
            double rawScore,
            double adjustedScore,
            boolean longImage,
            Duration inferenceDuration,
            boolean selected
    ) {

        public CandidateMeasurement {
            Objects.requireNonNull(inferenceDuration, "inferenceDuration");
        }

        private CandidateMeasurement withSelected(boolean value) {
            return new CandidateMeasurement(candidateIndex, boxCount, globalCoverageRatio,
                    adjustedCoverageRatio, meanConfidence, rawScore, adjustedScore,
                    longImage, inferenceDuration, value);
        }
    }
}
