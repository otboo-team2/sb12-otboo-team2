package com.otboo.virtualtryon;

import com.otboo.clothes.entity.Clothes;
import com.otboo.common.event.VirtualTryOnCompletedEvent;
import com.otboo.common.exception.BusinessException;
import com.otboo.common.storage.ImageStorage;
import com.otboo.virtualtryon.entity.VirtualTryOnCache;
import com.otboo.virtualtryon.entity.VirtualTryOnJob;
import com.otboo.virtualtryon.entity.VirtualTryOnJobStatus;
import com.otboo.virtualtryon.entity.VirtualTryOnStep;
import com.otboo.virtualtryon.exception.VirtualTryOnErrorCode;
import com.otboo.virtualtryon.repository.VirtualTryOnCacheRepository;
import com.otboo.virtualtryon.repository.VirtualTryOnJobRepository;
import com.otboo.virtualtryon.util.VirtualTryOnCacheKeyGenerator;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * VirtualTryOnPoller가 호출하는 실제 DB 트랜잭션들을 모아둔 클래스.
 * Poller 안에서 this.method() 로 직접 호출하면 @Transactional(REQUIRES_NEW)가
 * 프록시를 안 거쳐서 무시되는 문제가 있어서, 별도 빈으로 분리해 DI로 호출한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualTryOnJobTransactionService {

    private final VirtualTryOnJobRepository jobRepository;
    private final VirtualTryOnCacheRepository cacheRepository;
    private final VirtualTryOnCacheWriter cacheWriter;
    private final ImageStorage imageStorage;
    private final ApplicationEventPublisher eventPublisher;

    /** PENDING job을 최대 limit개 잠그고(SKIP LOCKED) PROCESSING으로 바꾼다. 인스턴스가 여러 개여도 같은 job을 중복으로 못 가져간다. */
    @Transactional
    public List<UUID> claimPendingJobs(int limit) {
        List<UUID> jobIds = jobRepository.lockPendingJobIds(limit).stream()
            .map(UUID::fromString)
            .toList();
        if (!jobIds.isEmpty()) {
            jobRepository.markProcessing(jobIds);
        }
        return jobIds;
    }

    /** 현재 PROCESSING 상태인 job id 목록을 조회한다. poller가 폴링할 대상을 고를 때 쓴다. */
    public List<UUID> findProcessingJobIds() {
        return jobRepository.findAllByStatus(VirtualTryOnJobStatus.PROCESSING).stream()
            .map(VirtualTryOnJob::getId)
            .toList();
    }

    /** job의 현재 단계(TOP/BOTTOM/ADDITIONAL)에 맞춰 FASHN에 보낼 모델 이미지 + 상품 이미지를 꺼낸다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DispatchTarget loadDispatchTarget(UUID jobId) {
        VirtualTryOnJob job = jobRepository.getReferenceById(jobId);
        String modelImage = imageStorage.readAsDataUri(job.getModelImageKey());
        Clothes product = getProductClothes(job, job.getCurrentStep());
        String productImage = imageStorage.readAsDataUri(product.getImageUrl());
        return new DispatchTarget(modelImage, productImage);
    }

    /** FASHN에 요청을 보낸 직후, 받은 prediction id를 저장하고 상태를 PROCESSING으로 바꾼다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRequested(UUID jobId, String predictionId) {
        jobRepository.getReferenceById(jobId).markRequested(predictionId);
    }

    /** 폴링에 필요한 생성 시각(타임아웃 판단용)과 prediction id를 꺼낸다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PollTarget loadPollTarget(UUID jobId) {
        VirtualTryOnJob job = jobRepository.getReferenceById(jobId);
        return new PollTarget(job.getCreatedAt(), job.getFashnPredictionId());
    }

    /** FASHN이 완료한 결과를 현재 단계에 맞게 처리한다. 단계별로 다음 동작(캐싱/다음 단계/완료)이 다르다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyResult(UUID jobId, String resultUrl) {
        VirtualTryOnJob job = jobRepository.getReferenceById(jobId);
        switch (job.getCurrentStep()) {
            case TOP -> handleTopResult(job, resultUrl);
            case BOTTOM -> completeRoot(job, resultUrl);
            case ADDITIONAL -> completeAdditional(job, resultUrl);
            case DONE -> throw new BusinessException(VirtualTryOnErrorCode.INVALID_JOB_STATE)
                .addDetail("jobId", jobId.toString())
                .addDetail("reason", "PROCESSING job has step=DONE");
        }
    }

    /** job을 실패 처리하고 완료(실패) 이벤트를 발행한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failJob(UUID jobId) {
        VirtualTryOnJob job = jobRepository.getReferenceById(jobId);
        job.fail();
        publishCompleted(job);
    }

    /** TOP 결과 처리 — 캐시 재사용 기반으로 시작한 job이면 BOTTOM 없이 바로 완료 처리, 아니면 캐싱 없이 BOTTOM으로 넘어간다. */
    private void handleTopResult(VirtualTryOnJob job, String resultUrl) {
        if (job.getReuseBaseCache() != null) {
            completeRoot(job, resultUrl);
        } else {
            job.advanceWithoutCaching(VirtualTryOnStep.BOTTOM, resultUrl);
        }
    }

    /** 상의+하의 조합 결과를 캐시로 저장한다. 추가 의상이 없으면 바로 완료, 있으면 ADDITIONAL 단계로 넘긴다. */
    private void completeRoot(VirtualTryOnJob job, String resultUrl) {
        VirtualTryOnCache cache = saveCacheOrGetExisting(job, resultUrl, null);
        job.rootReady(cache);
        if (job.getStatus() == VirtualTryOnJobStatus.SUCCEEDED) {
            publishCompleted(job);
        }
    }

    /** 추가 의상까지 합친 최종 결과를 캐시로 저장하고 job을 완료 처리한다. */
    private void completeAdditional(VirtualTryOnJob job, String resultUrl) {
        VirtualTryOnCache cache = saveCacheOrGetExisting(job, resultUrl, job.getAdditionalClothes());
        job.succeed(cache);
        publishCompleted(job);
    }

    /**
     * 결과 이미지를 저장소에 올리고 캐시 행을 만든다.
     * 동시에 같은 조합의 캐시를 만들려던 다른 job이 먼저 커밋했다면(cache_key 유니크 제약 위반),
     * 방금 올린 이미지는 지우고 기존 캐시를 그대로 재사용한다.
     */
    private VirtualTryOnCache saveCacheOrGetExisting(VirtualTryOnJob job, String resultUrl, Clothes additional) {
        String resultImageKey = imageStorage.storeFromUrl(resultUrl, "virtual-try-on/results");
        String cacheKey = VirtualTryOnCacheKeyGenerator.generate(job.getModelHash(),
            job.getTopClothes().getId(), job.getBottomClothes().getId(),
            additional != null ? additional.getId() : null);
        try {
            VirtualTryOnCache cache = VirtualTryOnCache.create(
                job.getRequester(), cacheKey, job.getModelHash(),
                job.getTopClothes(), job.getBottomClothes(), additional,
                job.getReuseBaseCache(), resultImageKey);
            return cacheWriter.save(cache);
        } catch (DataIntegrityViolationException e) {
            imageStorage.delete(resultImageKey);
            return cacheRepository.findByCacheKey(cacheKey).orElseThrow(() -> e);
        }
    }

    /** job의 최종 상태(성공/실패)에 맞는 완료 이벤트를 발행한다. */
    private void publishCompleted(VirtualTryOnJob job) {
//        long elapsedMs = Duration.between(job.getCreatedAt(), Instant.now()).toMillis();
//        log.info("virtual_try_on_completed jobId={} status={} elapsedMs={}",
//            job.getId(), job.getStatus(), elapsedMs);
        VirtualTryOnCompletedEvent event = job.getStatus() == VirtualTryOnJobStatus.SUCCEEDED
            ? VirtualTryOnCompletedEvent.succeeded(job.getRequester().getId(), job.getId())
            : VirtualTryOnCompletedEvent.failed(job.getRequester().getId(), job.getId());
        eventPublisher.publishEvent(event);
    }

    /** 현재 단계(TOP/BOTTOM/ADDITIONAL)에 해당하는 옷을 꺼낸다. */
    private Clothes getProductClothes(VirtualTryOnJob job, VirtualTryOnStep step) {
        return switch (step) {
            case TOP -> job.getTopClothes();
            case BOTTOM -> job.getBottomClothes();
            case ADDITIONAL -> job.getAdditionalClothes();
            case DONE -> throw new BusinessException(VirtualTryOnErrorCode.INVALID_JOB_STATE)
                .addDetail("jobId", job.getId().toString())
                .addDetail("reason", "Cannot resolve product clothes for step=DONE");
        };
    }

    /** FASHN 요청에 필요한 모델 이미지 + 상품 이미지 쌍. */
    public record DispatchTarget(String modelImage, String productImage) {}

    /** 폴링에 필요한 job 생성 시각 + FASHN prediction id. */
    public record PollTarget(Instant createdAt, String predictionId) {}
}
