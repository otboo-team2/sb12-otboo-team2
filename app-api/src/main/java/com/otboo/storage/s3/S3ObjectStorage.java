package com.otboo.storage.s3;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.storage.s3.RemoteImageDownloader.RemoteImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * 비공개 S3 객체 저장 기반. DB에는 {@link #store}가 돌려주는 객체 키만 저장하고,
 * 화면에 전달할 때 {@link #readUrl}로 짧게 유효한 URL을 따로 만든다.
 * 기존 {@code ImageStorage}에는 연결하지 않은 additive implementation이다.
 */
public class S3ObjectStorage {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/gif", ".gif",
            "image/webp", ".webp");

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final RemoteImageDownloader remoteImageDownloader;
    private final S3StorageProperties properties;

    public S3ObjectStorage(
            S3Client s3Client,
            S3Presigner presigner,
            RemoteImageDownloader remoteImageDownloader,
            S3StorageProperties properties
    ) {
        this.s3Client = s3Client;
        this.presigner = presigner;
        this.remoteImageDownloader = remoteImageDownloader;
        this.properties = properties;
    }

    /** 업로드 파일을 저장하고 만료되지 않는 객체 키만 반환한다. */
    public String store(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw invalidImage("empty file");
        }
        if (file.getSize() > properties.maxSize()) {
            throw invalidImage("file is too large");
        }
        String contentType = normalizeContentType(file.getContentType());
        String key = newKey(directory, contentType);
        PutObjectRequest request = putRequest(key, contentType);
        try (InputStream input = file.getInputStream()) {
            s3Client.putObject(request, RequestBody.fromInputStream(input, file.getSize()));
            return key;
        } catch (IOException | RuntimeException e) {
            throw storageFailure(e);
        }
    }

    /** 외부 HTTPS 이미지를 크기 제한 안에서 내려받아 저장하고 객체 키만 반환한다. */
    public String storeFromUrl(URI source, String directory) {
        RemoteImage image = remoteImageDownloader.download(source);
        String contentType = normalizeContentType(image.contentType());
        String key = newKey(directory, contentType);
        try {
            s3Client.putObject(putRequest(key, contentType), RequestBody.fromBytes(image.body()));
            return key;
        } catch (RuntimeException e) {
            throw storageFailure(e);
        }
    }

    /** 객체 키를 삭제한다. 존재하지 않는 키도 S3에서는 성공으로 처리된다. */
    public void delete(String key) {
        validateKey(key);
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.bucket())
                    .key(key)
                    .build());
        } catch (RuntimeException e) {
            throw storageFailure(e);
        }
    }

    /** 읽기 전용 Presigned URL을 발급한다. URL 자체는 DB에 저장하지 않는다. */
    public URI readUrl(String key) {
        validateKey(key);
        Duration ttl = properties.presignedUrlTtl();
        GetObjectRequest getObject = GetObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .build();
        return URI.create(presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .getObjectRequest(getObject)
                        .build())
                .url()
                .toString());
    }

    private PutObjectRequest putRequest(String key, String contentType) {
        return PutObjectRequest.builder()
                .bucket(properties.bucket())
                .key(key)
                .contentType(contentType)
                .build();
    }

    private String newKey(String directory, String contentType) {
        String prefix = normalizeDirectory(directory);
        return prefix + "/" + UUID.randomUUID() + EXTENSIONS.get(contentType);
    }

    private String normalizeDirectory(String directory) {
        if (directory == null || directory.isBlank()) {
            return "etc";
        }
        String normalized = Arrays.stream(directory.split("/"))
                .filter(segment -> !segment.isBlank())
                .peek(this::validateSegment)
                .collect(Collectors.joining("/"));
        return normalized.isBlank() ? "etc" : normalized;
    }

    private void validateSegment(String segment) {
        if (!segment.matches("[A-Za-z0-9_-]+")) {
            throw invalidImage("invalid object directory");
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains("\\")
                || Arrays.asList(key.split("/")).contains("..")) {
            throw invalidImage("invalid object key");
        }
    }

    private String normalizeContentType(String rawContentType) {
        String contentType = rawContentType == null
                ? ""
                : rawContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!EXTENSIONS.containsKey(contentType)) {
            throw invalidImage("unsupported content type");
        }
        return contentType;
    }

    private static BusinessException invalidImage(String reason) {
        return new BusinessException(CommonErrorCode.INVALID_IMAGE).addDetail("reason", reason);
    }

    private static BusinessException storageFailure(Exception cause) {
        if (cause instanceof BusinessException businessException) {
            return businessException;
        }
        return new BusinessException(CommonErrorCode.STORAGE_ERROR, cause);
    }
}
