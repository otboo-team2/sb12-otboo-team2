package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.otboo.clothes.extraction.CImageFilter.CandidateMeasurement;
import com.otboo.clothes.extraction.CImageFilter.CFilterResult;
import com.otboo.clothes.extraction.TextRegionDetector.TextBox;
import com.otboo.clothes.extraction.TextRegionDetector.TextDetectionResult;
import com.otboo.clothes.extraction.TextRegionDetector.TextPoint;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CImageFilterTest {

    @Test
    void longImageUsesLocalCoverageWithoutCallingDetectorTwice() {
        FakeTextRegionDetector detector = new FakeTextRegionDetector(List.of(
                rectangle(40, 1_800, 140, 1_900, 0.5)));
        CImageFilter filter = new CImageFilter(detector);

        CFilterResult result = filter.filter(List.of(candidate(0, 200, 2_000)));

        assertThat(detector.callCount()).isEqualTo(1);
        assertThat(result.selected()).extracting(CImageCandidate::candidateIndex)
                .containsExactly(0);
        CandidateMeasurement measurement = result.measurements().getFirst();
        assertThat(measurement.longImage()).isTrue();
        assertThat(measurement.globalCoverageRatio()).isEqualTo(0.025);
        assertThat(measurement.adjustedScore()).isGreaterThanOrEqualTo(0.15);
    }

    @Test
    void removesCandidateWhenDetectorFindsNoTextBoxes() {
        CImageFilter filter = new CImageFilter(new FakeTextRegionDetector(List.of()));

        CFilterResult result = filter.filter(List.of(candidate(0, 1_000, 1_000)));

        assertThat(result.selected()).isEmpty();
        assertThat(result.measurements().getFirst().boxCount()).isZero();
        assertThat(result.measurements().getFirst().adjustedScore()).isZero();
    }

    @Test
    void retainsCandidateWhenRoundedScoreIsExactlyAtThreshold() {
        CImageFilter filter = new CImageFilter(new FakeTextRegionDetector(List.of(
                rectangle(0, 0, 100, 100, 0.72685185))));

        CFilterResult result = filter.filter(List.of(candidate(0, 1_000, 1_000)));

        assertThat(result.measurements().getFirst().adjustedScore()).isEqualTo(0.15);
        assertThat(result.selected()).extracting(CImageCandidate::candidateIndex)
                .containsExactly(0);
    }

    @Test
    void removesCandidateWhenScoreIsJustBelowThreshold() {
        CImageFilter filter = new CImageFilter(new FakeTextRegionDetector(List.of(
                rectangle(0, 0, 100, 100, 0.71685185))));

        CFilterResult result = filter.filter(List.of(candidate(0, 1_000, 1_000)));

        assertThat(result.measurements().getFirst().adjustedScore()).isLessThan(0.15);
        assertThat(result.selected()).isEmpty();
    }

    @Test
    void retainsCandidateWhenScoreIsJustAboveThreshold() {
        CImageFilter filter = new CImageFilter(new FakeTextRegionDetector(List.of(
                rectangle(0, 0, 100, 100, 0.73685185))));

        CFilterResult result = filter.filter(List.of(candidate(0, 1_000, 1_000)));

        assertThat(result.measurements().getFirst().adjustedScore()).isGreaterThan(0.15);
        assertThat(result.selected()).extracting(CImageCandidate::candidateIndex)
                .containsExactly(0);
    }

    @Test
    void usesGlobalCoverageForNormalAspectRatioImages() {
        CImageFilter filter = new CImageFilter(new FakeTextRegionDetector(List.of(
                rectangle(10, 10, 110, 110, 0.5))));

        CFilterResult result = filter.filter(List.of(candidate(0, 1_000, 1_000)));

        CandidateMeasurement measurement = result.measurements().getFirst();
        assertThat(measurement.longImage()).isFalse();
        assertThat(measurement.adjustedCoverageRatio())
                .isEqualTo(measurement.globalCoverageRatio());
    }

    @Test
    void invokesDetectorExactlyOnceForEveryCandidate() {
        FakeTextRegionDetector detector = new FakeTextRegionDetector(List.of());
        CImageFilter filter = new CImageFilter(detector);

        filter.filter(List.of(
                candidate(0, 1_000, 1_000),
                candidate(1, 1_000, 1_000),
                candidate(2, 200, 2_000)));

        assertThat(detector.callCount()).isEqualTo(3);
    }

    @Test
    void rejectsMoreThanTopEightBeforeRunningDetection() {
        FakeTextRegionDetector detector = new FakeTextRegionDetector(List.of());
        CImageFilter filter = new CImageFilter(detector);

        assertThatThrownBy(() -> filter.filter(java.util.stream.IntStream.range(0, 9)
                .mapToObj(index -> candidate(index, 1_000, 1_000))
                .toList()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(detector.callCount()).isZero();
    }

    private static CImageCandidate candidate(int index, int width, int height) {
        URI uri = URI.create("https://example.test/candidate-%02d.jpg".formatted(index));
        return new CImageCandidate(
                index,
                uri,
                uri,
                "image/jpeg",
                Path.of("candidate-%02d.jpg".formatted(index)),
                1,
                width,
                height,
                "sha256-%d".formatted(index));
    }

    private static TextBox rectangle(
            double left,
            double top,
            double right,
            double bottom,
            double confidence
    ) {
        return new TextBox(List.of(
                new TextPoint(left, top),
                new TextPoint(right, top),
                new TextPoint(right, bottom),
                new TextPoint(left, bottom)), confidence);
    }

    private static final class FakeTextRegionDetector implements TextRegionDetector {

        private final List<TextBox> boxes;
        private final AtomicInteger calls = new AtomicInteger();

        private FakeTextRegionDetector(List<TextBox> boxes) {
            this.boxes = boxes;
        }

        @Override
        public TextDetectionResult detect(CImageCandidate candidate) {
            calls.incrementAndGet();
            return new TextDetectionResult(boxes, Duration.ZERO);
        }

        private int callCount() {
            return calls.get();
        }
    }
}
