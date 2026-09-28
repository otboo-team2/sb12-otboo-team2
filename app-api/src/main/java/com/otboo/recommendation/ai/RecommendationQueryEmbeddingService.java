package com.otboo.recommendation.ai;

import com.otboo.clothes.entity.ClothesType;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 자연어 요청과 기존 추천 조건을 의상 검색용 embedding 입력으로 변환한다. */
@Service
public class RecommendationQueryEmbeddingService {

    private final OpenAiEmbeddingClient embeddingClient;

    public RecommendationQueryEmbeddingService(OpenAiEmbeddingClient embeddingClient) {
        this.embeddingClient = embeddingClient;
    }

    public List<Float> embed(String prompt, RecommendationCondition condition, Set<String> preferredStyles) {
        return embeddingClient.embed(queryText(prompt, condition, preferredStyles));
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
        append(text, "선호 스타일", preferredStyles == null ? List.of() : List.copyOf(preferredStyles));
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
