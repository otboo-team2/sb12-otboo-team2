package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.common.http.ExternalApiClient;
import com.otboo.common.http.ExternalApiClientFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jsoup.nodes.Document;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AblyProductApiSupplementExtractorTest {

    private static final URI PRODUCT_URI = URI.create(
            "https://mobile.a-bly.com/goods/63697736");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ExternalApiClient api;
    private MockRestServiceServer server;
    private RestClient restClient;
    private AblyProductApiSupplementExtractor extractor;

    @BeforeEach
    void setUp() {
        api = mock(ExternalApiClient.class);
        ExternalApiClientFactory factory = mock(ExternalApiClientFactory.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();

        given(factory.create(
                eq("link-extract"),
                eq(HttpClient.Redirect.NEVER),
                org.mockito.ArgumentMatchers.<Consumer<RestClient.Builder>>any()))
                .willAnswer(invocation -> {
                    Consumer<RestClient.Builder> customizer = invocation.getArgument(2);
                    customizer.accept(builder);
                    return api;
                });
        doAnswer(invocation -> {
            Function<RestClient, ?> call = invocation.getArgument(1);
            return call.apply(restClient);
        }).when(api).exchange(
                anyString(),
                org.mockito.ArgumentMatchers.<Function<RestClient, ?>>any());

        extractor = new AblyProductApiSupplementExtractor(factory, objectMapper);
        restClient = builder.build();
    }

    @Test
    void collectsStaticCoverDescriptionImagesAndOptions() {
        server.expect(requestTo("https://api.a-bly.com/api/v2/anonymous/token/"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Device-Type", "PCWeb"))
                .andRespond(withSuccess("{\"token\":\"anonymous-token\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.a-bly.com/api/v3/goods/63697736/basic/"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Anonymous-Token", "anonymous-token"))
                .andRespond(withSuccess("""
                        {
                          "goods": {
                            "cover_images": [
                              "https://cdn.example.com/static-cover.webp",
                              "https://cdn.example.com/second-cover.webp"
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.a-bly.com/api/v3/goods/63697736/detail/"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Anonymous-Token", "anonymous-token"))
                .andRespond(withSuccess("""
                        {
                          "goods": {
                            "detail_html_parts": [
                              {
                                "html_part_type": "GOODS_TOP_NOTICE",
                                "contents": [
                                  "<p>광고 배너</p><img src='https://cdn.example.com/banner.png'>"
                                ]
                              },
                              {
                                "html_part_type": "DESCRIPTION",
                                "contents": [
                                  "<p>면 95%, 스판 5%</p><img src='https://cdn.example.com/detail-1.jpg'><img data-src='https://cdn.example.com/detail-2.jpg'>"
                                ]
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.a-bly.com/api/v2/goods/63697736/options/"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Anonymous-Token", "anonymous-token"))
                .andRespond(withSuccess("""
                        [
                          {
                            "name": "색상",
                            "option_components": [
                              {"name": "그레이"},
                              {"name": "블랙"}
                            ]
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        ProductPageSupplement result = extractor.extract(mock(Document.class), PRODUCT_URI);

        assertThat(result.descriptions()).containsExactly("면 95%, 스판 5%");
        assertThat(result.primaryImageUrl())
                .isEqualTo(URI.create("https://cdn.example.com/static-cover.webp"));
        assertThat(result.detailImageUrls()).containsExactly(
                URI.create("https://cdn.example.com/detail-1.jpg"),
                URI.create("https://cdn.example.com/detail-2.jpg"));
        assertThat(result.optionTexts()).containsExactly("색상: 그레이", "색상: 블랙");
        server.verify();
    }

    @Test
    void supportsOnlyAblyGoodsUrls() {
        assertThat(extractor.supports(PRODUCT_URI)).isTrue();
        assertThat(extractor.supports(URI.create("https://m.a-bly.com/goods/76233156")))
                .isTrue();
        assertThat(extractor.supports(URI.create("https://mobile.a-bly.com/events/1")))
                .isFalse();
        assertThat(extractor.supports(URI.create("https://api.a-bly.com/goods/63697736")))
                .isFalse();
        assertThat(extractor.supports(URI.create("https://example.com/goods/63697736")))
                .isFalse();
    }
}
