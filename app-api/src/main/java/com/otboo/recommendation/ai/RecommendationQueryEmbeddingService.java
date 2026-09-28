package com.otboo.recommendation.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 자연어 요청과 기존 추천 조건을 의상 검색용 embedding 입력으로 변환한다. */
@Service
@Slf4j
public class RecommendationQueryEmbeddingService {

    private static final String CACHE_VERSION = "v1";
    private static final String CACHE_PREFIX = "recommendation:embedding:";

    private final OpenAiEmbeddingClient embeddingClient;
    private final RecommendationAiProperties properties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final Duration cacheTtl;

    @Autowired
    public RecommendationQueryEmbeddingService(OpenAiEmbeddingClient embeddingClient,
            RecommendationAiProperties properties, ObjectMapper objectMapper,
            StringRedisTemplate redis,
            @Value("${otboo.recommendation.ai.embedding-cache-ttl:30m}") Duration cacheTtl) {
        this.embeddingClient = embeddingClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.cacheTtl = cacheTtl;
    }

    RecommendationQueryEmbeddingService(OpenAiEmbeddingClient embeddingClient) {
        this(embeddingClient,
                new RecommendationAiProperties("", "", "", "embedding", 1536),
                new ObjectMapper(), null, Duration.ZERO);
    }

    public List<Float> embed(String prompt, RecommendationCondition condition, Set<String> preferredStyles) {
        String query = queryText(prompt, condition, preferredStyles);
        String key = cacheKey(query);
        if (redis != null) try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                List<Float> vector = objectMapper.readValue(cached,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Float.class));
                if (isValid(vector)) {
                    log.debug("recommendation_embedding_cache result=hit");
                    return vector;
                }
            }
            log.debug("recommendation_embedding_cache result=miss");
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("recommendation_embedding_cache result=error operation=get");
        }
        List<Float> vector = embeddingClient.embed(query);
        if (!isValid(vector)) {
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR);
        }
        if (redis != null) try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(vector), cacheTtl);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("recommendation_embedding_cache result=error operation=put");
        }
        return vector;
    }

    private String cacheKey(String query) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(query.getBytes(StandardCharsets.UTF_8));
            return CACHE_PREFIX + properties.embeddingModel() + ":"
                    + properties.embeddingDimensions() + ":" + CACHE_VERSION + ":"
                    + HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private boolean isValid(List<Float> vector) {
        return vector != null && vector.size() == properties.embeddingDimensions()
                && vector.stream().allMatch(value -> value != null && Float.isFinite(value));
    }

    static String queryText(String prompt, RecommendationCondition condition, Set<String> preferredStyles) {
        if (prompt == null || prompt.isBlank()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
        StringBuilder text = new StringBuilder("요청:").append(prompt.trim());
        if (condition != null) {
            if (condition.occasion() != null) {
                text.append(" 상황:").append(condition.occasion().name());
            }
            append(text, "스타일", condition.styles());
            append(text, "타입", condition.categories().stream().map(ClothesType::name).toList());
            append(text, "키워드", condition.keywords());
        }
        append(text, "선호 스타일", preferredStyles == null ? List.of() : preferredStyles.stream().toList());
        return text.toString();
    }

    private static void append(StringBuilder text, String label, List<String> values) {
        String joined = values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
        if (!joined.isEmpty()) {
            text.append(' ').append(label).append(':').append(joined);
        }
    }
}
