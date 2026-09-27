package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CImageCandidateSpoolTest {

    private static final URI SOURCE = URI.create("https://cdn.example.com/source.jpg");
    private static final URI FINAL = URI.create("https://cdn.example.com/final.jpg");
    private static final byte[] WEBP_1X1 = Base64.getDecoder().decode(
            "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEALmk0mk0iIiIiIgBoSygABc6zbAAA");

    @TempDir
    Path tempRoot;

    @Test
    void deletesOnlyItsRequestDirectoryOnSuccessAndFailure() throws Exception {
        Path unrelated = Files.writeString(tempRoot.resolve("keep.txt"), "keep");
        Path requestDirectory;
        try (CImageCandidateSpool spool = openSpool(1024, 100, 200)) {
            requestDirectory = spool.directory();
            spool.store(0, SOURCE, resource(FINAL, png(2, 3)));
        }

        assertThat(requestDirectory).doesNotExist();
        assertThat(unrelated).hasContent("keep");

        Path failedDirectory;
        try (CImageCandidateSpool spool = openSpool(1024, 100, 200)) {
            failedDirectory = spool.directory();
            assertThatThrownBy(() -> spool.store(0, SOURCE, resource(FINAL, new byte[]{1, 2, 3})))
                    .isInstanceOf(CImageAnalysisException.class)
                    .extracting("reason")
                    .isEqualTo(CImageAnalysisException.Reason.DECODE_ERROR);
        }
        assertThat(failedDirectory).doesNotExist();
        assertThat(unrelated).exists();
    }

    @Test
    void skipsDuplicateSourceAndFinalUrisWithoutWritingAnotherCandidate() throws Exception {
        URI secondSource = URI.create("https://cdn.example.com/second.jpg");
        try (CImageCandidateSpool spool = openSpool(1024, 100, 200)) {
            Optional<CImageCandidate> first = spool.store(0, SOURCE, resource(FINAL, png(2, 3)));
            Optional<CImageCandidate> duplicateFinal = spool.store(
                    1, secondSource, resource(FINAL, png(2, 3)));
            Optional<CImageCandidate> duplicateSource = spool.store(
                    2, SOURCE, resource(URI.create("https://cdn.example.com/other.jpg"), png(2, 3)));

            assertThat(first).isPresent();
            assertThat(duplicateFinal).isEmpty();
            assertThat(duplicateSource).isEmpty();
            assertThat(fileCount(spool.directory())).isEqualTo(1);
            assertThat(spool.scannedBytes()).isGreaterThan(spool.candidates().getFirst().bytes());
        }
    }

    @Test
    void duplicatePayloadsStillCountTowardRequestScanByteLimit() throws Exception {
        byte[] encoded = png(2, 3);
        try (CImageCandidateSpool spool = openSpool(encoded.length, 100, 200)) {
            spool.store(0, SOURCE, resource(FINAL, encoded));
            assertThatThrownBy(() -> spool.store(
                    1,
                    URI.create("https://cdn.example.com/alias.jpg"),
                    resource(FINAL, encoded)))
                    .isInstanceOfSatisfying(CImageAnalysisException.class, exception ->
                            assertThat(exception.reason())
                                    .isEqualTo(CImageAnalysisException.Reason.SCAN_LIMIT));
        }
    }

    @Test
    void rejectsScanByteLimitBeforeKeepingOversizedCandidate() throws Exception {
        byte[] encoded = png(4, 4);
        try (CImageCandidateSpool spool = openSpool(encoded.length - 1L, 100, 200)) {
            assertThatThrownBy(() -> spool.store(0, SOURCE, resource(FINAL, encoded)))
                    .isInstanceOfSatisfying(CImageAnalysisException.class, exception ->
                            assertThat(exception.reason())
                                    .isEqualTo(CImageAnalysisException.Reason.SCAN_LIMIT));
            assertThat(fileCount(spool.directory())).isZero();
        }
    }

    @Test
    void acceptsActualSelectedImagePayloadAtTwentyFiveMebibytesAndRejectsOneByteLess()
            throws Exception {
        long maxGeminiBytes = 25L * 1024 * 1024;
        byte[] encodedJpeg = jpeg(2, 3);
        List<CImageCandidate> selected = new java.util.ArrayList<>();
        RemoteResource primary = resource(
                URI.create("https://cdn.example.com/primary.jpg"), "image/jpeg", encodedJpeg);
        long remainingGeminiBytes = maxGeminiBytes - primary.body().length;
        int eachImageBytes = (int) (remainingGeminiBytes / 8);
        int remainingBytes = (int) (remainingGeminiBytes % 8);

        try (CImageCandidateSpool spool = openSpool(100L * 1024 * 1024, 200, 8)) {
            for (int index = 0; index < 8; index++) {
                URI uri = URI.create("https://cdn.example.com/large-image-" + index + ".jpg");
                int imageBytes = eachImageBytes + (index < remainingBytes ? 1 : 0);
                byte[] paddedJpeg = Arrays.copyOf(encodedJpeg, imageBytes);
                selected.add(spool.store(index, uri, resource(uri, "image/jpeg", paddedJpeg))
                        .orElseThrow());
            }

            assertThat(selected).hasSize(8);
            List<RemoteResource> exactLimitPayload = spool.toRemoteResources(
                    selected, remainingGeminiBytes);
            assertThat(primary.body().length
                    + exactLimitPayload.stream().mapToLong(image -> image.body().length).sum())
                    .isEqualTo(maxGeminiBytes);
            assertThatThrownBy(() -> spool.toRemoteResources(
                    selected, remainingGeminiBytes - 1))
                    .isInstanceOfSatisfying(CImageAnalysisException.class, exception ->
                            assertThat(exception.reason())
                                    .isEqualTo(CImageAnalysisException.Reason.SCAN_LIMIT));
        }
    }

    @Test
    void rejectsDecodedPixelLimitBeforeDecodingTheImage() throws Exception {
        try (CImageCandidateSpool spool = openSpool(1024, 399, 200)) {
            assertThatThrownBy(() -> spool.store(0, SOURCE, resource(FINAL, png(20, 20))))
                    .isInstanceOfSatisfying(CImageAnalysisException.class, exception ->
                            assertThat(exception.reason())
                                    .isEqualTo(CImageAnalysisException.Reason.SCAN_LIMIT));
        }
    }

    @Test
    void rejectsTheTwoHundredFirstCandidate() throws Exception {
        byte[] encoded = png(1, 1);
        try (CImageCandidateSpool spool = openSpool(1024 * 1024, 100, 200)) {
            for (int index = 0; index < 200; index++) {
                URI uri = URI.create("https://cdn.example.com/image-" + index + ".png");
                assertThat(spool.store(index, uri, resource(uri, encoded))).isPresent();
            }

            URI overflow = URI.create("https://cdn.example.com/image-200.png");
            assertThatThrownBy(() -> spool.store(200, overflow, resource(overflow, encoded)))
                    .isInstanceOfSatisfying(CImageAnalysisException.class, exception ->
                            assertThat(exception.reason())
                                    .isEqualTo(CImageAnalysisException.Reason.SCAN_LIMIT));
        }
    }

    @Test
    void readsJpegPngAndWebpDimensions() throws Exception {
        assertDecoded("png", png(3, 5), 3, 5);
        assertDecoded("jpeg", jpeg(7, 4), 7, 4);
        assertDecoded("webp", WEBP_1X1, 1, 1);
    }

    @Test
    void keepsCandidateFileInsideNormalizedRequestDirectory() throws Exception {
        try (CImageCandidateSpool spool = openSpool(1024, 100, 200)) {
            CImageCandidate candidate = spool.store(0, SOURCE, resource(FINAL, png(2, 3))).orElseThrow();

            Path root = tempRoot.toAbsolutePath().normalize();
            assertThat(spool.directory().toAbsolutePath().normalize().getParent()).isEqualTo(root);
            assertThat(candidate.temporaryFile().toAbsolutePath().normalize().getParent())
                    .isEqualTo(spool.directory().toAbsolutePath().normalize());
        }
    }

    private void assertDecoded(String contentType, byte[] bytes, int expectedWidth, int expectedHeight)
            throws Exception {
        try (CImageCandidateSpool spool = openSpool(1024, 100, 200)) {
            CImageCandidate candidate = spool.store(0, SOURCE, resource(FINAL, contentType, bytes))
                    .orElseThrow();
            assertThat(candidate.width()).isEqualTo(expectedWidth);
            assertThat(candidate.height()).isEqualTo(expectedHeight);
        }
    }

    private CImageCandidateSpool openSpool(long maxBytes, long maxPixels, int maxCandidates)
            throws Exception {
        return new CImageCandidateSpool(tempRoot, maxBytes, maxPixels, maxCandidates);
    }

    private static RemoteResource resource(URI finalUri, byte[] bytes) {
        return resource(finalUri, "image/png", bytes);
    }

    private static RemoteResource resource(URI finalUri, String contentType, byte[] bytes) {
        return new RemoteResource(finalUri, contentType, bytes);
    }

    private static byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        image.flush();
        return output.toByteArray();
    }

    private static byte[] jpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", output);
        image.flush();
        return output.toByteArray();
    }

    private static long fileCount(Path directory) throws Exception {
        try (var files = Files.list(directory)) {
            return files.count();
        }
    }
}
