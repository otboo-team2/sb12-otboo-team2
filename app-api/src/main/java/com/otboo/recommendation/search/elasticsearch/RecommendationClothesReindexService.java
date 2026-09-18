package com.otboo.recommendation.search.elasticsearch;

import com.otboo.clothes.repository.ClothesRepository;
import co.elastic.clients.elasticsearch._types.FieldValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;

/** 기존 MySQL 의상을 추천 검색 색인에 채우는 명시적 운영 작업. */
@Slf4j
@RequiredArgsConstructor
public class RecommendationClothesReindexService {

    private static final int BATCH_SIZE = 100;

    private final ClothesRepository clothesRepository;
    private final RecommendationClothesIndexer indexer;
    private final RecommendationClothesIndexManager indexManager;

    public ReindexResult reindexAll() throws IOException {
        indexManager.createIndexIfMissing();

        UUID afterId = null;
        long succeeded = 0;
        long failed = 0;
        List<UUID> failedIds = new ArrayList<>();
        while (true) {
            List<UUID> ids = clothesRepository.findIdsAfter(afterId, PageRequest.of(0, BATCH_SIZE));
            if (ids.isEmpty()) {
                break;
            }
            for (UUID clothesId : ids) {
                if (indexer.index(clothesId)) {
                    succeeded++;
                } else {
                    failed++;
                    failedIds.add(clothesId);
                }
            }
            afterId = ids.getLast();
        }
        // 삭제 이벤트가 실패한 문서도 복구한다. 전체 ES 문서를 메모리에 올리지 않는다.
        indexManager.refresh();
        List<FieldValue> after = List.of();
        long deleted = 0;
        while (true) {
            var hits = indexManager.documentIdsAfter(after, BATCH_SIZE);
            if (hits.isEmpty()) {
                break;
            }
            for (var hit : hits) {
                UUID id = UUID.fromString(hit.id());
                switch (indexer.deleteIfMissing(id)) {
                    case DELETED -> deleted++;
                    case FAILED -> { failed++; failedIds.add(id); }
                    case PRESENT -> { }
                }
            }
            var next = hits.getLast().sort();
            if (next.size() != 1 || (!after.isEmpty()
                    && next.getFirst().stringValue().equals(after.getFirst().stringValue()))) {
                throw new IOException("추천 의상 복구 페이지 cursor 누락 또는 반복");
            }
            after = next;
        }
        indexManager.refresh();
        log.info("recommendation_clothes_reindex_completed succeeded={} failed={} deleted={}",
                succeeded, failed, deleted);
        return new ReindexResult(succeeded, failed, List.copyOf(failedIds), deleted);
    }

    public record ReindexResult(long succeeded, long failed, List<UUID> failedClothesIds, long deleted) {
    }
}
