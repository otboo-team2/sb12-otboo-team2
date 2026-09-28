package com.otboo.clothes.extraction;

import java.util.Objects;

public record CImageFeatureScore(
        CImageCandidate candidate,
        double componentScore,
        double rowAlignmentScore,
        double coverageScore,
        double horizontalLineScore,
        double tableStructureScore,
        double informationDocumentScore,
        int scoringWindowCount
) {

    public CImageFeatureScore {
        Objects.requireNonNull(candidate, "candidate");
        if (candidate.width() <= 0 || candidate.height() <= 0) {
            throw new IllegalArgumentException("Candidate dimensions must be positive");
        }
        requireNormalized(componentScore, "componentScore");
        requireNormalized(rowAlignmentScore, "rowAlignmentScore");
        requireNormalized(coverageScore, "coverageScore");
        requireNormalized(horizontalLineScore, "horizontalLineScore");
        requireNormalized(tableStructureScore, "tableStructureScore");
        requireNormalized(informationDocumentScore, "informationDocumentScore");
        if (scoringWindowCount <= 0) {
            throw new IllegalArgumentException("scoringWindowCount must be positive");
        }
    }

    public int candidateIndex() {
        return candidate.candidateIndex();
    }

    public double textScore() {
        return (componentScore + rowAlignmentScore) / 2.0;
    }

    public double tableScore() {
        return (horizontalLineScore + tableStructureScore) / 2.0;
    }

    public double densityScore() {
        return coverageScore;
    }

    public double aspectRatio() {
        return Math.max(
                candidate.width() / (double) candidate.height(),
                candidate.height() / (double) candidate.width());
    }

    public double detailTextReserveScore() {
        double aspectSignal = Math.min(aspectRatio() / 4.0, 1.0);
        return Math.rint((textScore() * 0.70 + aspectSignal * 0.30) * 1_000_000.0)
                / 1_000_000.0;
    }

    private static void requireNormalized(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be finite and between 0 and 1");
        }
    }
}
