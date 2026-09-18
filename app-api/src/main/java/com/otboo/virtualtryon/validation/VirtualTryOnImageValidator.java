package com.otboo.virtualtryon.validation;

import com.otboo.common.exception.BusinessException;
import com.otboo.virtualtryon.exception.VirtualTryOnErrorCode;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class VirtualTryOnImageValidator {

    @Value("${otboo.virtual-try-on.image.min-width}")
    private int minWidth;

    @Value("${otboo.virtual-try-on.image.min-height}")
    private int minHeight;

    @Value("${otboo.virtual-try-on.image.min-aspect-ratio}")
    private double minAspectRatio;

    @Value("${otboo.virtual-try-on.image.max-aspect-ratio}")
    private double maxAspectRatio;

    public void validate(MultipartFile modelImage) {
        BufferedImage image;
        try (InputStream in = modelImage.getInputStream()) {
            image = ImageIO.read(in);
        } catch (IOException e) {
            throw new BusinessException(VirtualTryOnErrorCode.UNDECODABLE_IMAGE, e);
        }
        if (image == null) {
            throw new BusinessException(VirtualTryOnErrorCode.UNDECODABLE_IMAGE)
                .addDetail("reason", "이미지를 디코딩할 수 없음");
        }

        int width = image.getWidth();
        int height = image.getHeight();
        if (width < minWidth || height < minHeight) {
            throw new BusinessException(VirtualTryOnErrorCode.UNSUPPORTED_RESOLUTION)
                .addDetail("width", String.valueOf(width))
                .addDetail("height", String.valueOf(height))
                .addDetail("minWidth", String.valueOf(minWidth))
                .addDetail("minHeight", String.valueOf(minHeight));
        }

        double aspectRatio = (double) width / height;
        if (aspectRatio < minAspectRatio || aspectRatio > maxAspectRatio) {
            throw new BusinessException(VirtualTryOnErrorCode.UNSUPPORTED_ASPECT_RATIO)
                .addDetail("aspectRatio", String.valueOf(aspectRatio))
                .addDetail("allowedRange", minAspectRatio + "~" + maxAspectRatio);
        }
    }
}
