package com.otboo.common.storage;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 로컬 디스크 구현.
 *
 * <p><b>업로드된 파일명을 절대 그대로 쓰지 않는다.</b> {@code ../../etc/passwd} 같은 이름이 오면
 * 디렉터리 밖에 파일을 쓰게 된다. 이름은 서버가 UUID 로 새로 만들고, 확장자는
 * <b>선언된 content-type 에서</b> 정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalImageStorage implements ImageStorage {

    /** 허용 형식. 여기 없는 형식은 저장하지 않는다. SVG 는 스크립트를 품을 수 있어 제외한다. */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/gif", "gif",
            "image/webp", "webp");

    private final StorageProperties properties;

    @Override
    public String store(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(CommonErrorCode.INVALID_IMAGE)
                    .addDetail("reason", "빈 파일");
        }
        if (file.getSize() > properties.maxSize()) {
            throw new BusinessException(CommonErrorCode.INVALID_IMAGE)
                    .addDetail("reason", "크기 초과")
                    .addDetail("maxBytes", String.valueOf(properties.maxSize()));
        }
        String extension = ALLOWED_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new BusinessException(CommonErrorCode.INVALID_IMAGE)
                    .addDetail("reason", "지원하지 않는 형식")
                    .addDetail("contentType", String.valueOf(file.getContentType()));
        }

        String storedName = UUID.randomUUID() + "." + extension;
        Path targetDirectory = Path.of(properties.baseDir(), sanitize(directory));
        try {
            Files.createDirectories(targetDirectory);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, targetDirectory.resolve(storedName),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException(CommonErrorCode.STORAGE_ERROR, e);
        }
        return properties.baseUrl() + "/" + sanitize(directory) + "/" + storedName;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.startsWith(properties.baseUrl() + "/")) {
            return;         // 외부 URL 이거나 저장한 적 없는 값
        }
        String relative = url.substring(properties.baseUrl().length() + 1);
        Path target = Path.of(properties.baseDir()).resolve(relative).normalize();
        if (!target.startsWith(Path.of(properties.baseDir()).normalize())) {
            return;         // 경로를 벗어나는 값은 무시한다
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            // 지우기 실패로 사용자의 프로필 수정을 막을 이유가 없다. 파일만 남는다.
            log.warn("이미지 삭제 실패 url={}", url, e);
        }
    }

    /** 하위 경로에 구분자나 상위 이동이 섞이지 않게 한다. */
    private static String sanitize(String directory) {
        if (directory == null || directory.isBlank()) {
            return "etc";
        }
        return directory.replaceAll("[^A-Za-z0-9_-]", "");
    }
}
