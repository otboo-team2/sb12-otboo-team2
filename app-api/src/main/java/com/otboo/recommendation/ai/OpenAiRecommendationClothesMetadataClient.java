package com.otboo.recommendation.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.http.ExternalApiClient;
import com.otboo.common.http.ExternalApiClientFactory;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class OpenAiRecommendationClothesMetadataClient {

    private static final String TOOL_NAME = "analyze_recommendation_clothes_metadata";
    private static final String INSTRUCTIONS = """
            추천 검색용 의상 metadata를 분석한다. 제공된 이름, 타입, 사용자 속성과 이미지에서
            직접 확인할 수 있는 정보만 사용하고, 근거가 부족한 값은 빈 목록 또는 null로 반환한다.
            inferredStyles와 occasions는 복수 선택할 수 있으며 허용값 밖의 값을 만들지 않는다.
            FORMAL 상황과 포멀 스타일은 서로 자동 변환하지 않는다.
            """;

    private final RecommendationAiProperties properties;
    private final ExternalApiClient api;
    private final ObjectMapper objectMapper;
    private final ObjectReader jsonReader;

    public OpenAiRecommendationClothesMetadataClient(
            ExternalApiClientFactory factory,
            RecommendationAiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.jsonReader = objectMapper.reader()
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.api = factory.create("llm", HttpClient.Redirect.NEVER, builder -> {
            builder.baseUrl(properties.baseUrl())
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
                builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey());
            }
        });
    }

    public RecommendationClothesMetadata analyze(ClothesDto clothes, String imageDataUri) {
        if (clothes == null || properties.apiKey() == null || properties.apiKey().isBlank()
                || properties.model() == null || properties.model().isBlank()) {
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR);
        }

        Map<String, Object> context = Map.of(
                "name", clothes.name(),
                "type", clothes.type().name(),
                "attributes", clothes.attributes().stream().map(attribute -> Map.of(
                        "name", attribute.definitionName(), "value", attribute.value())).toList());
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "input_text", "text", objectMapper.valueToTree(context).toString()));
        if (imageDataUri != null && !imageDataUri.isBlank()) {
            content.add(Map.of("type", "input_image", "image_url", imageDataUri));
        }

        Map<String, Object> request = Map.of(
                "model", properties.model(),
                "instructions", INSTRUCTIONS,
                "input", List.of(Map.of("role", "user", "content", content)),
                "tools", List.of(tool()),
                "tool_choice", Map.of("type", "function", "name", TOOL_NAME),
                "parallel_tool_calls", false,
                "store", false);
        return parse(api.post("/responses", request, String.class));
    }

    private Map<String, Object> tool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("inferredStyles", Map.of(
                "type", "array",
                "items", Map.of("type", "string", "enum", RecommendationClothesMetadata.ALLOWED_STYLES)));
        properties.put("formality", Map.of("anyOf", List.of(
                Map.of("type", "string", "enum", Arrays.stream(RecommendationFormality.values())
                        .map(Enum::name).toList()),
                Map.of("type", "null"))));
        properties.put("occasions", Map.of(
                "type", "array",
                "items", Map.of("type", "string", "enum", Arrays.stream(RecommendationOccasion.values())
                        .map(Enum::name).toList())));
        return Map.of(
                "type", "function",
                "name", TOOL_NAME,
                "description", "의상의 추천 검색용 metadata를 반환한다.",
                "strict", true,
                "parameters", Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "properties", properties,
                        "required", List.of("inferredStyles", "formality", "occasions")));
    }

    private RecommendationClothesMetadata parse(String response) {
        try {
            JsonNode root = jsonReader.readTree(response);
            if (root == null || !"completed".equals(root.path("status").asText())
                    || !root.path("output").isArray()) {
                throw externalFailure();
            }
            JsonNode arguments = null;
            for (JsonNode output : root.path("output")) {
                if (!"function_call".equals(output.path("type").asText())) {
                    continue;
                }
                if (arguments != null || !TOOL_NAME.equals(output.path("name").asText())
                        || !output.path("arguments").isTextual()) {
                    throw externalFailure();
                }
                arguments = jsonReader.readTree(output.path("arguments").textValue());
            }
            if (arguments == null || !arguments.isObject() || arguments.size() != 3
                    || !arguments.path("inferredStyles").isArray()
                    || !(arguments.path("formality").isNull() || arguments.path("formality").isTextual())
                    || !arguments.path("occasions").isArray()) {
                throw externalFailure();
            }
            List<String> styles = new ArrayList<>();
            for (JsonNode style : arguments.path("inferredStyles")) {
                if (!style.isTextual() || !RecommendationClothesMetadata.ALLOWED_STYLES.contains(style.asText())) {
                    throw externalFailure();
                }
                styles.add(style.asText());
            }
            RecommendationFormality formality = arguments.path("formality").isNull()
                    ? null : RecommendationFormality.valueOf(arguments.path("formality").asText());
            List<RecommendationOccasion> occasions = new ArrayList<>();
            for (JsonNode occasion : arguments.path("occasions")) {
                if (!occasion.isTextual()) {
                    throw externalFailure();
                }
                occasions.add(RecommendationOccasion.valueOf(occasion.asText()));
            }
            return new RecommendationClothesMetadata(styles, formality, occasions);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw externalFailure();
        }
    }

    private BusinessException externalFailure() {
        return new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR);
    }
}
