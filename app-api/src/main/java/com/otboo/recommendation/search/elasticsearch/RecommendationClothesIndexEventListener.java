package com.otboo.recommendation.search.elasticsearch;

import com.otboo.clothes.search.ClothesIndexEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** 의상 변경이 커밋된 뒤 추천 색인을 갱신한다. */
@RequiredArgsConstructor
public class RecommendationClothesIndexEventListener {

    private final RecommendationClothesIndexer indexer;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ClothesIndexEvent event) {
        switch (event.operation()) {
            case UPSERT -> indexer.index(event.clothesId());
            case DELETE -> indexer.delete(event.clothesId());
        }
    }
}
