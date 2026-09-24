package com.otboo.recommendation.search.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.recommendation.ai.RecommendationClothesMetadata;
import com.otboo.recommendation.ai.RecommendationFormality;
import com.otboo.recommendation.ai.RecommendationOccasion;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RecommendationClothesVectorSearchTest {

    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final RecommendationClothesIndexManager manager = new RecommendationClothesIndexManager(
            client, new RecommendationClothesSearchProperties(true, null));
    private final RecommendationClothesVectorSearch search = new RecommendationClothesVectorSearch(client, manager);
    private final UUID ownerId = UUID.randomUUID();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private final List<Float> vector = java.util.Collections.nCopies(1536, 0.1f);

    @Test
    void knnRequestPrefiltersOwnerAndWeatherCandidateIdsAndPreservesRelevanceOrder() throws IOException {
        when(client.search(any(SearchRequest.class), eq(Void.class)))
                .thenReturn(response(false, 0, second, outsider, first));

        assertThat(search.search(ownerId, List.of(first, second), vector)).containsExactly(second, first);

        var captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(captor.capture(), eq(Void.class));
        SearchRequest request = captor.getValue();
        assertThat(request.index()).containsExactly("recommendation-clothes");
        assertThat(request.size()).isEqualTo(2);
        assertThat(request.source().fetch()).isFalse();
        assertThat(request.allowPartialSearchResults()).isFalse();
        assertThat(request.query()).isNull();
        var knn = request.knn().getFirst();
        assertThat(knn.field()).isEqualTo("embedding");
        assertThat(knn.queryVector()).containsExactlyElementsOf(vector);
        assertThat(knn.k()).isEqualTo(2);
        assertThat(knn.numCandidates()).isEqualTo(2);
        var filters = knn.filter().getFirst().bool().filter();
        assertThat(filters).hasSize(2);
        assertThat(filters.getFirst().term().field()).isEqualTo("ownerId");
        assertThat(filters.getFirst().term().value().stringValue()).isEqualTo(ownerId.toString());
        assertThat(filters.get(1).terms().field()).isEqualTo("clothesId");
        assertThat(filters.get(1).terms().terms().value().stream().map(value -> value.stringValue()))
                .containsExactlyInAnyOrder(first.toString(), second.toString());
    }

    @Test
    void emptyCandidatesNeverCallElasticsearch() {
        assertThat(search.search(ownerId, List.of(), null)).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void networkFailureBecomesExternalFailureForBasicRecommendationFallback() throws IOException {
        when(client.search(any(SearchRequest.class), eq(Void.class)))
                .thenThrow(new IOException("unavailable"));

        assertThatThrownBy(() -> search.search(ownerId, List.of(first), vector))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR));
    }

    @Test
    void timedOutResponseIsNotAcceptedAsSuccessfulSearch() throws IOException {
        when(client.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response(true, 0, first));

        assertThatThrownBy(() -> search.search(ownerId, List.of(first), vector))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR));
    }

    @Test
    void failedShardResponseIsNotAcceptedAsSuccessfulSearch() throws IOException {
        when(client.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response(false, 1, first));

        assertThatThrownBy(() -> search.search(ownerId, List.of(first), vector))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(CommonErrorCode.EXTERNAL_API_ERROR));
    }

    @Test
    void metadataIsLinkedByClothesIdAndRejectsWrongOwnerSource() throws IOException {
        var firstDocument = new RecommendationClothesDocument(first.toString(), ownerId.toString(),
                "TOP", "first", List.of("포멀"), RecommendationFormality.HIGH,
                List.of(RecommendationOccasion.WORK), null);
        var outsiderDocument = new RecommendationClothesDocument(second.toString(), outsider.toString(),
                "SHOES", "outsider", List.of("스트릿"), RecommendationFormality.LOW,
                List.of(RecommendationOccasion.DAILY), null);
        SearchResponse<RecommendationClothesDocument> response = SearchResponse.of(r -> r
                .took(1).timedOut(false).shards(s -> s.total(1).successful(1).failed(0))
                .hits(h -> h.hits(List.of(
                        Hit.of(hit -> hit.index("recommendation-clothes-v2").id(first.toString())
                                .source(firstDocument)),
                        Hit.of(hit -> hit.index("recommendation-clothes-v2").id(second.toString())
                                .source(outsiderDocument))))));
        when(client.search(any(SearchRequest.class), eq(RecommendationClothesDocument.class)))
                .thenReturn(response);

        assertThat(search.metadata(ownerId, List.of(first, second)))
                .containsOnlyKeys(first)
                .containsEntry(first, new RecommendationClothesMetadata(
                        List.of("포멀"), RecommendationFormality.HIGH,
                        List.of(RecommendationOccasion.WORK)));

        var captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(captor.capture(), eq(RecommendationClothesDocument.class));
        assertThat(captor.getValue().source().filter().includes())
                .containsExactly("clothesId", "ownerId", "inferredStyles", "formality", "occasions");
    }

    private SearchResponse<Void> response(boolean timedOut, int failed, UUID... ids) {
        List<Hit<Void>> hits = java.util.Arrays.stream(ids)
                .map(id -> Hit.<Void>of(h -> h.index("recommendation-clothes-v1").id(id.toString())))
                .toList();
        return SearchResponse.of(r -> r.took(1).timedOut(timedOut)
                .shards(s -> s.total(1).successful(failed == 0 ? 1 : 0).failed(failed))
                .hits(h -> h.hits(hits)));
    }
}
