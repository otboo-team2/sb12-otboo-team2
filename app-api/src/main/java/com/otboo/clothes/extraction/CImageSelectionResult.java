package com.otboo.clothes.extraction;

import com.otboo.clothes.dto.ClothesExtractionFailureDto;
import java.time.Duration;
import java.util.List;

public record CImageSelectionResult(
        List<Integer> selectedCandidateIndexes,
        List<RemoteResource> images,
        List<ClothesExtractionFailureDto> downloadFailures,
        int discoveredCandidateCount,
        int downloadedCandidateCount,
        int failedDownloadCount,
        long downloadedBytes,
        Duration downloadDuration,
        Duration analysisDuration
) {

    public CImageSelectionResult {
        selectedCandidateIndexes = List.copyOf(selectedCandidateIndexes);
        images = List.copyOf(images);
        downloadFailures = List.copyOf(downloadFailures);
    }
}
