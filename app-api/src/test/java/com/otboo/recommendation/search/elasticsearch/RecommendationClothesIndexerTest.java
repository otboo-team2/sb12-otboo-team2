package com.otboo.recommendation.search.elasticsearch;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.otboo.clothes.ClothesService;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.recommendation.ai.RecommendationClothesEmbeddingService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecommendationClothesIndexerTest {

    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final ClothesService clothesService = mock(ClothesService.class);
    private final RecommendationClothesEmbeddingService embeddingService =
            mock(RecommendationClothesEmbeddingService.class);
    private final RecommendationClothesIndexManager indexManager =
            mock(RecommendationClothesIndexManager.class);
    private final RecommendationClothesIndexer indexer = new RecommendationClothesIndexer(
            client, clothesService, embeddingService, indexManager);
    private final UUID clothesId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        when(indexManager.alias()).thenReturn("recommendation-clothes");
        when(clothesService.findForRecommendation(clothesId)).thenReturn(List.of(
                new ClothesDto(clothesId, UUID.randomUUID(), "티셔츠", null,
                        ClothesType.TOP, false, List.of())));
        when(embeddingService.embed(any())).thenReturn(List.of(0.1f, 0.2f));
    }

    @Test
    void indexReadsLatestDataEmbedsAndUpsertsByClothesId() throws Exception {
        indexer.index(clothesId);

        verify(indexManager).createIndexIfMissing();
        verify(embeddingService).embed(any(RecommendationClothesDocument.class));
        verify(client).index(any(co.elastic.clients.elasticsearch.core.IndexRequest.class));
    }

    @Test
    void cleanupPreservesExistingClothesRegardlessOfOwner() throws Exception {
        org.assertj.core.api.Assertions.assertThat(indexer.deleteIfMissing(clothesId))
                .isEqualTo(RecommendationClothesIndexer.CleanupResult.PRESENT);
        verify(client, never()).delete(any(co.elastic.clients.elasticsearch.core.DeleteRequest.class));
        verifyNoInteractions(embeddingService);
    }

    @Test
    void mysqlLookupFailureNeverDeletesDocument() throws Exception {
        when(clothesService.findForRecommendation(clothesId)).thenThrow(new IllegalStateException("DB down"));
        org.assertj.core.api.Assertions.assertThat(indexer.deleteIfMissing(clothesId))
                .isEqualTo(RecommendationClothesIndexer.CleanupResult.FAILED);
        verifyNoInteractions(client, embeddingService);
    }

    @Test
    void failedEmbeddingDoesNotHoldLockForNextAttempt() throws Exception {
        when(embeddingService.embed(any())).thenThrow(new IllegalStateException("timeout"))
                .thenReturn(List.of(0.1f));
        org.assertj.core.api.Assertions.assertThat(indexer.index(clothesId)).isFalse();
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            org.assertj.core.api.Assertions.assertThat(executor.submit(() -> indexer.index(clothesId))
                    .get(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        verify(client, times(1)).index(any(co.elastic.clients.elasticsearch.core.IndexRequest.class));
    }

    @Test
    void deleteDoesNotCallEmbedding() throws Exception {
        indexer.delete(clothesId);

        verify(client).delete(any(co.elastic.clients.elasticsearch.core.DeleteRequest.class));
        verifyNoInteractions(embeddingService);
    }
}
