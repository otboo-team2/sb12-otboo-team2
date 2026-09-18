package com.otboo.recommendation.search.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.otboo.clothes.repository.ClothesRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class RecommendationClothesReindexServiceTest {

    private final ClothesRepository clothesRepository = mock(ClothesRepository.class);
    private final RecommendationClothesIndexer indexer = mock(RecommendationClothesIndexer.class);
    private final RecommendationClothesIndexManager indexManager =
            mock(RecommendationClothesIndexManager.class);
    private final RecommendationClothesReindexService service =
            new RecommendationClothesReindexService(clothesRepository, indexer, indexManager);

    @Test
    void processesAllPagesAndContinuesAfterOneFailure() throws Exception {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        when(clothesRepository.findIdsAfter(any(), any(Pageable.class)))
                .thenReturn(List.of(first, second), List.of(third), List.of());
        when(indexer.index(first)).thenReturn(true);
        when(indexer.index(second)).thenReturn(false);
        when(indexer.index(third)).thenReturn(true);

        var result = service.reindexAll();

        assertThat(result.succeeded()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failedClothesIds()).containsExactly(second);
        verify(indexer).index(first);
        verify(indexer).index(second);
        verify(indexer).index(third);
        verify(clothesRepository, times(3)).findIdsAfter(any(), any(Pageable.class));
        ArgumentCaptor<UUID> afterIds = ArgumentCaptor.forClass(UUID.class);
        verify(clothesRepository, times(3)).findIdsAfter(afterIds.capture(), any(Pageable.class));
        assertThat(afterIds.getAllValues()).containsExactly(null, second, third);
    }

    @Test
    void cleanupPagesKeepPresentDocumentsAndReportFailedDeletes() throws Exception {
        UUID live = UUID.randomUUID();
        UUID ghost = UUID.randomUUID();
        UUID failed = UUID.randomUUID();
        when(clothesRepository.findIdsAfter(any(), any(Pageable.class))).thenReturn(List.of());
        when(indexManager.documentIdsAfter(any(), anyInt())).thenReturn(
                List.of(hit(live), hit(ghost)), List.of(hit(failed)), List.of());
        when(indexer.deleteIfMissing(live)).thenReturn(RecommendationClothesIndexer.CleanupResult.PRESENT);
        when(indexer.deleteIfMissing(ghost)).thenReturn(RecommendationClothesIndexer.CleanupResult.DELETED);
        when(indexer.deleteIfMissing(failed)).thenReturn(RecommendationClothesIndexer.CleanupResult.FAILED);
        var result = service.reindexAll();
        assertThat(result.deleted()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.failedClothesIds()).containsExactly(failed);
        var cursor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(indexManager, times(3)).documentIdsAfter(cursor.capture(), eq(100));
        assertThat(cursor.getAllValues().getFirst()).isEmpty();
        assertThat(((co.elastic.clients.elasticsearch._types.FieldValue) cursor.getAllValues().get(1).getFirst()).stringValue())
                .isEqualTo(ghost.toString());
        assertThat(((co.elastic.clients.elasticsearch._types.FieldValue) cursor.getAllValues().get(2).getFirst()).stringValue())
                .isEqualTo(failed.toString());
        verify(indexManager, times(2)).refresh();
    }

    @Test
    void cleanupScanFailureIsPropagated() throws Exception {
        when(clothesRepository.findIdsAfter(any(), any(Pageable.class))).thenReturn(List.of());
        when(indexManager.documentIdsAfter(any(), anyInt())).thenThrow(new java.io.IOException("scan failed"));
        org.assertj.core.api.Assertions.assertThatThrownBy(service::reindexAll)
                .isInstanceOf(java.io.IOException.class).hasMessage("scan failed");
        verifyNoInteractions(indexer);
    }

    private co.elastic.clients.elasticsearch.core.search.Hit<Void> hit(UUID id) {
        return co.elastic.clients.elasticsearch.core.search.Hit.of(h -> h.index("recommendation-clothes-v1")
                .id(id.toString()).sort(co.elastic.clients.elasticsearch._types.FieldValue.of(id.toString())));
    }

    @Test
    void emptyRepositoryFinishesWithZeroCounts() throws Exception {
        when(clothesRepository.findIdsAfter(any(), any(Pageable.class))).thenReturn(List.of());

        var result = service.reindexAll();

        assertThat(result.succeeded()).isZero();
        assertThat(result.failed()).isZero();
        assertThat(result.failedClothesIds()).isEmpty();
        verifyNoInteractions(indexer);
    }
}
