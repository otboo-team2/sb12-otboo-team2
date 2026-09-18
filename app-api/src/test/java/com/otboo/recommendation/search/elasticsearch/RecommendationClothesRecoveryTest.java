package com.otboo.recommendation.search.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import com.otboo.clothes.ClothesService;
import com.otboo.clothes.repository.ClothesRepository;
import com.otboo.recommendation.ai.RecommendationClothesEmbeddingService;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class RecommendationClothesRecoveryTest {
    @Test
    void reindexRemovesDocumentLeftByFailedDelete() throws Exception {
        UUID ghost = UUID.randomUUID();
        Set<String> documents = new HashSet<>(Set.of(ghost.toString()));
        ElasticsearchClient client = mock(ElasticsearchClient.class);
        ClothesService clothes = mock(ClothesService.class);
        RecommendationClothesEmbeddingService embeddings = mock(RecommendationClothesEmbeddingService.class);
        RecommendationClothesIndexManager manager = mock(RecommendationClothesIndexManager.class);
        ClothesRepository repository = mock(ClothesRepository.class);
        when(manager.alias()).thenReturn("recommendation-clothes");
        when(repository.findIdsAfter(any(), any(Pageable.class))).thenReturn(List.of());
        when(manager.documentIdsAfter(any(), anyInt())).thenReturn(List.of(
                co.elastic.clients.elasticsearch.core.search.Hit.of(h -> h.index("recommendation-clothes-v1")
                        .id(ghost.toString()).sort(co.elastic.clients.elasticsearch._types.FieldValue.of(ghost.toString())))), List.of());
        AtomicBoolean failFirstDelete = new AtomicBoolean(true);
        when(client.delete(any(DeleteRequest.class))).thenAnswer(inv -> {
            if (failFirstDelete.getAndSet(false)) throw new IOException("ES unavailable");
            DeleteRequest request = inv.getArgument(0);
            documents.remove(request.id());
            return null;
        });
        RecommendationClothesIndexer indexer = new RecommendationClothesIndexer(client, clothes, embeddings, manager);
        assertThat(indexer.delete(ghost)).isFalse();
        assertThat(documents).containsExactly(ghost.toString());

        new RecommendationClothesReindexService(repository, indexer, manager).reindexAll();

        assertThat(documents).isEmpty();
        verifyNoInteractions(embeddings);
    }
}
