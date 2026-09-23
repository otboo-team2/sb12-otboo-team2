package com.otboo.pinterest.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.otboo.pinterest.client.PinterestPinResponse.Image;
import com.otboo.pinterest.client.PinterestPinResponse.Images;
import com.otboo.pinterest.client.PinterestPinResponse.Media;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PinterestPinResponseTest {

    @Test
    @DisplayName("가장 큰 이미지부터 고른다")
    void picksLargestImage() {
        Images images = new Images(image("1200"), image("600"), image("400"), image("150"));

        assertThat(pin(new Media("image", images)).imageUrl()).contains("https://i.pinimg.com/1200.jpg");
    }

    @Test
    @DisplayName("영상 · 여러 장짜리 핀은 이미지 주소가 없다")
    void noImageForNonImagePins() {
        Images images = new Images(image("1200"), null, null, null);

        assertThat(pin(new Media("video", images)).imageUrl()).isEmpty();
        assertThat(pin(new Media("multiple_images", null)).imageUrl()).isEmpty();
        assertThat(pin(null).imageUrl()).isEmpty();
    }

    @Test
    @DisplayName("주소가 비어 있는 크기는 건너뛴다")
    void skipsBlankUrls() {
        Images images = new Images(new Image(" ", 1200, 1200), null, image("400"), null);

        assertThat(pin(new Media("image", images)).imageUrl()).contains("https://i.pinimg.com/400.jpg");
    }

    private static Image image(String size) {
        return new Image("https://i.pinimg.com/%s.jpg".formatted(size), 1, 1);
    }

    private static PinterestPinResponse pin(Media media) {
        return new PinterestPinResponse("1", "2", null, null, null, null, media);
    }
}
