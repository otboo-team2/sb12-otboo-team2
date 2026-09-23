package com.otboo.recommendation.search.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.exception.CommonErrorCode;
import com.otboo.common.logging.SafeExceptionLog;
import com.otboo.clothes.ClothesService;
import com.otboo.clothes.dto.ClothesDto;
import com.otboo.recommendation.ai.RecommendationClothesEmbeddingService;
import com.otboo.recommendation.ai.RecommendationClothesMetadataAnalyzer;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 커밋된 의상 원본을 추천용 Elasticsearch 문서로 동기화한다. */
@Slf4j
@RequiredArgsConstructor
public class RecommendationClothesIndexer {

    private final ElasticsearchClient client;
    private final ClothesService clothesService;
    private final RecommendationClothesMetadataAnalyzer metadataAnalyzer;
    private final RecommendationClothesEmbeddingService embeddingService;
    private final RecommendationClothesIndexManager indexManager;

    // 단일 JVM에서 같은 ID의 조회부터 ES 반영까지 직렬화한다. 고정 크기로 메모리를 제한한다.
    // 다중 인스턴스에서는 이 잠금만으로 순서를 보장할 수 없다.
    private final ReentrantLock[] locks = IntStream.range(0, 64)
            .mapToObj(i -> new ReentrantLock(true)).toArray(ReentrantLock[]::new);

    private ReentrantLock lockFor(UUID id) {
        return locks[Math.floorMod(id.hashCode(), locks.length)];
    }

    /** 현재 MySQL 데이터를 다시 읽어 같은 ID로 덮어쓴다. */
    public boolean index(UUID clothesId) {
        ReentrantLock lock = lockFor(clothesId);
        lock.lock();
        String stage = "es_index_setup";
        try {
            indexManager.createIndexIfMissing();
            stage = "mysql_read";
            List<ClothesDto> clothes = clothesService.findForRecommendation(clothesId);
            if (clothes.isEmpty()) {
                return delete(clothesId);
            }

            stage = "metadata";
            var metadata = metadataAnalyzer.analyze(clothes.getFirst());
            stage = "document";
            RecommendationClothesDocument document =
                    RecommendationClothesDocument.of(clothes.getFirst(), metadata);
            stage = "embedding";
            List<Float> embedding = embeddingService.embed(document);
            RecommendationClothesDocument indexed = new RecommendationClothesDocument(
                    document.clothesId(),
                    document.ownerId(),
                    document.type(),
                    document.content(),
                    document.inferredStyles(),
                    document.formality(),
                    document.occasions(),
                    embedding);
            stage = "es_upsert";
            client.index(IndexRequest.of(request -> request
                    .index(indexManager.alias())
                    .id(indexed.clothesId())
                    .document(indexed)));
            log.info("recommendation_clothes_indexed clothesId={}", clothesId);
            return true;
        } catch (IOException | RuntimeException exception) {
            logFailure("recommendation_clothes_index_failed", clothesId, stage, exception);
            return false;
        } finally {
            lock.unlock();
        }
    }

    /** 삭제는 원본이 이미 지워졌으므로 Embedding API를 호출하지 않는다. */
    public boolean delete(UUID clothesId) {
        ReentrantLock lock = lockFor(clothesId);
        lock.lock();
        try {
            client.delete(DeleteRequest.of(request -> request
                    .index(indexManager.alias())
                    .id(clothesId.toString())));
            log.info("recommendation_clothes_deleted clothesId={}", clothesId);
            return true;
        } catch (ElasticsearchException exception) {
            String errorType = exception.error() == null ? null : exception.error().type();
            if ("index_not_found_exception".equals(errorType)
                    || "document_missing_exception".equals(errorType)) {
                log.info("recommendation_clothes_delete_skipped_missing clothesId={}", clothesId);
                return true;
            }
            logFailure("recommendation_clothes_delete_failed", clothesId, "es_delete", exception);
            return false;
        } catch (IOException | RuntimeException exception) {
            logFailure("recommendation_clothes_delete_failed", clothesId, "es_delete", exception);
            return false;
        } finally {
            lock.unlock();
        }
    }

    /** 복구 작업도 동일한 잠금 안에서 MySQL 존재 여부를 확인하고 삭제한다. */
    public CleanupResult deleteIfMissing(UUID clothesId) {
        ReentrantLock lock = lockFor(clothesId);
        lock.lock();
        try {
            if (!clothesService.findForRecommendation(clothesId).isEmpty()) {
                return CleanupResult.PRESENT;
            }
            return delete(clothesId) ? CleanupResult.DELETED : CleanupResult.FAILED;
        } catch (RuntimeException exception) {
            logFailure("recommendation_clothes_cleanup_failed", clothesId, "mysql_read", exception);
            return CleanupResult.FAILED;
        } finally {
            lock.unlock();
        }
    }

    private void logFailure(String event, UUID clothesId, String stage, Exception exception) {
        String code = CommonErrorCode.INTERNAL_ERROR.getCode();
        String status = "none";
        if (exception instanceof BusinessException business) {
            code = business.getErrorCode().getCode();
            String externalStatus = business.getDetails().get("status");
            if (externalStatus != null && externalStatus.matches("[1-5][0-9]{2}")) {
                status = externalStatus;
            }
        } else if (exception instanceof ElasticsearchException elasticsearch) {
            code = CommonErrorCode.EXTERNAL_API_ERROR.getCode();
            status = String.valueOf(elasticsearch.status());
        }
        log.error("{} clothesId={} stage={} error_code={} status={} exception_type={}",
                event, clothesId, stage, code, status, exception.getClass().getSimpleName(),
                SafeExceptionLog.sanitized(exception));
    }

    public enum CleanupResult { PRESENT, DELETED, FAILED }

}
