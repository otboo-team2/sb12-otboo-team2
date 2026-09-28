package com.otboo.recommendation.search.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.otboo.clothes.ClothesService;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.clothes.entity.ClothesType;
import com.otboo.recommendation.ai.RecommendationClothesEmbeddingService;
import com.otboo.recommendation.ai.RecommendationClothesMetadata;
import com.otboo.recommendation.ai.RecommendationClothesMetadataAnalyzer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RecommendationClothesIndexerConcurrencyTest {
    private final UUID id = UUID.randomUUID();
    private final UUID owner = UUID.randomUUID();
    private final ElasticsearchClient client = mock(ElasticsearchClient.class);
    private final ClothesService clothes = mock(ClothesService.class);
    private final RecommendationClothesEmbeddingService embeddings = mock(RecommendationClothesEmbeddingService.class);
    private final RecommendationClothesMetadataAnalyzer metadata = mock(RecommendationClothesMetadataAnalyzer.class);
    private final RecommendationClothesIndexManager manager = mock(RecommendationClothesIndexManager.class);
    private final RecommendationClothesIndexer indexer = new RecommendationClothesIndexer(client, clothes, metadata, embeddings, manager);

    @Test
    void laterUpdateMustNotBeOverwrittenBySlowEarlierEmbedding() throws Exception {
        runRace(false);
    }

    @Test
    void deleteMustNotBeUndoneBySlowEarlierEmbedding() throws Exception {
        runRace(true);
    }

    private void runRace(boolean delete) throws Exception {
        when(metadata.analyze(any())).thenReturn(RecommendationClothesMetadata.EMPTY);
        CountDownLatch firstEmbedding = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicReference<ClothesDto> database = new AtomicReference<>(dto("A"));
        AtomicReference<RecommendationClothesDocument> elasticsearch = new AtomicReference<>();
        when(manager.alias()).thenReturn("recommendation-clothes");
        when(clothes.findForRecommendation(id)).thenAnswer(inv -> database.get() == null ? List.of() : List.of(database.get()));
        when(embeddings.embed(any())).thenAnswer(inv -> {
            RecommendationClothesDocument doc = inv.getArgument(0);
            if (doc.content().startsWith("A ")) {
                firstEmbedding.countDown();
                if (!releaseFirst.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            }
            return List.of(0.1f);
        });
        when(client.index(any(IndexRequest.class))).thenAnswer(inv -> {
            IndexRequest<RecommendationClothesDocument> request = inv.getArgument(0);
            elasticsearch.set(request.document());
            return null;
        });
        when(client.delete(any(DeleteRequest.class))).thenAnswer(inv -> { elasticsearch.set(null); return null; });
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> indexer.index(id));
            assertThat(firstEmbedding.await(5, TimeUnit.SECONDS)).isTrue();
            database.set(delete ? null : dto("B"));
            CountDownLatch secondStarted = new CountDownLatch(1);
            Future<Boolean> second = executor.submit(() -> {
                secondStarted.countDown();
                return delete ? indexer.delete(id) : indexer.index(id);
            });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            try {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> second.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
            } finally {
                releaseFirst.countDown();
            }
            assertThat(first.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(second.get(5, TimeUnit.SECONDS)).isTrue();
            if (delete) assertThat(elasticsearch.get()).isNull();
            else assertThat(elasticsearch.get().content()).startsWith("B ");
        } finally {
            releaseFirst.countDown();
        }
    }

    @Test
    void duplicateEventsUpsertTheSameDocumentId() throws Exception {
        when(metadata.analyze(any())).thenReturn(RecommendationClothesMetadata.EMPTY);
        when(manager.alias()).thenReturn("recommendation-clothes");
        when(clothes.findForRecommendation(id)).thenReturn(List.of(dto("current")));
        when(embeddings.embed(any())).thenReturn(List.of(0.1f));
        var documents = new java.util.HashMap<String, RecommendationClothesDocument>();
        when(client.index(any(IndexRequest.class))).thenAnswer(inv -> {
            IndexRequest<RecommendationClothesDocument> request = inv.getArgument(0);
            documents.put(request.id(), request.document());
            return null;
        });
        assertThat(indexer.index(id)).isTrue();
        assertThat(indexer.index(id)).isTrue();
        assertThat(documents).containsOnlyKeys(id.toString());
    }

    @Test
    void queuedUpsertAfterDeleteOnlyDeletesAndDoesNotEmbed() throws Exception {
        when(manager.alias()).thenReturn("recommendation-clothes");
        when(clothes.findForRecommendation(id)).thenReturn(List.of());
        assertThat(indexer.delete(id)).isTrue();
        assertThat(indexer.index(id)).isTrue();
        verifyNoInteractions(embeddings);
        verify(client, never()).index(any(IndexRequest.class));
        verify(client, times(2)).delete(any(DeleteRequest.class));
    }

    private ClothesDto dto(String name) {
        return new ClothesDto(id, owner, name, null, ClothesType.TOP, false, List.of());
    }
}
