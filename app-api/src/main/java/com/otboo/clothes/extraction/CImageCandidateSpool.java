package com.otboo.clothes.extraction;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** 요청 하나에서 다운로드한 검증 이미지 파일의 수명주기를 소유한다. */
public class CImageCandidateSpool implements AutoCloseable {

    private final Path baseDirectory;
    private final Path directory;
    private final long maxScanBytes;
    private final long maxDecodedPixels;
    private final int maxCandidates;
    private final Set<URI> seenUris = new LinkedHashSet<>();
    private final List<CImageCandidate> candidates = new ArrayList<>();
    private long scannedBytes;
    private boolean closed;

    public CImageCandidateSpool(
            Path tempDirectory,
            long maxScanBytes,
            long maxDecodedPixels,
            int maxCandidates
    ) {
        this(tempDirectory, maxScanBytes, maxDecodedPixels, maxCandidates, Set.of());
    }

    public CImageCandidateSpool(
            Path tempDirectory,
            long maxScanBytes,
            long maxDecodedPixels,
            int maxCandidates,
            Set<URI> excludedUris
    ) {
        Objects.requireNonNull(tempDirectory, "tempDirectory");
        if (maxScanBytes <= 0 || maxDecodedPixels <= 0 || maxCandidates <= 0) {
            throw new IllegalArgumentException("C image spool limits must be positive");
        }
        baseDirectory = tempDirectory.toAbsolutePath().normalize();
        this.maxScanBytes = maxScanBytes;
        this.maxDecodedPixels = maxDecodedPixels;
        this.maxCandidates = maxCandidates;
        seenUris.addAll(Objects.requireNonNull(excludedUris, "excludedUris"));
        try {
            Files.createDirectories(baseDirectory);
            directory = Files.createTempDirectory(baseDirectory, "request-")
                    .toAbsolutePath().normalize();
            if (!baseDirectory.equals(directory.getParent())) {
                deleteRequestDirectory();
                throw new IllegalArgumentException("Request directory escaped configured temp directory");
            }
        } catch (IOException exception) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.ANALYSIS_ERROR, exception);
        }
    }

    public Path directory() {
        return directory;
    }

    public List<CImageCandidate> candidates() {
        ensureOpen();
        return List.copyOf(candidates);
    }

    public long scannedBytes() {
        ensureOpen();
        return scannedBytes;
    }

    public Optional<CImageCandidate> store(int candidateIndex, URI sourceUri, RemoteResource resource) {
        ensureOpen();
        Objects.requireNonNull(sourceUri, "sourceUri");
        Objects.requireNonNull(resource, "resource");
        if (candidateIndex < 0 || candidateIndex >= maxCandidates) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.SCAN_LIMIT);
        }
        URI finalUri = resource.finalUri();
        byte[] body = resource.body();
        if (body.length == 0 || (long) scannedBytes + body.length > maxScanBytes) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.SCAN_LIMIT);
        }
        scannedBytes += body.length;
        if (finalUri == null || seenUris.contains(sourceUri) || seenUris.contains(finalUri)) {
            return Optional.empty();
        }
        Path imageFile = directory.resolve(String.format(Locale.ROOT, "candidate-%03d.img", candidateIndex))
                .toAbsolutePath().normalize();
        if (!imageFile.startsWith(directory)) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.ANALYSIS_ERROR);
        }

        try {
            Files.write(imageFile, body, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            ImageDimensions dimensions = readDimensions(imageFile);
            CImageCandidate candidate = new CImageCandidate(
                    candidateIndex,
                    sourceUri,
                    finalUri,
                    resource.contentType(),
                    imageFile,
                    body.length,
                    dimensions.width(),
                    dimensions.height(),
                    sha256(body));
            seenUris.add(sourceUri);
            seenUris.add(finalUri);
            candidates.add(candidate);
            return Optional.of(candidate);
        } catch (CImageAnalysisException exception) {
            deleteFile(imageFile);
            throw exception;
        } catch (IOException | RuntimeException exception) {
            deleteFile(imageFile);
            throw new CImageAnalysisException(CImageAnalysisException.Reason.DECODE_ERROR, exception);
        }
    }

    public List<RemoteResource> toRemoteResources(List<CImageCandidate> selected, long maxBytes) {
        ensureOpen();
        Objects.requireNonNull(selected, "selected");
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must not be negative");
        }
        long selectedBytes = selected.stream().mapToLong(CImageCandidate::bytes).sum();
        if (selectedBytes > maxBytes) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.SCAN_LIMIT);
        }

        List<RemoteResource> resources = new ArrayList<>(selected.size());
        for (CImageCandidate candidate : selected) {
            if (!candidates.contains(candidate) || !directory.equals(candidate.temporaryFile()
                    .toAbsolutePath().normalize().getParent())) {
                throw new CImageAnalysisException(CImageAnalysisException.Reason.ANALYSIS_ERROR);
            }
            try {
                resources.add(new RemoteResource(
                        candidate.finalUri(),
                        candidate.contentType(),
                        Files.readAllBytes(candidate.temporaryFile())));
            } catch (IOException exception) {
                throw new CImageAnalysisException(CImageAnalysisException.Reason.ANALYSIS_ERROR, exception);
            }
        }
        return List.copyOf(resources);
    }

    @Override
    public void close() {
        if (!closed) {
            deleteRequestDirectory();
            closed = true;
        }
    }

    private ImageDimensions readDimensions(Path file) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(file.toFile())) {
            if (input == null) {
                throw new CImageAnalysisException(CImageAnalysisException.Reason.DECODE_ERROR);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new CImageAnalysisException(CImageAnalysisException.Reason.DECODE_ERROR);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) {
                    throw new CImageAnalysisException(CImageAnalysisException.Reason.DECODE_ERROR);
                }
                if ((long) width * height > maxDecodedPixels) {
                    throw new CImageAnalysisException(CImageAnalysisException.Reason.SCAN_LIMIT);
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new CImageAnalysisException(CImageAnalysisException.Reason.DECODE_ERROR);
                }
                try {
                    if (decoded.getWidth() != width || decoded.getHeight() != height) {
                        throw new CImageAnalysisException(CImageAnalysisException.Reason.DECODE_ERROR);
                    }
                    return new ImageDimensions(width, height);
                } finally {
                    decoded.flush();
                }
            } finally {
                reader.dispose();
            }
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("C image spool is already closed");
        }
    }

    private void deleteRequestDirectory() {
        if (!directory.startsWith(baseDirectory) || !baseDirectory.equals(directory.getParent())) {
            throw new IllegalStateException("Refusing to clean outside the owned request directory");
        }
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                        throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path currentDirectory, IOException exception)
                        throws IOException {
                    if (exception != null) {
                        throw exception;
                    }
                    Files.deleteIfExists(currentDirectory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.ANALYSIS_ERROR, exception);
        }
    }

    private static void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.ANALYSIS_ERROR, exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record ImageDimensions(int width, int height) {
    }
}
