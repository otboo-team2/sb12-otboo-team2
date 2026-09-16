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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jsoup.nodes.Document;

class ProductPageExtractorTest {

    private static final URI PRODUCT_URI = URI.create("https://shop.example.com/products/1");

    private SafeRemoteResourceClient remoteClient;
    private ProductPageExtractor extractor;

    @BeforeEach
    void setUp() {
        remoteClient = mock(SafeRemoteResourceClient.class);
        extractor = createExtractor(40);
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
    void supplementsMusinsaPageWithEmbeddedProductData() throws IOException {
        URI musinsaProductUri = URI.create("https://www.musinsa.com/products/4189920");
        givenHtml(musinsaProductUri, "musinsa-next-data.html");

        ProductPageData result = createExtractor(200).extract(musinsaProductUri);

        assertThat(result.description())
                .contains("기본 상품 설명")
                .contains("면 95%, 폴리우레탄 5%")
                .contains("신축성이 좋은 슬림 핏 레깅스");
        assertThat(result.detailImageUrls()).containsExactly(
                URI.create("https://image.msscdn.net/detail/first.jpg"),
                URI.create("https://image.msscdn.net/detail/middle.jpg"),
                URI.create("https://image.msscdn.net/detail/material-table.jpg"));
    }

    @Test
    void supplementsTwentyNineCmPageWithFlightDetailImages() throws IOException {
        URI productUri = URI.create("https://www.29cm.co.kr/products/4163753");
        givenHtml(productUri, "twentyninecm-flight-data.html");

        ProductPageData result = createExtractor(200).extract(productUri);

        assertThat(result.detailImageUrls()).containsExactly(
                URI.create("https://cdn.example.com/detail/material.jpg"),
                URI.create("https://cdn.example.com/detail/model.jpg"));
    }

    @Test
    void runsOnlySupplementExtractorsThatSupportThePage() throws IOException {
        givenHtml("product-og.html");
        AtomicInteger supportedExtractorCalls = new AtomicInteger();
        AtomicInteger unsupportedExtractorCalls = new AtomicInteger();
        ProductPageSupplementExtractor supportedExtractor =
                new ProductPageSupplementExtractor() {
                    @Override
                    public boolean supports(URI productUrl) {
                        return productUrl.getHost().equals("shop.example.com");
                    }

                    @Override
                    public ProductPageSupplement extract(Document document, URI productUrl) {
                        supportedExtractorCalls.incrementAndGet();
                        return new ProductPageSupplement(
                                List.of("추가 상품 설명"),
                                List.of(URI.create("https://cdn.example.com/detail-table.jpg")));
                    }
                };
        ProductPageSupplementExtractor unsupportedExtractor =
                new ProductPageSupplementExtractor() {
                    @Override
                    public boolean supports(URI productUrl) {
                        return false;
                    }

                    @Override
                    public ProductPageSupplement extract(Document document, URI productUrl) {
                        unsupportedExtractorCalls.incrementAndGet();
                        return ProductPageSupplement.empty();
                    }
                };
        ProductPageExtractor pageExtractor = createExtractor(
                200,
                List.of(supportedExtractor, unsupportedExtractor));

        ProductPageData result = pageExtractor.extract(PRODUCT_URI);

        assertThat(supportedExtractorCalls).hasValue(1);
        assertThat(unsupportedExtractorCalls).hasValue(0);
        assertThat(result.description()).contains("추가 상품 설명");
        assertThat(result.detailImageUrls())
                .containsExactly(URI.create("https://cdn.example.com/detail-table.jpg"));
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
        givenHtml(PRODUCT_URI, resourceName);
    }

    private void givenHtml(URI productUri, String resourceName) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(
                "/clothes/extraction/" + resourceName)) {
            if (input == null) {
                throw new IOException("Missing test fixture: " + resourceName);
            }
            given(remoteClient.getHtml(eq(productUri))).willReturn(new RemoteResource(
                    productUri,
                    "text/html; charset=UTF-8",
                    input.readAllBytes()));
        }
    }

    private ProductPageExtractor createExtractor(int maxPageTextChars) {
        ObjectMapper objectMapper = new ObjectMapper();
        return createExtractor(
                maxPageTextChars,
                List.of(
                        new MusinsaEmbeddedDataExtractor(objectMapper),
                        new TwentyNineCmEmbeddedDataExtractor(objectMapper)));
    }

    private ProductPageExtractor createExtractor(
            int maxPageTextChars,
            List<ProductPageSupplementExtractor> supplementExtractors
    ) {
        ClothesExtractionProperties properties = new ClothesExtractionProperties(
                "", "gemini-test", 3, 2 * 1024 * 1024, 10 * 1024 * 1024,
                25 * 1024 * 1024, 4, maxPageTextChars);
        ObjectMapper objectMapper = new ObjectMapper();
        return new ProductPageExtractor(
                remoteClient,
                objectMapper,
                properties,
                supplementExtractors);
    }
}
