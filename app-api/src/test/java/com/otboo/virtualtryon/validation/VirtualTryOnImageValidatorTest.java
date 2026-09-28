package com.otboo.virtualtryon.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.otboo.common.exception.BusinessException;
import com.otboo.virtualtryon.exception.VirtualTryOnErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

class VirtualTryOnImageValidatorTest {

    private final VirtualTryOnImageValidator validator = new VirtualTryOnImageValidator();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(validator, "minWidth", 200);
        ReflectionTestUtils.setField(validator, "minHeight", 200);
        ReflectionTestUtils.setField(validator, "minAspectRatio", 0.5);
        ReflectionTestUtils.setField(validator, "maxAspectRatio", 2.0);
    }

    private MockMultipartFile pngOf(int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return new MockMultipartFile("modelImage", "model.png", "image/png", out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    @DisplayName("해상도와 비율이 모두 허용 범위면 통과한다")
    void passesWhenValid() {
        MockMultipartFile file = pngOf(300, 300);

        assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이미지를 디코딩할 수 없으면(IOException) UNDECODABLE_IMAGE를 던진다")
    void throwsWhenDecodingFails() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("깨진 스트림"));

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(VirtualTryOnErrorCode.UNDECODABLE_IMAGE);
    }

    @Test
    @DisplayName("ImageIO가 포맷을 못 읽어 null을 반환하면 UNDECODABLE_IMAGE를 던진다")
    void throwsWhenImageIoReturnsNull() {
        MockMultipartFile file = new MockMultipartFile("modelImage", "not-an-image.txt",
            "text/plain", "이건 이미지가 아닙니다".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(VirtualTryOnErrorCode.UNDECODABLE_IMAGE);
    }

    @Test
    @DisplayName("너비가 최소값보다 작으면 UNSUPPORTED_RESOLUTION을 던진다")
    void throwsWhenWidthTooSmall() {
        MockMultipartFile file = pngOf(199, 300);

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(VirtualTryOnErrorCode.UNSUPPORTED_RESOLUTION);
    }

    @Test
    @DisplayName("높이가 최소값보다 작으면 UNSUPPORTED_RESOLUTION을 던진다")
    void throwsWhenHeightTooSmall() {
        MockMultipartFile file = pngOf(300, 199);

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(VirtualTryOnErrorCode.UNSUPPORTED_RESOLUTION);
    }

    @Test
    @DisplayName("비율이 최소값보다 작으면(세로로 너무 김) UNSUPPORTED_ASPECT_RATIO를 던진다")
    void throwsWhenAspectRatioTooNarrow() {
        MockMultipartFile file = pngOf(200, 500);

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(VirtualTryOnErrorCode.UNSUPPORTED_ASPECT_RATIO);
    }

    @Test
    @DisplayName("비율이 최대값보다 크면(가로로 너무 김) UNSUPPORTED_ASPECT_RATIO를 던진다")
    void throwsWhenAspectRatioTooWide() {
        MockMultipartFile file = pngOf(500, 200);

        assertThatThrownBy(() -> validator.validate(file))
            .isInstanceOf(BusinessException.class)
            .extracting(e -> ((BusinessException) e).getErrorCode())
            .isEqualTo(VirtualTryOnErrorCode.UNSUPPORTED_ASPECT_RATIO);
    }

    @Test
    @DisplayName("비율이 정확히 경계값이면 통과한다")
    void passesAtExactBoundaryRatios() {
        MockMultipartFile minRatio = pngOf(200, 400);
        MockMultipartFile maxRatio = pngOf(400, 200);

        assertThatCode(() -> validator.validate(minRatio)).doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(maxRatio)).doesNotThrowAnyException();
    }
}
