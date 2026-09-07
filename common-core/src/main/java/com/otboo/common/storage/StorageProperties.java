package com.otboo.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseDir 파일을 쓸 로컬 디렉터리. 배포에서는 볼륨을 붙인다
 * @param baseUrl 저장된 파일을 읽을 때 붙는 URL 접두사. 정적 리소스 매핑과 같아야 한다
 * @param maxSize 허용 최대 크기(바이트). 프론트가 5MB 로 막고 있어 같은 값을 쓴다
 */
@ConfigurationProperties(prefix = "otboo.storage")
public record StorageProperties(
        String baseDir,
        String baseUrl,
        Long maxSize
) {

    public StorageProperties {
        if (baseDir == null || baseDir.isBlank()) {
            baseDir = "./data/images";
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "/images";
        }
        if (maxSize == null || maxSize <= 0) {
            maxSize = 5L * 1024 * 1024;
        }
    }
}
