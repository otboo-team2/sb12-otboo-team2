package com.otboo.recommendation.search.elasticsearch;

import com.otboo.clothes.dto.ClothesAttributeWithDefDto;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.recommendation.ai.RecommendationClothesMetadata;
import com.otboo.recommendation.ai.RecommendationFormality;
import com.otboo.recommendation.ai.RecommendationOccasion;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 추천 검색용 의상 문서. Elasticsearch는 검색 후보와 점수만 정하고,
 * 최종 의상 데이터는 항상 MySQL에서 다시 읽는다.
 *
 * <p>{@code content}는 Embedding 입력과 키워드 검색에 함께 쓰는 결정적 텍스트다.
 * 의상명·타입·속성 이름·속성값을 포함하고, 사용자에게 보여주는 문장은 넣지 않는다.
 */
public record RecommendationClothesDocument(
        String clothesId,
        String ownerId,
        String type,
        String content,
        List<String> inferredStyles,
        RecommendationFormality formality,
        List<RecommendationOccasion> occasions,
        List<Float> embedding
) {

    public RecommendationClothesDocument {
        inferredStyles = inferredStyles == null ? List.of() : List.copyOf(inferredStyles);
        occasions = occasions == null ? List.of() : List.copyOf(occasions);
        embedding = embedding == null ? null : List.copyOf(embedding);
    }

    public static RecommendationClothesDocument of(ClothesDto clothes) {
        return of(clothes, RecommendationClothesMetadata.EMPTY);
    }

    public static RecommendationClothesDocument of(
            ClothesDto clothes,
            RecommendationClothesMetadata metadata
    ) {
        RecommendationClothesMetadata safeMetadata = metadata == null
                ? RecommendationClothesMetadata.EMPTY : metadata;
        return new RecommendationClothesDocument(
                clothes.id().toString(),
                clothes.ownerId().toString(),
                clothes.type().name(),
                contentOf(clothes, safeMetadata),
                safeMetadata.inferredStyles(),
                safeMetadata.formality(),
                safeMetadata.occasions(),
                null);
    }

    /**
     * content 규칙: "의상명 타입:TYPE 속성:정의=값 ...".
     * 속성 순서를 정렬해 DB 조회 순서가 달라도 같은 Embedding 입력을 만든다.
     */
    public static String contentOf(ClothesDto clothes) {
        return contentOf(clothes, RecommendationClothesMetadata.EMPTY);
    }

    public static String contentOf(ClothesDto clothes, RecommendationClothesMetadata metadata) {
        String attributes = clothes.attributes().stream()
                .sorted(Comparator
                        .comparing(ClothesAttributeWithDefDto::definitionName,
                                Comparator.nullsFirst(String::compareTo))
                        .thenComparing(ClothesAttributeWithDefDto::value,
                                Comparator.nullsFirst(String::compareTo)))
                .map(attribute -> attribute.definitionName() + "=" + attribute.value())
                .collect(Collectors.joining(" "));
        StringBuilder content = new StringBuilder(clothes.name())
                .append(" 타입:").append(clothes.type().name());
        if (!attributes.isBlank()) {
            content.append(" 속성:").append(attributes);
        }
        String styles = metadata.inferredStyles().stream().distinct().sorted()
                .collect(Collectors.joining(","));
        if (!styles.isBlank()) {
            content.append(" 추론스타일:").append(styles);
        }
        if (metadata.formality() != null) {
            content.append(" 격식도:").append(metadata.formality().name());
        }
        String occasions = metadata.occasions().stream().distinct().sorted()
                .map(Enum::name).collect(Collectors.joining(","));
        if (!occasions.isBlank()) {
            content.append(" 적합상황:").append(occasions);
        }
        return content.toString();
    }
}
