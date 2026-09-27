package com.otboo.clothes.extraction;

import java.net.URI;
import java.nio.file.Path;

public record CImageCandidate(
        int candidateIndex,
        URI originalUri,
        URI finalUri,
        String contentType,
        Path temporaryFile,
        long bytes,
        int width,
        int height,
        String sha256
) {
}
