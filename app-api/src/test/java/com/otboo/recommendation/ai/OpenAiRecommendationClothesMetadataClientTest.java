package com.otboo.recommendation.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.clothes.dto.ClothesAttributeWithDefDto;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.http.ExternalApiClient;
import com.otboo.common.http.ExternalApiClientFactory;
import com.otboo.common.http.ExternalApiProperties;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiRecommendationClothesMetadataClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private OpenAiRecommendationClothesMetadataClient client;

    @BeforeEach
    void setUp() {
        var factory = new ExternalApiClientFactory(new ExternalApiProperties(null, Map.of())) {
            @Override
            public ExternalApiClient create(String name, HttpClient.Redirect redirect,
                    Consumer<RestClient.Builder> customizer) {
                return super.create(name, redirect, builder -> {
                    customizer.accept(builder);
                    server = MockRestServiceServer.bindTo(builder).build();
                });
            }
        };
        client = new OpenAiRecommendationClothesMetadataClient(factory,
                new RecommendationAiProperties("test-key", "test-model",
                        "https://api.openai.com/v1", "text-embedding-3-small", 1536), objectMapper);
    }

    @Test
    void textOnlyRequestUsesStrictAllowedValuesAndParsesNullableFormality() throws Exception {
        expect(arguments("{\"inferredStyles\":[\"포멀\",\"클래식\"],"
                + "\"formality\":null,\"occasions\":[\"WORK\",\"FORMAL\"]}"), request -> {
            var content = request.path("input").get(0).path("content");
            assertThat(content).hasSize(1);
            assertThat(content.get(0).path("type").asText()).isEqualTo("input_text");
            assertThat(request.path("tools").get(0).path("strict").asBoolean()).isTrue();
            var parameters = request.path("tools").get(0).path("parameters");
            assertThat(parameters.path("additionalProperties").asBoolean()).isFalse();
            assertThat(parameters.path("required")).hasSize(3);
            assertThat(parameters.path("properties").path("inferredStyles").path("items").path("enum"))
                    .extracting(node -> node.asText())
                    .containsExactlyInAnyOrderElementsOf(RecommendationClothesMetadata.ALLOWED_STYLES);
        });

        var result = client.analyze(clothes(null), null);

        assertThat(result.inferredStyles()).containsExactly("포멀", "클래식");
        assertThat(result.formality()).isNull();
        assertThat(result.occasions()).containsExactly(RecommendationOccasion.WORK, RecommendationOccasion.FORMAL);
        server.verify();
    }

    @Test
    void imageRequestAddsDataUriWithoutRemovingTextInput() throws Exception {
        expect(arguments("{\"inferredStyles\":[],\"formality\":\"HIGH\",\"occasions\":[]}"), request -> {
            var content = request.path("input").get(0).path("content");
            assertThat(content).hasSize(2);
            assertThat(content.get(0).path("type").asText()).isEqualTo("input_text");
            assertThat(content.get(1).path("type").asText()).isEqualTo("input_image");
            assertThat(content.get(1).path("image_url").asText()).isEqualTo("data:image/png;base64,AA==");
        });

        assertThat(client.analyze(clothes("/images/test.png"), "data:image/png;base64,AA==").formality())
                .isEqualTo(RecommendationFormality.HIGH);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"inferredStyles\":[\"고프코어\"],\"formality\":null,\"occasions\":[]}",
            "{\"inferredStyles\":[],\"formality\":null,\"occasions\":[\"INTERVIEW\"]}",
            "{\"inferredStyles\":[],\"formality\":\"VERY_HIGH\",\"occasions\":[]}"
    })
    void rejectsValuesOutsideBackendAllowList(String invalid) throws Exception {
        expect(arguments(invalid), request -> { });

        assertThatThrownBy(() -> client.analyze(clothes(null), null))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR));
        server.verify();
    }

    private ClothesDto clothes(String imageUrl) {
        return new ClothesDto(UUID.randomUUID(), UUID.randomUUID(), "옥스퍼드 셔츠", imageUrl,
                ClothesType.TOP, false, List.of(new ClothesAttributeWithDefDto(
                        UUID.randomUUID(), "보온성", List.of("얇음"), "얇음")));
    }

    private void expect(String response, java.util.function.Consumer<com.fasterxml.jackson.databind.JsonNode> assertion) {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(request -> assertion.accept(objectMapper.readTree(
                        ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString())))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private String arguments(String value) throws Exception {
        return objectMapper.writeValueAsString(Map.of("status", "completed", "output", List.of(Map.of(
                "type", "function_call", "name", "analyze_recommendation_clothes_metadata",
                "arguments", value))));
    }
}
