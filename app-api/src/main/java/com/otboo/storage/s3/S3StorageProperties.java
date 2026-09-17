package com.otboo.storage.s3;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 비공개 S3 객체 저장소 설정. 기본값은 비활성이라 기존 로컬 저장소 동작에 관여하지 않는다. */
@ConfigurationProperties(prefix = "otboo.storage.s3")
public record S3StorageProperties(
        boolean enabled,
        String bucket,
        String region,
        Duration presignedUrlTtl,
        long maxSize,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final long DEFAULT_MAX_SIZE = 10 * 1024 * 1024;

    public S3StorageProperties {
        region = isBlank(region) ? "ap-northeast-2" : region;
        presignedUrlTtl = presignedUrlTtl == null ? Duration.ofMinutes(15) : presignedUrlTtl;
        maxSize = maxSize <= 0 ? DEFAULT_MAX_SIZE : maxSize;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(30) : readTimeout;
    }

    public void requireConfigured() {
        if (isBlank(bucket)) {
            throw new IllegalStateException("S3 storage requires AWS_S3_BUCKET");
        }
        if (presignedUrlTtl.isNegative() || presignedUrlTtl.isZero()) {
            throw new IllegalStateException("S3 presigned URL TTL must be positive");
        }
        if (maxSize > Integer.MAX_VALUE) {
            throw new IllegalStateException("S3 max object size must fit in memory for remote downloads");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
