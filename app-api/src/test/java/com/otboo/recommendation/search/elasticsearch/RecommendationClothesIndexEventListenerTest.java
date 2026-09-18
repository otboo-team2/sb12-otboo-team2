package com.otboo.recommendation.search.elasticsearch;

import static org.mockito.Mockito.*;

import com.otboo.clothes.search.ClothesIndexEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationClothesIndexEventListenerTest {

    private final RecommendationClothesIndexer indexer = mock(RecommendationClothesIndexer.class);
    private final RecommendationClothesIndexEventListener listener =
            new RecommendationClothesIndexEventListener(indexer);
    private final UUID clothesId = UUID.randomUUID();

    @Test
    void upsertEventIndexesAfterCommitListenerReceivesIt() {
        listener.on(ClothesIndexEvent.upsert(clothesId));

        verify(indexer).index(clothesId);
        verify(indexer, never()).delete(any());
    }

    @Test
    void deleteEventOnlyDeletesTheDocument() {
        listener.on(ClothesIndexEvent.delete(clothesId));

        verify(indexer).delete(clothesId);
        verify(indexer, never()).index(any());
    }
}
