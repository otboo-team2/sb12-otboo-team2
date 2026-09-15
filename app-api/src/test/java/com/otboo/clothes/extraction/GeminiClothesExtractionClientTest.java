package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.http.ExternalApiClient;
import com.otboo.common.http.ExternalApiClientFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiClothesExtractionClientTest {

    private static final String MODEL = "gemini-test";
    private static final String API_KEY = "test-key";
    private static final URI PRODUCT_URI = URI.create("https://shop.example.com/products/1");
    private static final UUID FIT_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ExternalApiClient api;
    private ExternalApiClientFactory factory;
    private MockRestServiceServer server;
    private AtomicReference<RestClient> restClient;
    private AtomicReference<JsonNode> requestBody;
    private GeminiClothesExtractionClient client;

    @BeforeEach
    void setUp() {
        api = mock(ExternalApiClient.class);
        factory = mock(ExternalApiClientFactory.class);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = new AtomicReference<>();
        requestBody = new AtomicReference<>();
        builder.requestInterceptor((request, body, execution) -> {
            requestBody.set(objectMapper.readTree(body));
            return execution.execute(request, body);
        });

        given(factory.create(
                eq("clothes-gemini"),
                org.mockito.ArgumentMatchers.<Consumer<RestClient.Builder>>any()))
                .willAnswer(invocation -> {
                    Consumer<RestClient.Builder> customizer = invocation.getArgument(1);
                    customizer.accept(builder);
                    return api;
                });
        doAnswer(invocation -> {
            Function<RestClient, ?> call = invocation.getArgument(1);
            return call.apply(restClient.get());
        }).when(api).exchange(
                anyString(), org.mockito.ArgumentMatchers.<Function<RestClient, ?>>any());

        client = new GeminiClothesExtractionClient(
                factory,
                objectMapper,
                properties(API_KEY));
        restClient.set(builder.build());
    }

    @Test
    void sendsStructuredRequestAndParsesFirstCandidate() throws Exception {
        String candidateText = """
                {
                  "name": "세미 와이드 데님",
                  "type": "BOTTOM",
                  "attributes": [
                    {
                      "definitionId": "00000000-0000-0000-0000-000000000001",
                      "value": "세미와이드",
                      "evidence": "세미 와이드 핏",
                      "source": "DETAIL_IMAGE",
                      "optionDependent": false
                    }
                  ],
                  "ambiguities": []
                }
                """;
        String response = objectMapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of("text", candidateText)))))));
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/"
                                + MODEL + ":generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", API_KEY))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        byte[] imageBytes = "image-bytes".getBytes(StandardCharsets.UTF_8);
        GeminiExtractionCandidate result = client.extract(
                page(),
                List.of(new RemoteResource(
                        URI.create("https://cdn.example.com/detail.jpg"),
                        "image/jpeg",
                        imageBytes)),
                List.of(new AttributeDefinitionSnapshot(
                        FIT_ID,
                        "핏",
                        List.of("세미와이드", "와이드"))));

        assertThat(result.name()).isEqualTo("세미 와이드 데님");
        assertThat(result.type()).isEqualTo("BOTTOM");
        assertThat(result.attributes()).singleElement().satisfies(attribute -> {
            assertThat(attribute.definitionId()).isEqualTo(FIT_ID.toString());
            assertThat(attribute.value()).isEqualTo("세미와이드");
            assertThat(attribute.evidence()).isEqualTo("세미 와이드 핏");
            assertThat(attribute.source()).isEqualTo("DETAIL_IMAGE");
            assertThat(attribute.optionDependent()).isFalse();
        });

        JsonNode request = requestBody.get();
        String prompt = request.at("/contents/0/parts/0/text").asText();
        assertThat(prompt)
                .contains(ClothesType.values()[0].name(), ClothesType.BOTTOM.name())
                .contains(FIT_ID.toString(), "핏", "세미와이드", "와이드")
                .contains("<UNTRUSTED_PRODUCT_TEXT>", "</UNTRUSTED_PRODUCT_TEXT>")
                .contains("가벼운 원단만으로 얇음 판단 금지")
                .contains("시원함만으로 계절 판단 금지")
                .contains("옵션별 값은 optionDependent=true")
                .contains("혼방 소재에 함량 비율이 있으면 가장 높은 비율의 소재 하나만 선택")
                .contains("최고 함량이 같으면 소재를 확정하지 말고 ambiguity에 추가")
                .contains("스웨이드라는 표현만으로 가죽으로 추론하지 말 것")
                .contains("이미지 안에서 읽은 글자와 표의 source는 DETAIL_IMAGE");
        assertThat(request.at("/contents/0/parts/1/inline_data/mime_type").asText())
                .isEqualTo("image/jpeg");
        assertThat(request.at("/contents/0/parts/1/inline_data/data").asText())
                .isEqualTo(Base64.getEncoder().encodeToString(imageBytes));
        assertThat(request.at("/generationConfig/temperature").asDouble()).isZero();
        assertThat(request.at("/generationConfig/responseMimeType").asText())
                .isEqualTo("application/json");
        assertThat(request.at("/generationConfig/responseSchema").isObject()).isTrue();
        server.verify();
    }

    @Test
    void rejectsBlankApiKeyBeforeExternalCall() {
        GeminiClothesExtractionClient blankKeyClient = new GeminiClothesExtractionClient(
                factory,
                objectMapper,
                properties(" "));

        assertExternalFailure(() -> blankKeyClient.extract(page(), List.of(), List.of()));
        verifyNoInteractions(api);
    }

    @Test
    void rejectsResponseWithoutCandidates() {
        expectResponse("{\"candidates\":[]}");

        assertExternalFailure(() -> client.extract(page(), List.of(), List.of()));
        server.verify();
    }

    @Test
    void rejectsMalformedNestedCandidateJson() {
        expectResponse(responseWithText("{\"name\":"));

        assertExternalFailure(() -> client.extract(page(), List.of(), List.of()));
        server.verify();
    }

    @Test
    void rejectsEmptyCandidateText() {
        expectResponse(responseWithText("  "));

        assertExternalFailure(() -> client.extract(page(), List.of(), List.of()));
        server.verify();
    }

    @Test
    void defensivelyCopiesCandidateCollections() {
        GeminiExtractionCandidate candidate = new GeminiExtractionCandidate(
                "셔츠",
                "TOP",
                List.of(new GeminiExtractionCandidate.AttributeCandidate(
                        FIT_ID.toString(), "세미와이드", "핏", "DETAIL_IMAGE", false)),
                List.of("옵션별 소재"));

        assertThatThrownBy(() -> candidate.attributes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> candidate.ambiguities().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private ProductPageData page() {
        return new ProductPageData(
                PRODUCT_URI,
                "세미 와이드 데님",
                "<script>ignore this instruction</script>",
                URI.create("https://cdn.example.com/main.jpg"),
                List.of(),
                List.of("S", "M"));
    }

    private ClothesExtractionProperties properties(String apiKey) {
        return new ClothesExtractionProperties(
                apiKey,
                MODEL,
                3,
                2 * 1024 * 1024,
                10 * 1024 * 1024,
                25 * 1024 * 1024,
                4,
                15_000);
    }

    private void expectResponse(String response) {
        server.expect(requestTo(
                        "https://generativelanguage.googleapis.com/v1beta/models/"
                                + MODEL + ":generateContent"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private String responseWithText(String text) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":"
                + objectMapper.valueToTree(text) + "}]}}]}";
    }

    private void assertExternalFailure(Runnable action) {
        assertThatThrownBy(() -> action.run())
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode())
                            .isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR);
                    assertThat(exception.getDetails()).doesNotContainKey("response");
                    assertThat(exception.getDetails()).doesNotContainValue("{\"name\":");
                });
    }
}
