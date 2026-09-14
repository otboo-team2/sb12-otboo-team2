package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.common.exception.BusinessException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.Base64;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.multipart.MultipartFile;

class RemoteImageDownloaderTest {

    private static final URI IMAGE_URI = URI.create("https://cdn.example.com/item.jpg");

    private SafeRemoteResourceClient remoteClient;
    private RemoteImageDownloader downloader;

    @BeforeAll
    static void loadImagePlugins() {
        ImageIO.scanForPlugins();
    }

    @BeforeEach
    void setUp() {
        remoteClient = mock(SafeRemoteResourceClient.class);
        downloader = new RemoteImageDownloader(remoteClient);
    }

    @ParameterizedTest
    @MethodSource("validImages")
    void acceptsSupportedImagesWhenContentTypeAndBytesMatch(
            String contentType,
            byte[] bytes,
            String extension
    ) throws Exception {
        given(remoteClient.getImage(IMAGE_URI))
                .willReturn(new RemoteResource(IMAGE_URI, contentType, bytes));

        MultipartFile result = downloader.download(IMAGE_URI.toString());

        assertThat(result.getName()).isEqualTo("image");
        assertThat(result.getOriginalFilename()).endsWith("." + extension);
        assertThat(result.getContentType()).isEqualTo(contentType);
        assertThat(result.getBytes()).isEqualTo(bytes);
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    void rejectsHtmlRenamedAsJpeg() {
        given(remoteClient.getImage(IMAGE_URI))
                .willReturn(new RemoteResource(
                        IMAGE_URI,
                        "image/jpeg",
                        "<html>not an image</html>".getBytes()));

        assertInvalidImage();
    }

    @Test
    void rejectsJpegBytesDeclaredAsPng() {
        given(remoteClient.getImage(IMAGE_URI))
                .willReturn(new RemoteResource(IMAGE_URI, "image/png", imageBytes("jpeg")));

        assertInvalidImage();
    }

    @Test
    void rejectsUndecodableBytesWithAnImagePrefix() {
        given(remoteClient.getImage(IMAGE_URI))
                .willReturn(new RemoteResource(
                        IMAGE_URI,
                        "image/jpeg",
                        new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01}));

        assertInvalidImage();
    }

    @Test
    void preservesSafeClientSizeErrorBeforeDecoding() {
        given(remoteClient.getImage(IMAGE_URI))
                .willThrow(new BusinessException(ClothesErrorCode.REMOTE_RESOURCE_TOO_LARGE));

        assertThatThrownBy(() -> downloader.download(IMAGE_URI.toString()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.REMOTE_RESOURCE_TOO_LARGE));
    }

    private void assertInvalidImage() {
        assertThatThrownBy(() -> downloader.download(IMAGE_URI.toString()))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.INVALID_REMOTE_IMAGE));
    }

    private static Stream<Arguments> validImages() {
        return Stream.of(
                Arguments.of("image/jpeg", imageBytes("jpeg"), "jpg"),
                Arguments.of("image/png", imageBytes("png"), "png"),
                Arguments.of("image/webp", imageBytes("webp"), "webp"));
    }

    private static byte[] imageBytes(String format) {
        try {
            BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, Color.RED.getRGB());
            image.setRGB(1, 1, Color.BLUE.getRGB());
            if ("webp".equals(format)) {
                return Base64.getDecoder().decode(
                        "UklGRhIAAABXRUJQVlA4TAYAAAAvAAAAAAfQ//73v/+BiOh/AAA=");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, output)) {
                throw new IllegalStateException("No ImageIO writer for " + format);
            }
            return output.toByteArray();
        } catch (Exception exception) {
            throw new AssertionError("Could not create " + format + " fixture", exception);
        }
    }
}
