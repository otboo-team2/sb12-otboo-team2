package com.otboo.recommendation.ai;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.http.ExternalApiClient;
import com.otboo.common.http.ExternalApiClientFactory;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** RecommendationClothesDocument content를 OpenAI embedding으로 변환한다. */
@Component
public class OpenAiEmbeddingClient {

    private static final String EMBEDDINGS_PATH = "/embeddings";
    private final RecommendationAiProperties properties;
    private final ExternalApiClient api;
    private final ObjectReader jsonReader;

    public OpenAiEmbeddingClient(
            ExternalApiClientFactory factory,
            RecommendationAiProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
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

    public List<Float> embed(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        if (isBlank(properties.apiKey()) || isBlank(properties.embeddingModel())
                || properties.embeddingDimensions() == null
                || properties.embeddingDimensions() <= 0) {
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR);
        }

        Map<String, Object> request = Map.of(
                "model", properties.embeddingModel(),
                "input", content,
                "dimensions", properties.embeddingDimensions(),
                "encoding_format", "float");
        String response = api.post(EMBEDDINGS_PATH, request, String.class);
        return parseEmbedding(response);
    }

    private List<Float> parseEmbedding(String response) {
        if (response == null || response.isBlank()) {
            throw externalFailure();
        }
        try {
            JsonNode root = jsonReader.readTree(response);
            JsonNode data = root == null ? null : root.path("data");
            if (!data.isArray() || data.isEmpty()) {
                throw externalFailure();
            }
            JsonNode embedding = data.get(0).path("embedding");
            if (!embedding.isArray() || embedding.isEmpty()
                    || embedding.size() != properties.embeddingDimensions()) {
                throw externalFailure();
            }

            List<Float> values = new ArrayList<>(embedding.size());
            for (JsonNode value : embedding) {
                if (!value.isNumber()) {
                    throw externalFailure();
                }
                float converted = value.floatValue();
                if (!Float.isFinite(converted)) {
                    throw externalFailure();
                }
                values.add(converted);
            }
            return List.copyOf(values);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw externalFailure();
        }
    }

    private BusinessException externalFailure() {
        return new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
