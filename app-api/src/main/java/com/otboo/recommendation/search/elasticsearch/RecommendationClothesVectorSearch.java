package com.otboo.recommendation.search.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.logging.SafeExceptionLog;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/** 날씨 후보와 소유자로 미리 제한한 kNN 검색. MySQL 재검증은 후속 단계에서 수행한다. */
@RequiredArgsConstructor
public class RecommendationClothesVectorSearch {

    private static final int TOP_K = 10;

    private final ElasticsearchClient client;
    private final RecommendationClothesIndexManager indexManager;

    public List<UUID> search(UUID ownerId, Collection<UUID> candidateIds, List<Float> vector) {
        if (candidateIds.isEmpty()) {
            return List.of();
        }
        if (ownerId == null || vector == null || vector.size() != 1536) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }

        Set<String> allowedIds = candidateIds.stream().map(UUID::toString).collect(Collectors.toSet());
        List<FieldValue> ids = allowedIds.stream().sorted().map(FieldValue::of).toList();
        int k = Math.min(TOP_K, ids.size());
        SearchRequest request = SearchRequest.of(r -> r
                .index(indexManager.alias())
                .size(k)
                .source(s -> s.fetch(false))
                .allowPartialSearchResults(false)
                .knn(knn -> knn.field("embedding").queryVector(vector)
                        .k(k).numCandidates(Math.min(100, ids.size()))
                        .filter(f -> f.bool(b -> b
                                .filter(owner -> owner.term(t -> t.field("ownerId")
                                        .value(ownerId.toString())))
                                .filter(candidates -> candidates.terms(t -> t.field("clothesId")
                                        .terms(v -> v.value(ids))))))));
        try {
            var response = client.search(request, Void.class);
            if (response.timedOut() || response.shards().failed().intValue() > 0) {
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR);
            }
            return response.hits().hits().stream()
                    .map(hit -> hit.id())
                    .filter(allowedIds::contains)
                    .map(UUID::fromString)
                    .toList();
        } catch (IOException | ElasticsearchException error) {
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_ERROR,
                    SafeExceptionLog.sanitized(error));
        }
    }
}
