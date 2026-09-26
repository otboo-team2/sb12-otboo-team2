package com.otboo.clothes.extraction;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

public class CImageFeatureScorer {

    private static final int TARGET_WIDTH = 640;
    private static final int WINDOW_HEIGHT = 640;
    private static final int WINDOW_OVERLAP = 64;
    private static final int MAX_SCORING_WINDOWS = 3;
    private static final double COMPONENT_WEIGHT = 0.2625;
    private static final double ALIGNMENT_WEIGHT = 0.225;
    private static final double COVERAGE_WEIGHT = 0.15;
    private static final double LINE_WEIGHT = 0.1125;
    private static final double TABLE_WEIGHT = 0.25;

    private final long maxDecodedPixels;

    public CImageFeatureScorer(CImageSelectionProperties properties) {
        if (properties.maxDecodedPixels() <= 0) {
            throw new IllegalArgumentException("maxDecodedPixels must be positive");
        }
        maxDecodedPixels = properties.maxDecodedPixels();
        OpenCvRuntime.ensureLoaded();
    }

    public CImageFeatureScore score(CImageCandidate candidate) {
        if (candidate == null || candidate.temporaryFile() == null
                || candidate.width() <= 0 || candidate.height() <= 0) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.DECODE_ERROR);
        }
        if ((long) candidate.width() * candidate.height() > maxDecodedPixels) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.SCAN_LIMIT);
        }
        if (candidate.width() == 1 || candidate.height() == 1) {
            return zeroScore(candidate, 1);
        }

        byte[] encodedBytes;
        try {
            encodedBytes = Files.readAllBytes(candidate.temporaryFile());
        } catch (IOException exception) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.DECODE_ERROR);
        }
        if (encodedBytes.length == 0 || encodedBytes.length != candidate.bytes()) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.DECODE_ERROR);
        }

        MatOfByte encoded = new MatOfByte();
        Mat bgr = null;
        Mat rgb = new Mat();
        Mat resized = new Mat();
        try {
            encoded.fromArray(encodedBytes);
            bgr = Imgcodecs.imdecode(encoded, Imgcodecs.IMREAD_COLOR);
            if (bgr.empty()) {
                throw new CImageAnalysisException(CImageAnalysisException.Reason.DECODE_ERROR);
            }
            if (bgr.cols() != candidate.width() || bgr.rows() != candidate.height()) {
                throw new CImageAnalysisException(CImageAnalysisException.Reason.DECODE_ERROR);
            }
            if ((long) bgr.cols() * bgr.rows() > maxDecodedPixels) {
                throw new CImageAnalysisException(CImageAnalysisException.Reason.SCAN_LIMIT);
            }

            Imgproc.cvtColor(bgr, rgb, Imgproc.COLOR_BGR2RGB);
            bgr.release();
            bgr = null;
            resizeToTargetWidth(rgb, resized);
            return scoreResizedImage(candidate, resized);
        } catch (CImageAnalysisException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.ANALYSIS_ERROR);
        } finally {
            encoded.release();
            if (bgr != null) {
                bgr.release();
            }
            rgb.release();
            resized.release();
        }
    }

    private static CImageFeatureScore scoreResizedImage(CImageCandidate candidate, Mat resized) {
        List<Window> windows = selectWindows(buildWindows(resized.rows()));
        List<WindowScore> scores = new ArrayList<>(windows.size());
        for (Window window : windows) {
            Mat region = resized.submat(window.top(), window.bottom(), 0, resized.cols());
            try {
                scores.add(scoreWindow(region));
            } finally {
                region.release();
            }
        }

        WindowScore best = scores.get(0);
        for (int index = 1; index < scores.size(); index++) {
            if (scores.get(index).totalScore() > best.totalScore()) {
                best = scores.get(index);
            }
        }
        double topWindowMean = scores.stream()
                .map(WindowScore::totalScore)
                .sorted((left, right) -> Double.compare(right, left))
                .limit(3)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);
        double informationScore = best.totalScore() * 0.70 + topWindowMean * 0.30;

        return new CImageFeatureScore(
                candidate,
                best.componentScore(),
                best.alignmentScore(),
                best.coverageScore(),
                best.horizontalLineScore(),
                best.tableStructureScore(),
                informationScore,
                windows.size());
    }

    private static void resizeToTargetWidth(Mat source, Mat destination) {
        if (source.cols() <= TARGET_WIDTH) {
            source.copyTo(destination);
            return;
        }
        int targetHeight = Math.max(1, (int) Math.rint(
                source.rows() * (double) TARGET_WIDTH / source.cols()));
        byte[] resizedBytes = resizeLanczos3(source, TARGET_WIDTH, targetHeight);
        destination.create(targetHeight, TARGET_WIDTH, CvType.CV_8UC3);
        destination.put(0, 0, resizedBytes);
    }

    private static byte[] resizeLanczos3(Mat source, int targetWidth, int targetHeight) {
        int sourceWidth = source.cols();
        int sourceHeight = source.rows();
        int channels = source.channels();
        int rowByteCount = checkedByteCount(sourceWidth, channels);
        int intermediateByteCount = checkedByteCount(sourceHeight, targetWidth, channels);
        int outputByteCount = checkedByteCount(targetHeight, targetWidth, channels);
        AxisWeights horizontal = axisWeights(sourceWidth, targetWidth);
        AxisWeights vertical = axisWeights(sourceHeight, targetHeight);
        byte[] sourceRow = new byte[rowByteCount];
        byte[] intermediate = new byte[intermediateByteCount];
        byte[] output = new byte[outputByteCount];

        for (int y = 0; y < sourceHeight; y++) {
            source.get(y, 0, sourceRow);
            int outputRow = y * targetWidth * channels;
            for (int x = 0; x < targetWidth; x++) {
                int weightOffset = horizontal.offsets()[x];
                int first = horizontal.starts()[x];
                int count = horizontal.counts()[x];
                for (int channel = 0; channel < channels; channel++) {
                    double value = 0.0;
                    for (int tap = 0; tap < count; tap++) {
                        int sourceOffset = (first + tap) * channels + channel;
                        value += Byte.toUnsignedInt(sourceRow[sourceOffset])
                                * horizontal.coefficients()[weightOffset + tap];
                    }
                    intermediate[outputRow + x * channels + channel] = clipByte(value);
                }
            }
        }

        for (int y = 0; y < targetHeight; y++) {
            int weightOffset = vertical.offsets()[y];
            int first = vertical.starts()[y];
            int count = vertical.counts()[y];
            for (int x = 0; x < targetWidth; x++) {
                int outputOffset = (y * targetWidth + x) * channels;
                for (int channel = 0; channel < channels; channel++) {
                    double value = 0.0;
                    for (int tap = 0; tap < count; tap++) {
                        int sourceOffset = ((first + tap) * targetWidth + x) * channels + channel;
                        value += Byte.toUnsignedInt(intermediate[sourceOffset])
                                * vertical.coefficients()[weightOffset + tap];
                    }
                    output[outputOffset + channel] = clipByte(value);
                }
            }
        }
        return output;
    }

    private static int checkedByteCount(int... factors) {
        long byteCount = 1;
        for (int factor : factors) {
            byteCount *= factor;
            if (byteCount > Integer.MAX_VALUE) {
                throw new CImageAnalysisException(CImageAnalysisException.Reason.SCAN_LIMIT);
            }
        }
        return (int) byteCount;
    }

    private static AxisWeights axisWeights(int sourceSize, int targetSize) {
        double scale = sourceSize / (double) targetSize;
        double filterScale = Math.max(scale, 1.0);
        double support = 3.0 * filterScale;
        int[] starts = new int[targetSize];
        int[] counts = new int[targetSize];
        int[] offsets = new int[targetSize];
        List<Double> allCoefficients = new ArrayList<>();

        for (int output = 0; output < targetSize; output++) {
            double center = (output + 0.5) * scale;
            int first = Math.max(0, (int) (center - support + 0.5));
            int end = Math.min(sourceSize, (int) (center + support + 0.5));
            starts[output] = first;
            counts[output] = Math.max(1, end - first);
            offsets[output] = allCoefficients.size();
            double[] weights = new double[counts[output]];
            double sum = 0.0;
            for (int tap = 0; tap < weights.length; tap++) {
                double distance = (first + tap - center + 0.5) / filterScale;
                weights[tap] = lanczos3(distance);
                sum += weights[tap];
            }
            for (double weight : weights) {
                allCoefficients.add(weight / sum);
            }
        }

        double[] coefficients = new double[allCoefficients.size()];
        for (int index = 0; index < coefficients.length; index++) {
            coefficients[index] = allCoefficients.get(index);
        }
        return new AxisWeights(starts, counts, offsets, coefficients);
    }

    private static double lanczos3(double value) {
        double absolute = Math.abs(value);
        if (absolute < 1.0e-8) {
            return 1.0;
        }
        if (absolute >= 3.0) {
            return 0.0;
        }
        double piValue = Math.PI * value;
        double thirdPiValue = piValue / 3.0;
        return (Math.sin(piValue) / piValue) * (Math.sin(thirdPiValue) / thirdPiValue);
    }

    private static byte clipByte(double value) {
        return (byte) Math.max(0, Math.min(255, (int) Math.floor(value + 0.5)));
    }

    private static WindowScore scoreWindow(Mat rgb) {
        Mat gray = new Mat();
        Mat binary = new Mat();
        Mat cleaned = new Mat();
        Mat labels = new Mat();
        Mat stats = new Mat();
        Mat centroids = new Mat();
        Mat cleanKernel = Mat.ones(2, 2, CvType.CV_8U);
        try {
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY);
            Imgproc.adaptiveThreshold(gray, binary, 255,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 31, 15.0);
            Imgproc.morphologyEx(binary, cleaned, Imgproc.MORPH_OPEN, cleanKernel);
            int componentCount = Imgproc.connectedComponentsWithStats(
                    cleaned, labels, stats, centroids, 8, CvType.CV_32S);

            List<Component> components = new ArrayList<>();
            int width = gray.cols();
            int height = gray.rows();
            long imageArea = (long) width * height;
            for (int component = 1; component < componentCount; component++) {
                int[] stat = new int[5];
                double[] centroid = new double[2];
                stats.get(component, 0, stat);
                centroids.get(component, 0, centroid);
                int componentWidth = stat[Imgproc.CC_STAT_WIDTH];
                int componentHeight = stat[Imgproc.CC_STAT_HEIGHT];
                int area = stat[Imgproc.CC_STAT_AREA];
                double areaRatio = area / (double) imageArea;
                double aspectRatio = componentHeight == 0
                        ? 0.0
                        : componentWidth / (double) componentHeight;
                if (areaRatio < 0.00001 || areaRatio > 0.03
                        || componentHeight < 2 || componentHeight > height * 0.15
                        || aspectRatio < 0.1 || aspectRatio > 20.0) {
                    continue;
                }
                components.add(new Component(centroid[1], area));
            }

            double componentScore = Math.min(components.size() / 80.0, 1.0);
            double alignmentScore = rowAlignmentScore(components, height);
            long componentArea = components.stream().mapToLong(Component::area).sum();
            double coverageScore = Math.min(componentArea / (double) imageArea / 0.18, 1.0);
            double lineScore = horizontalLineScore(binary);
            double tableScore = tableStructureScore(gray);
            double totalScore = componentScore * COMPONENT_WEIGHT
                    + alignmentScore * ALIGNMENT_WEIGHT
                    + coverageScore * COVERAGE_WEIGHT
                    + lineScore * LINE_WEIGHT
                    + tableScore * TABLE_WEIGHT;
            return new WindowScore(componentScore, alignmentScore, coverageScore,
                    lineScore, tableScore, totalScore);
        } finally {
            gray.release();
            binary.release();
            cleaned.release();
            labels.release();
            stats.release();
            centroids.release();
            cleanKernel.release();
        }
    }

    private static double rowAlignmentScore(List<Component> components, int imageHeight) {
        if (components.isEmpty()) {
            return 0.0;
        }
        int binSize = Math.max(4, imageHeight / 40);
        Map<Integer, Integer> counts = new HashMap<>();
        for (Component component : components) {
            int row = (int) Math.floor(component.centerY() / binSize);
            counts.merge(row, 1, Integer::sum);
        }
        int aligned = counts.values().stream()
                .filter(count -> count >= 3)
                .mapToInt(Integer::intValue)
                .sum();
        return aligned / (double) components.size();
    }

    private static double horizontalLineScore(Mat binary) {
        int width = binary.cols();
        int height = binary.rows();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT,
                new Size(Math.max(8, width / 12), 1));
        Mat horizontal = new Mat();
        try {
            Imgproc.morphologyEx(binary, horizontal, Imgproc.MORPH_OPEN, kernel);
            double ratio = Core.countNonZero(horizontal) / (double) ((long) width * height);
            return Math.min(ratio / 0.08, 1.0);
        } finally {
            kernel.release();
            horizontal.release();
        }
    }

    private static double tableStructureScore(Mat gray) {
        int width = gray.cols();
        int height = gray.rows();
        Mat edges = new Mat();
        Mat horizontalKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT,
                new Size(Math.max(12, width / 10), 1));
        Mat verticalKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT,
                new Size(1, Math.max(12, height / 10)));
        Mat horizontal = new Mat();
        Mat vertical = new Mat();
        Mat intersections = new Mat();
        try {
            Imgproc.Canny(gray, edges, 10.0, 30.0);
            Imgproc.morphologyEx(edges, horizontal, Imgproc.MORPH_OPEN, horizontalKernel);
            Imgproc.morphologyEx(edges, vertical, Imgproc.MORPH_OPEN, verticalKernel);
            Core.bitwise_and(horizontal, vertical, intersections);
            double area = (double) width * height;
            double horizontalScore = Math.min(Core.countNonZero(horizontal) / area / 0.03, 1.0);
            double verticalScore = Math.min(Core.countNonZero(vertical) / area / 0.03, 1.0);
            double intersectionScore = Math.min(Core.countNonZero(intersections) / area / 0.0001, 1.0);
            return horizontalScore * 0.20 + verticalScore * 0.20 + intersectionScore * 0.60;
        } finally {
            edges.release();
            horizontalKernel.release();
            verticalKernel.release();
            horizontal.release();
            vertical.release();
            intersections.release();
        }
    }

    private static List<Window> buildWindows(int imageHeight) {
        if (imageHeight <= WINDOW_HEIGHT) {
            return List.of(new Window(0, imageHeight));
        }
        int step = WINDOW_HEIGHT - WINDOW_OVERLAP;
        List<Window> windows = new ArrayList<>();
        for (int start = 0; start <= imageHeight - WINDOW_HEIGHT; start += step) {
            windows.add(new Window(start, Math.min(imageHeight, start + WINDOW_HEIGHT)));
        }
        int lastStart = imageHeight - WINDOW_HEIGHT;
        if (windows.get(windows.size() - 1).top() != lastStart) {
            windows.add(new Window(lastStart, imageHeight));
        }
        return windows;
    }

    private static List<Window> selectWindows(List<Window> windows) {
        if (windows.size() <= MAX_SCORING_WINDOWS) {
            return windows;
        }
        List<Window> selected = new ArrayList<>(MAX_SCORING_WINDOWS);
        for (int position = 0; position < MAX_SCORING_WINDOWS; position++) {
            int index = (int) Math.rint(position * (windows.size() - 1.0)
                    / (MAX_SCORING_WINDOWS - 1.0));
            selected.add(windows.get(index));
        }
        return selected;
    }

    private static CImageFeatureScore zeroScore(CImageCandidate candidate, int windowCount) {
        return new CImageFeatureScore(candidate, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, windowCount);
    }

    private static final class OpenCvRuntime {

        private static final boolean LOADED = load();

        private static boolean load() {
            OpenCvNativeLibraryLoader.load();
            return true;
        }

        private static void ensureLoaded() {
            if (!LOADED) {
                throw new IllegalStateException("OpenCV native runtime did not initialize");
            }
        }
    }

    private record AxisWeights(int[] starts, int[] counts, int[] offsets, double[] coefficients) {}

    private record Component(double centerY, int area) {}

    private record Window(int top, int bottom) {}

    private record WindowScore(
            double componentScore,
            double alignmentScore,
            double coverageScore,
            double horizontalLineScore,
            double tableStructureScore,
            double totalScore
    ) {}
}
