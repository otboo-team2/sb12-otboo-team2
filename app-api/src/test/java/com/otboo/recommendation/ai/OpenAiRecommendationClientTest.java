package com.otboo.recommendation.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.clothes.dto.ClothesAttributeWithDefDto;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.recommendation.RecommendationCandidates;
import com.otboo.weather.PrecipitationType;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.http.ExternalApiClient;
import com.otboo.common.http.ExternalApiClientFactory;
import com.otboo.common.http.ExternalApiProperties;
import java.io.IOException;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiRecommendationClientTest {

    private static final String TOOL_NAME = "extract_recommendation_condition";
    private static final String VALID_ARGUMENTS = """
            {"occasion":"DATE","styles":["캐주얼"],
             "categories":["TOP"],"keywords":[]}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockRestServiceServer server;
    private ExternalApiClientFactory factory;
    private OpenAiRecommendationClient client;

    @BeforeEach
    void setUp() {
        factory = new ExternalApiClientFactory(new ExternalApiProperties(null, Map.of())) {
            @Override
            public ExternalApiClient create(String name, HttpClient.Redirect redirect,
                    Consumer<RestClient.Builder> customizer) {
                assertThat(name).isEqualTo("llm");
                assertThat(redirect).isEqualTo(HttpClient.Redirect.NEVER);
                return super.create(name, redirect, builder -> {
                    customizer.accept(builder);
                    server = MockRestServiceServer.bindTo(builder).build();
                });
            }
        };
        client = newClient("test-key", "gpt-5.6-luna");
    }

    @Test
    void sendsForcedStrictToolAndParsesArgumentsAfterReasoning() throws Exception {
        String response = response(List.of(
                Map.of("type", "reasoning"),
                function(TOOL_NAME, VALID_ARGUMENTS)));
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(request -> {
                    var body = objectMapper.readTree(
                            ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                                    .getBodyAsString());
                    assertThat(body.path("model").asText()).isEqualTo("gpt-5.6-luna");
                    assertThat(body.path("input").get(0).path("content").asText())
                            .isEqualTo("데이트에 캐주얼한 상의 추천해줘");
                    assertThat(body.path("tools").get(0))
                            .isEqualTo(objectMapper.valueToTree(RecommendationConditionTool.definition()));
                    assertThat(body.path("tool_choice").path("name").asText()).isEqualTo(TOOL_NAME);
                    assertThat(body.path("tool_choice").path("type").asText()).isEqualTo("function");
                    assertThat(body.path("parallel_tool_calls").booleanValue()).isFalse();
                    assertThat(body.path("store").booleanValue()).isFalse();
                })
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        var condition = client.extractCondition("  데이트에 캐주얼한 상의 추천해줘  ");

        assertThat(condition.occasion()).isEqualTo(RecommendationOccasion.DATE);
        assertThat(condition.styles()).containsExactly("캐주얼");
        assertThat(condition.categories()).containsExactly(ClothesType.TOP);
        server.verify();
    }

    @Test
    void acceptsNullOccasionAndEmptyArrays() throws Exception {
        String arguments = """
                {"occasion":null,"styles":[],"categories":[],"keywords":[]}
                """;
        expectResponse(response(List.of(function(TOOL_NAME, arguments))));

        var condition = client.extractCondition("옷 추천해줘");

        assertThat(condition.occasion()).isNull();
        assertThat(condition.styles()).isEmpty();
        assertThat(condition.categories()).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json", "null", "{}", "{} {}",
            "{\"occasion\":null,\"occasion\":\"DATE\",\"styles\":[],\"categories\":[],\"keywords\":[]}",
            "{\"occasion\":\"DATE\",\"styles\":[],\"categories\":[],\"clothesId\":\"invented\"}",
            "{\"occasion\":\"UNKNOWN\",\"styles\":[],\"categories\":[],\"keywords\":[]}",
            "{\"occasion\":null,\"styles\":[],\"categories\":[\"UNKNOWN\"],\"keywords\":[]}",
            "{\"occasion\":null,\"styles\":[],\"categories\":[],\"keywords\":[],\"colors\":[\"검은색\"]}",
            "{\"occasion\":null,\"styles\":[],\"categories\":[],\"keywords\":[],\"fits\":[\"슬림\"]}",
            "{\"occasion\":null,\"styles\":null,\"categories\":[],\"keywords\":[]}",
            "{\"occasion\":null,\"styles\":[null],\"categories\":[],\"keywords\":[]}",
            "{\"occasion\":null,\"styles\":[12],\"categories\":[],\"keywords\":[]}"
    })
    void rejectsInvalidArguments(String arguments) throws Exception {
        expectResponse(response(List.of(function(TOOL_NAME, arguments))));

        assertExternalFailure(CommonErrorCode.EXTERNAL_API_ERROR);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "not-json", "null", "{}", "{\"status\":\"completed\",\"output\":[]}",
            "{\"status\":\"incomplete\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"refusal\"}]}]}"
    })
    void rejectsInvalidOrIncompleteResponses(String response) {
        expectResponse(response);

        assertExternalFailure(CommonErrorCode.EXTERNAL_API_ERROR);
        server.verify();
    }

    @Test
    void rejectsWrongFunction() throws Exception {
        expectResponse(response(List.of(function("select_clothes", VALID_ARGUMENTS))));
        assertExternalFailure(CommonErrorCode.EXTERNAL_API_ERROR);
        server.verify();
    }

    @Test
    void rejectsMultipleFunctionCalls() throws Exception {
        expectResponse(response(List.of(
                function(TOOL_NAME, VALID_ARGUMENTS), function(TOOL_NAME, VALID_ARGUMENTS))));
        assertExternalFailure(CommonErrorCode.EXTERNAL_API_ERROR);
        server.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsInvalidPromptWithoutCallingApi(String prompt) {
        assertThatThrownBy(() -> client.extractCondition(prompt))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE));
        server.verify();
    }

    @Test
    void rejectsPromptOver100CharactersWithoutCallingApi() {
        assertThatThrownBy(() -> client.extractCondition("가".repeat(101)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT_VALUE));
        server.verify();
    }

    @Test
    void rejectsMissingKeyWithoutCallingApi() {
        client = newClient("", "gpt-5.6-luna");
        assertExternalFailure(CommonErrorCode.EXTERNAL_API_ERROR);
        server.verify();
    }

    @Test
    void rejectsMissingModelWithoutCallingApi() {
        client = newClient("test-key", "");
        assertExternalFailure(CommonErrorCode.EXTERNAL_API_ERROR);
        server.verify();
    }

    @Test
    void translatesUnauthorizedResponseWithoutRetry() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        assertExternalFailure(CommonErrorCode.EXTERNAL_API_ERROR);
        server.verify();
    }

    @Test
    void translatesConnectionFailureWithoutRetry() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withException(new IOException("connection failure")));
        assertExternalFailure(CommonErrorCode.EXTERNAL_API_TIMEOUT);
        server.verify();
    }

    @Test
    void generationSendsOnlyVerifiedClothesAndParsesSelection() throws Exception {
        var clothes = verifiedClothes();
        String id = clothes.getFirst().id().toString();
        var condition = new RecommendationCondition(
                RecommendationOccasion.WORK, List.of(), List.of(), List.of());
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    var body = objectMapper.readTree(
                            ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                                    .getBodyAsString());
                    var input = objectMapper.readTree(body.path("input").get(0).path("content").asText());
                    assertThat(input.path("request").asText()).isEqualTo("데이트룩 추천해줘");
                    assertThat(input.path("temperature").asDouble()).isEqualTo(25.0);
                    assertThat(input.path("temperatureSensitivity").asInt()).isEqualTo(3);
                    assertThat(input.path("preferredStyles").get(0).asText()).isEqualTo("캐주얼");
                    assertThat(input.path("requestCondition").path("occasion").asText())
                            .isEqualTo("WORK");
                    assertThat(input.path("requestCondition").path("styles")).isEmpty();
                    assertThat(input.path("requestCondition").path("categories")).isEmpty();
                    assertThat(input.path("requestCondition").path("keywords")).isEmpty();
                    assertThat(input.path("clothes").get(0).path("clothesId").asText()).isEqualTo(id);
                    assertThat(input.path("clothes").get(0).path("name").asText()).isEqualTo("셔츠");
                    assertThat(input.path("clothes").get(0).path("type").asText()).isEqualTo("TOP");
                    assertThat(input.path("clothes").get(0).path("attributes").get(0).path("name").asText())
                            .isEqualTo("스타일");
                    assertThat(input.path("clothes").get(0).path("attributes").get(0).path("value").asText())
                            .isEqualTo("스트릿");
                    var metadata = input.path("clothes").get(0).path("recommendationMetadata");
                    assertThat(metadata.path("inferredStyles").get(0).asText()).isEqualTo("포멀");
                    assertThat(metadata.path("formality").asText()).isEqualTo("HIGH");
                    assertThat(metadata.path("occasions").get(0).asText()).isEqualTo("WORK");
                    assertThat(body.path("instructions").asText())
                            .contains("requestCondition은 이번 요청에서 확인된 조건")
                            .contains("preferredStyles는 저장된 사용자 선호")
                            .contains("명시적 요청 조건과 preferredStyles가 충돌하면 명시적 요청 조건을 우선한다")
                            .contains("후보는 제공된 name, type, attributes, recommendationMetadata만 근거로 비교한다")
                            .contains("명확히 충돌하면 우선 선택하지 않되")
                            .contains("metadata가 없다는 이유만으로 부적합하다고 단정하지 않는다")
                            .contains("제공되지 않은 소재·디자인·실루엣·상황 적합성을 만들어내지 않는다")
                            .contains("reason은 일반 사용자가 자연스럽게 이해할 수 있는 한국어 1~3문장")
                            .contains("enum 값이나")
                            .contains("내부 용어를 노출하지 않는다")
                            .contains("사용자의 실제 요청에 맞는 자연스러운 표현으로 설명한다");
                    assertThat(body.path("tools").get(0).path("parameters").path("properties")
                            .path("clothesIds").path("items").path("enum").get(0).asText()).isEqualTo(id);
                    assertThat(body.path("store").asBoolean()).isFalse();
                })
                .andRespond(withSuccess(response(List.of(function("select_recommendation_clothes",
                        "{\"clothesIds\":[\"" + id + "\"],\"reason\":\"데이트에 어울립니다\"}"))),
                        MediaType.APPLICATION_JSON));

        var result = client.generate("데이트룩 추천해줘", condition,
                generationCandidates(clothes), clothes, Map.of(clothes.getFirst().id(),
                        new RecommendationClothesMetadata(List.of("포멀"),
                                RecommendationFormality.HIGH, List.of(RecommendationOccasion.WORK))));

        assertThat(result.clothesIds()).containsExactly(clothes.getFirst().id());
        assertThat(result.reason()).isEqualTo("데이트에 어울립니다");
        server.verify();
    }

    @Test
    void generationKeepsExplicitStylesSeparateFromStoredPreferences() throws Exception {
        var clothes = verifiedClothes();
        String id = clothes.getFirst().id().toString();
        var condition = new RecommendationCondition(
                null, List.of("포멀"), List.of(), List.of());
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(request -> {
                    var body = objectMapper.readTree(
                            ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                                    .getBodyAsString());
                    var input = objectMapper.readTree(body.path("input").get(0).path("content").asText());
                    assertThat(input.path("requestCondition").path("styles").size()).isEqualTo(1);
                    assertThat(input.path("requestCondition").path("styles").get(0).asText())
                            .isEqualTo("포멀");
                    assertThat(input.path("preferredStyles").size()).isEqualTo(1);
                    assertThat(input.path("preferredStyles").get(0).asText()).isEqualTo("캐주얼");
                })
                .andRespond(withSuccess(response(List.of(function("select_recommendation_clothes",
                        "{\"clothesIds\":[\"" + id + "\"],\"reason\":\"추천 이유\"}"))),
                        MediaType.APPLICATION_JSON));

        client.generate("추천해줘", condition, generationCandidates(clothes), clothes);

        server.verify();
    }

    @Test
    void generationPreservesNullOccasionAndExplicitStyleAndCategory() throws Exception {
        var clothes = verifiedClothes();
        String id = clothes.getFirst().id().toString();
        var condition = new RecommendationCondition(
                null, List.of("캐주얼"), List.of(ClothesType.TOP), List.of());
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(request -> {
                    var body = objectMapper.readTree(
                            ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                                    .getBodyAsString());
                    var requestCondition = objectMapper.readTree(
                            body.path("input").get(0).path("content").asText()).path("requestCondition");
                    assertThat(requestCondition.has("occasion")).isTrue();
                    assertThat(requestCondition.path("occasion").isNull()).isTrue();
                    assertThat(requestCondition.path("styles").size()).isEqualTo(1);
                    assertThat(requestCondition.path("styles").get(0).asText()).isEqualTo("캐주얼");
                    assertThat(requestCondition.path("categories").size()).isEqualTo(1);
                    assertThat(requestCondition.path("categories").get(0).asText()).isEqualTo("TOP");
                    assertThat(requestCondition.path("keywords")).isEmpty();
                })
                .andRespond(withSuccess(response(List.of(function("select_recommendation_clothes",
                        "{\"clothesIds\":[\"" + id + "\"],\"reason\":\"추천 이유\"}"))),
                        MediaType.APPLICATION_JSON));

        client.generate("추천해줘", condition, generationCandidates(clothes), clothes);

        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}", "{\"clothesIds\":[],\"reason\":\"이유\"}",
            "{\"clothesIds\":null,\"reason\":\"이유\"}",
            "{\"clothesIds\":[null],\"reason\":\"이유\"}",
            "{\"clothesIds\":[\"not-a-uuid\"],\"reason\":\"이유\"}",
            "{\"clothesIds\":[\"00000000-0000-0000-0000-000000000001\"],\"reason\":\" \"}"})
    void generationRejectsMalformedArguments(String arguments) throws Exception {
        expectResponse(response(List.of(function("select_recommendation_clothes", arguments))));
        var clothes = verifiedClothes();

        assertThatThrownBy(() -> client.generate(
                "추천해줘", emptyCondition(), generationCandidates(clothes), clothes))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR));
        server.verify();
    }

    @Test
    void generationRejectsMultipleToolCalls() throws Exception {
        var clothes = verifiedClothes();
        String arguments = "{\"clothesIds\":[\"" + clothes.getFirst().id() + "\"],\"reason\":\"이유\"}";
        expectResponse(response(List.of(
                function("select_recommendation_clothes", arguments),
                function("select_recommendation_clothes", arguments))));

        assertThatThrownBy(() -> client.generate(
                "추천해줘", emptyCondition(), generationCandidates(clothes), clothes))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR));
        server.verify();
    }

    private List<ClothesDto> verifiedClothes() {
        return List.of(new ClothesDto(UUID.randomUUID(), UUID.randomUUID(), "셔츠", null,
                ClothesType.TOP, false, List.of(new ClothesAttributeWithDefDto(
                        UUID.randomUUID(), "스타일", List.of("스트릿"), "스트릿"))));
    }

    private RecommendationCondition emptyCondition() {
        return new RecommendationCondition(null, List.of(), List.of(), List.of());
    }

    private RecommendationCandidates generationCandidates(List<ClothesDto> clothes) {
        return new RecommendationCandidates(UUID.randomUUID(), clothes.getFirst().ownerId(),
                25.0, PrecipitationType.NONE, 3, Set.of("캐주얼"), clothes);
    }

    private OpenAiRecommendationClient newClient(String key, String model) {
        return new OpenAiRecommendationClient(factory,
                new RecommendationAiProperties(
                        key, model, "https://api.openai.com/v1",
                        "text-embedding-3-small", 1536), objectMapper);
    }

    private Map<String, Object> function(String name, String arguments) {
        return Map.of("type", "function_call", "name", name, "arguments", arguments);
    }

    private String response(List<Map<String, Object>> output) throws Exception {
        return objectMapper.writeValueAsString(Map.of("status", "completed", "output", output));
    }

    private void expectResponse(String body) {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void assertExternalFailure(CommonErrorCode errorCode) {
        assertThatThrownBy(() -> client.extractCondition("옷 추천해줘"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(errorCode));
    }
}
