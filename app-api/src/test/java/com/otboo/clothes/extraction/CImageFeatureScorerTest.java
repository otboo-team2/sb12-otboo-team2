package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

class CImageFeatureScorerTest {

    @TempDir
    Path temporaryDirectory;

    private final CImageFeatureScorer scorer = new CImageFeatureScorer(properties(200_000_000L));

    @BeforeAll
    static void loadOpenCvForSyntheticFixtures() {
        OpenCvNativeLibraryLoader.load();
    }

    @Test
    void scoresBlankWhiteImageAsHavingNoDocumentLikeFeatures() throws Exception {
        Mat image = whiteImage(640, 640);
        try {
            CImageFeatureScore score = scorer.score(writeCandidate(1, image));

            assertThat(score.componentScore()).isZero();
            assertThat(score.rowAlignmentScore()).isZero();
            assertThat(score.coverageScore()).isZero();
            assertThat(score.horizontalLineScore()).isZero();
            assertThat(score.tableStructureScore()).isZero();
            assertThat(score.informationDocumentScore()).isZero();
        } finally {
            image.release();
        }
    }

    @Test
    void findsAlignedSmallTextLikeComponentsWithoutReadingCharacters() throws Exception {
        Mat image = whiteImage(640, 640);
        try {
            for (int row = 0; row < 8; row++) {
                int top = 40 + row * 48;
                for (int column = 0; column < 10; column++) {
                    int left = 32 + column * 56;
                    Imgproc.rectangle(image,
                            new Point(left, top),
                            new Point(left + 7, top + 11),
                            new Scalar(0, 0, 0),
                            -1);
                }
            }

            CImageFeatureScore score = scorer.score(writeCandidate(2, image));

            assertThat(score.componentScore()).isGreaterThan(0.0);
            assertThat(score.rowAlignmentScore()).isGreaterThan(0.0);
            assertThat(score.textScore()).isGreaterThan(0.0);
            assertThat(score.informationDocumentScore()).isBetween(0.0, 1.0);
        } finally {
            image.release();
        }
    }

    @Test
    void givesGridLikeImageARecognizableTableSignal() throws Exception {
        Mat image = whiteImage(640, 640);
        try {
            for (int coordinate = 80; coordinate <= 560; coordinate += 80) {
                Imgproc.line(image, new Point(40, coordinate), new Point(600, coordinate),
                        new Scalar(0, 0, 0), 2);
                Imgproc.line(image, new Point(coordinate, 40), new Point(coordinate, 600),
                        new Scalar(0, 0, 0), 2);
            }

            CImageFeatureScore score = scorer.score(writeCandidate(3, image));

            assertThat(score.horizontalLineScore()).isGreaterThan(0.0);
            assertThat(score.tableStructureScore()).isGreaterThan(0.0);
            assertThat(score.tableScore()).isGreaterThan(0.0);
        } finally {
            image.release();
        }
    }

    @Test
    void samplesAtMostThreeWindowsFromAVeryTallImage() throws Exception {
        Mat image = whiteImage(320, 3_200);
        try {
            CImageFeatureScore score = scorer.score(writeCandidate(4, image));

            assertThat(score.scoringWindowCount()).isEqualTo(3);
        } finally {
            image.release();
        }
    }

    @Test
    void treatsAOnePixelHighImageAsZeroScoreInsteadOfCrashing() throws Exception {
        Mat image = whiteImage(320, 1);
        try {
            CImageFeatureScore score = scorer.score(writeCandidate(5, image));

            assertThat(score.informationDocumentScore()).isZero();
            assertThat(score.componentScore()).isZero();
        } finally {
            image.release();
        }
    }

    @Test
    void reportsAnExplicitDecodeFailureForInvalidImageBytes() throws Exception {
        Path invalidImage = temporaryDirectory.resolve("not-an-image.bin");
        Files.write(invalidImage, new byte[]{1, 2, 3, 4, 5});
        CImageCandidate candidate = candidate(6, invalidImage, 10, 10);

        assertThatThrownBy(() -> scorer.score(candidate))
                .isInstanceOfSatisfying(CImageAnalysisException.class,
                        exception -> assertThat(exception.reason())
                                .isEqualTo(CImageAnalysisException.Reason.DECODE_ERROR));
    }

    @Test
    void refusesAnImageAboveTheConfiguredDecodedPixelLimit() throws Exception {
        Mat image = whiteImage(20, 20);
        try {
            CImageFeatureScorer smallLimitScorer = new CImageFeatureScorer(properties(399));

            assertThatThrownBy(() -> smallLimitScorer.score(writeCandidate(7, image)))
                    .isInstanceOfSatisfying(CImageAnalysisException.class,
                            exception -> assertThat(exception.reason())
                                    .isEqualTo(CImageAnalysisException.Reason.SCAN_LIMIT));
        } finally {
            image.release();
        }
    }

    private CImageCandidate writeCandidate(int index, Mat image) throws Exception {
        Path path = temporaryDirectory.resolve("candidate-" + index + ".png");
        assertThat(Imgcodecs.imwrite(path.toString(), image)).isTrue();
        return candidate(index, path, image.cols(), image.rows());
    }

    private static CImageCandidate candidate(int index, Path path, int width, int height)
            throws Exception {
        long bytes = Files.size(path);
        return new CImageCandidate(
                index,
                URI.create("https://example.test/candidate-" + index + ".png"),
                URI.create("https://example.test/candidate-" + index + ".png"),
                "image/png",
                path,
                bytes,
                width,
                height,
                "test-sha-" + index);
    }

    private static Mat whiteImage(int width, int height) {
        return new Mat(height, width, CvType.CV_8UC3, new Scalar(255, 255, 255));
    }

    private static CImageSelectionProperties properties(long maxDecodedPixels) {
        return new CImageSelectionProperties(
                CImageSelectionProperties.Mode.B0,
                null,
                "",
                Path.of(System.getProperty("java.io.tmpdir")),
                8,
                100L * 1024 * 1024,
                maxDecodedPixels,
                1,
                Duration.ofMillis(250),
                Duration.ofSeconds(45));
    }
}
