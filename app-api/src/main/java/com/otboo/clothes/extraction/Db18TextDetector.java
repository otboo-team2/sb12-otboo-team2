package com.otboo.clothes.extraction;

import com.otboo.clothes.extraction.TextRegionDetector.TextBox;
import com.otboo.clothes.extraction.TextRegionDetector.TextDetectionResult;
import com.otboo.clothes.extraction.TextRegionDetector.TextPoint;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.dnn.TextDetectionModel_DB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class Db18TextDetector implements TextRegionDetector {

    private static final int INPUT_SIDE = 736;
    private static final double INPUT_SCALE = 1.0 / 255.0;
    private static final Scalar INPUT_MEAN = new Scalar(
            122.67891434, 116.66876762, 104.00698793);
    private static final String EXPECTED_SHA_256_PATTERN = "[0-9A-Fa-f]{64}";

    private final TextDetectionModel_DB detector;
    private final long maxDecodedPixels;

    public Db18TextDetector(CImageSelectionProperties properties) {
        Objects.requireNonNull(properties, "properties");
        try {
            OpenCvNativeLibraryLoader.load();
            verifyModel(properties.modelPath(), properties.modelSha256());
            if (properties.maxDecodedPixels() <= 0) {
                throw new CImageAnalysisException(CImageAnalysisException.Reason.MODEL_ERROR);
            }
            maxDecodedPixels = properties.maxDecodedPixels();
            detector = new TextDetectionModel_DB(properties.modelPath().toString());
            detector.setBinaryThreshold(0.3f);
            detector.setPolygonThreshold(0.5f);
            detector.setInputParams(
                    INPUT_SCALE,
                    new Size(INPUT_SIDE, INPUT_SIDE),
                    INPUT_MEAN,
                    true);
        } catch (CImageAnalysisException exception) {
            throw exception;
        } catch (IOException | RuntimeException | LinkageError exception) {
            throw new CImageAnalysisException(
                    CImageAnalysisException.Reason.MODEL_ERROR,
                    exception);
        }
    }

    @Override
    public synchronized TextDetectionResult detect(CImageCandidate candidate) {
        long startedAt = System.nanoTime();
        validateCandidate(candidate);
        if ((long) candidate.width() * candidate.height() > maxDecodedPixels) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.SCAN_LIMIT);
        }

        Mat bgr = null;
        Mat rgb = new Mat();
        MatOfFloat confidences = new MatOfFloat();
        List<MatOfPoint> detections = new ArrayList<>();
        try {
            bgr = Imgcodecs.imread(
                    candidate.temporaryFile().toString(), Imgcodecs.IMREAD_COLOR);
            if (bgr.empty()
                    || bgr.cols() != candidate.width()
                    || bgr.rows() != candidate.height()) {
                throw new CImageAnalysisException(CImageAnalysisException.Reason.DECODE_ERROR);
            }
            Imgproc.cvtColor(bgr, rgb, Imgproc.COLOR_BGR2RGB);
            bgr.release();
            bgr = null;

            detector.detect(rgb, detections, confidences);
            if (detections.isEmpty()) {
                return new TextDetectionResult(
                        List.of(), Duration.ofNanos(System.nanoTime() - startedAt));
            }
            float[] confidenceValues = confidences.empty()
                    ? new float[0]
                    : confidences.toArray();
            List<TextBox> boxes = new ArrayList<>(detections.size());
            for (int index = 0; index < detections.size(); index++) {
                Point[] points = detections.get(index).toArray();
                if (points.length < 3) {
                    continue;
                }
                List<TextPoint> coordinates = new ArrayList<>(points.length);
                for (Point point : points) {
                    coordinates.add(new TextPoint(point.x, point.y));
                }
                double confidence = index < confidenceValues.length
                        ? confidenceValues[index]
                        : 0.0;
                boxes.add(new TextBox(coordinates, confidence));
            }
            return new TextDetectionResult(
                    boxes, Duration.ofNanos(System.nanoTime() - startedAt));
        } catch (CImageAnalysisException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CImageAnalysisException(
                    CImageAnalysisException.Reason.ANALYSIS_ERROR,
                    exception);
        } finally {
            detections.forEach(Mat::release);
            confidences.release();
            if (bgr != null) {
                bgr.release();
            }
            rgb.release();
        }
    }

    private static void validateCandidate(CImageCandidate candidate) {
        if (candidate == null
                || candidate.temporaryFile() == null
                || candidate.width() <= 0
                || candidate.height() <= 0
                || !Files.isRegularFile(candidate.temporaryFile())) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.DECODE_ERROR);
        }
    }

    private static void verifyModel(Path modelPath, String expectedSha256) throws IOException {
        if (modelPath == null
                || expectedSha256 == null
                || !expectedSha256.matches(EXPECTED_SHA_256_PATTERN)
                || !Files.isRegularFile(modelPath)) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.MODEL_ERROR);
        }
        String actualSha256 = sha256(modelPath);
        if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.MODEL_ERROR);
        }
    }

    private static String sha256(Path modelPath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(modelPath)) {
                byte[] buffer = new byte[16 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().withUpperCase().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
