package com.otboo.recommendation.search.elasticsearch;

import com.otboo.clothes.dto.ClothesAttributeWithDefDto;
import com.otboo.clothes.dto.ClothesDto;
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
        List<Float> embedding
) {

    public RecommendationClothesDocument {
        embedding = embedding == null ? null : List.copyOf(embedding);
    }

    public static RecommendationClothesDocument of(ClothesDto clothes) {
        return new RecommendationClothesDocument(
                clothes.id().toString(),
                clothes.ownerId().toString(),
                clothes.type().name(),
                contentOf(clothes),
                null);
    }

    /**
     * content 규칙: "의상명 타입:TYPE 속성:정의=값 ...".
     * 속성 순서를 정렬해 DB 조회 순서가 달라도 같은 Embedding 입력을 만든다.
     */
    public static String contentOf(ClothesDto clothes) {
        String attributes = clothes.attributes().stream()
                .sorted(Comparator
                        .comparing(ClothesAttributeWithDefDto::definitionName,
                                Comparator.nullsFirst(String::compareTo))
                        .thenComparing(ClothesAttributeWithDefDto::value,
                                Comparator.nullsFirst(String::compareTo)))
                .map(attribute -> attribute.definitionName() + "=" + attribute.value())
                .collect(Collectors.joining(" "));
        return clothes.name() + " 타입:" + clothes.type().name()
                + (attributes.isBlank() ? "" : " 속성:" + attributes);
    }
}
