package com.otboo.clothes.extraction;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/** 서버가 검증한 원격 이미지 바이트를 기존 업로드 계약으로 감싸는 파일 어댑터다. */
public final class DownloadedMultipartFile implements MultipartFile {

    private final String contentType;
    private final byte[] bytes;
    private final String originalFilename;

    public DownloadedMultipartFile(String contentType, byte[] bytes) {
        this.contentType = contentType;
        this.bytes = bytes.clone();
        this.originalFilename = UUID.randomUUID() + "." + extension(contentType);
    }

    @Override
    public String getName() {
        return "image";
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return bytes.length == 0;
    }

    @Override
    public long getSize() {
        return bytes.length;
    }

    @Override
    public byte[] getBytes() {
        return bytes.clone();
    }

    @Override
    public InputStream getInputStream() {
        return new ByteArrayInputStream(bytes);
    }

    @Override
    public void transferTo(File destination) throws IOException {
        Path path = destination.toPath();
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, bytes);
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Unsupported image type");
        };
    }
}
