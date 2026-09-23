package com.otboo.clothes.extraction;

import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.common.exception.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 안전한 외부 이미지 바이트를 검증한 뒤 업로드 파일 계약으로 변환한다. */
@Component
public class RemoteImageDownloader {

    private static final String JPEG = "image/jpeg";
    private static final String PNG = "image/png";
    private static final String WEBP = "image/webp";

    static {
        ImageIO.scanForPlugins();
    }

    private final SafeRemoteResourceClient remoteResourceClient;

    public RemoteImageDownloader(SafeRemoteResourceClient remoteResourceClient) {
        this.remoteResourceClient = remoteResourceClient;
    }

    public MultipartFile download(String sourceImageUrl) {
        URI imageUri = parseUri(sourceImageUrl);
        RemoteResource resource = remoteResourceClient.getImage(imageUri);
        String contentType = normalizeContentType(resource == null ? null : resource.contentType());
        byte[] bytes = resource == null ? null : resource.body();

        validateContentTypeAndMagic(contentType, bytes);
        validateDecodableImage(bytes);
        return new DownloadedMultipartFile(contentType, bytes);
    }

    private URI parseUri(String sourceImageUrl) {
        if (sourceImageUrl == null || sourceImageUrl.isBlank()) {
            throw new BusinessException(ClothesErrorCode.INVALID_PRODUCT_URL);
        }
        try {
            return URI.create(sourceImageUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ClothesErrorCode.INVALID_PRODUCT_URL, exception);
        }
    }

    private void validateContentTypeAndMagic(String contentType, byte[] bytes) {
        if (contentType == null || bytes == null || bytes.length == 0) {
            throw invalidImage();
        }
        boolean matches = switch (contentType) {
            case JPEG -> hasJpegMagic(bytes);
            case PNG -> hasPngMagic(bytes);
            case WEBP -> hasWebpMagic(bytes);
            default -> false;
        };
        if (!matches) {
            throw invalidImage();
        }
    }

    private void validateDecodableImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw invalidImage();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ClothesErrorCode.INVALID_REMOTE_IMAGE, exception);
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        return contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasJpegMagic(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff;
    }

    private boolean hasPngMagic(byte[] bytes) {
        byte[] signature = {
                (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a};
        if (bytes.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (bytes[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean hasWebpMagic(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P';
    }

    private BusinessException invalidImage() {
        return new BusinessException(ClothesErrorCode.INVALID_REMOTE_IMAGE);
    }
}
