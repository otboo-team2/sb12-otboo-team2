package com.otboo.clothes.extraction;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public interface TextRegionDetector {

    TextDetectionResult detect(CImageCandidate candidate);

    record TextDetectionResult(List<TextBox> boxes, Duration inferenceDuration) {

        public TextDetectionResult {
            boxes = List.copyOf(Objects.requireNonNull(boxes, "boxes"));
            Objects.requireNonNull(inferenceDuration, "inferenceDuration");
            if (inferenceDuration.isNegative()) {
                throw new IllegalArgumentException("inferenceDuration must not be negative");
            }
        }
    }

    record TextBox(List<TextPoint> points, double confidence) {

        public TextBox {
            points = List.copyOf(Objects.requireNonNull(points, "points"));
            if (points.size() < 3) {
                throw new IllegalArgumentException("A text box needs at least three points");
            }
            if (!Double.isFinite(confidence)) {
                throw new IllegalArgumentException("confidence must be finite");
            }
        }

        public double area() {
            double twiceArea = 0.0;
            for (int index = 0; index < points.size(); index++) {
                TextPoint current = points.get(index);
                TextPoint next = points.get((index + 1) % points.size());
                twiceArea += current.x() * next.y() - next.x() * current.y();
            }
            return Math.abs(twiceArea) / 2.0;
        }

        public double centerX() {
            return points.stream().mapToDouble(TextPoint::x).average().orElse(0.0);
        }

        public double centerY() {
            return points.stream().mapToDouble(TextPoint::y).average().orElse(0.0);
        }
    }

    record TextPoint(double x, double y) {

        public TextPoint {
            if (!Double.isFinite(x) || !Double.isFinite(y)) {
                throw new IllegalArgumentException("Text point coordinates must be finite");
            }
        }
    }
}
