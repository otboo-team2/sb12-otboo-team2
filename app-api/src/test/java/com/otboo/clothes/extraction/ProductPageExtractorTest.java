package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.clothes.exception.ClothesErrorCode;
import com.otboo.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProductPageExtractorTest {

    private static final URI PRODUCT_URI = URI.create("https://shop.example.com/products/1");

    private SafeRemoteResourceClient remoteClient;
    private ProductPageExtractor extractor;

    @BeforeEach
    void setUp() {
        remoteClient = mock(SafeRemoteResourceClient.class);
        ClothesExtractionProperties properties = new ClothesExtractionProperties(
                "", "gemini-test", 3, 2 * 1024 * 1024, 10 * 1024 * 1024,
                25 * 1024 * 1024, 4, 40);
        extractor = new ProductPageExtractor(remoteClient, new ObjectMapper(), properties);
    }

    @Test
    void prefersProductJsonLdOverOpenGraph() throws IOException {
        givenHtml("product-json-ld.html");

        ProductPageData result = extractor.extract(PRODUCT_URI);

        assertThat(result.name()).isEqualTo("세미 와이드 데님");
        assertThat(result.description()).isEqualTo("편안한 데일리 데님 팬츠");
        assertThat(result.imageUrl())
                .isEqualTo(URI.create("https://cdn.example.com/main.jpg"));
        assertThat(result.detailImageUrls()).containsExactly(
                URI.create("https://cdn.example.com/detail-1.jpg"),
                URI.create("https://cdn.example.com/detail-2.jpg"));
    }

    @Test
    void fallsBackToOpenGraph() throws IOException {
        givenHtml("product-og.html");

        ProductPageData result = extractor.extract(PRODUCT_URI);

        assertThat(result.name()).isEqualTo("후드 하프 코트");
        assertThat(result.description()).startsWith("가벼운 원단을 사용한 후드 코트입니다.");
        assertThat(result.imageUrl())
                .isEqualTo(URI.create("https://cdn.example.com/coat-main.jpg"));
    }

    @Test
    void excludesAdvertisementReviewAndTinyImages() throws IOException {
        givenHtml("product-detail-images.html");

        ProductPageData result = extractor.extract(PRODUCT_URI);

        assertThat(result.imageUrl())
                .isEqualTo(URI.create("https://cdn.example.com/product-main.jpg"));
        assertThat(result.detailImageUrls()).containsExactly(
                URI.create("https://cdn.example.com/detail-front.jpg"),
                URI.create("https://cdn.example.com/detail-back.jpg"),
                URI.create("https://cdn.example.com/srcset-first.jpg"));
        assertThat(result.detailImageUrls())
                .noneMatch(uri -> uri.toString().contains("logo"))
                .noneMatch(uri -> uri.toString().contains("review"))
                .noneMatch(uri -> uri.toString().startsWith("http://"));
    }

    @Test
    void capsDescriptionToConfiguredLength() throws IOException {
        givenHtml("product-og.html");

        ProductPageData result = extractor.extract(PRODUCT_URI);

        assertThat(result.description()).hasSize(40);
    }

    @Test
    void rejectsPageWithoutProductEvidence() throws IOException {
        givenHtml("not-a-product.html");

        assertThatThrownBy(() -> extractor.extract(PRODUCT_URI))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ClothesErrorCode.PRODUCT_DATA_NOT_FOUND));
    }

    private void givenHtml(String resourceName) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/clothes/extraction/" + resourceName)) {
            if (input == null) {
                throw new IOException("Missing test fixture: " + resourceName);
            }
            given(remoteClient.getHtml(eq(PRODUCT_URI))).willReturn(new RemoteResource(
                    PRODUCT_URI,
                    "text/html; charset=UTF-8",
                    input.readAllBytes()));
        }
    }
}
