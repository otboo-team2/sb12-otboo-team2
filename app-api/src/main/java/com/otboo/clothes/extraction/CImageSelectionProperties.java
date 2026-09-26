package com.otboo.clothes.extraction;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "otboo.clothes.extraction.c-selector")
public record CImageSelectionProperties(
        Mode mode,
        Path modelPath,
        String modelSha256,
        Path tempDirectory,
        int maxSelectedImages,
        long maxScanBytes,
        long maxDecodedPixels,
        int maxConcurrentAnalyses,
        Duration acquireTimeout,
        Duration analysisTimeout
) {

    public enum Mode {
        B0,
        C
    }
}
