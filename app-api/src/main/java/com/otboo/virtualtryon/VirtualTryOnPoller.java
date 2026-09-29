package com.otboo.virtualtryon;

import com.otboo.virtualtryon.client.FashnClient;
import com.otboo.virtualtryon.client.FashnStatusResponse;
import com.otboo.virtualtryon.VirtualTryOnJobTransactionService.DispatchTarget;
import com.otboo.virtualtryon.VirtualTryOnJobTransactionService.PollTarget;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VirtualTryOnPoller {

    private static final int BATCH_SIZE = 10;
    private static final Duration MAX_PROCESSING_TIME = Duration.ofMinutes(10);

    private final VirtualTryOnJobTransactionService transactionService;
    private final FashnClient fashnClient;

    @Scheduled(fixedRateString = "${otboo.virtual-try-on.dispatch-interval:5000}")
    public void dispatchPendingJobs() {
        transactionService.claimPendingJobs(BATCH_SIZE).forEach(this::dispatchSafely);
    }

    /** 이벤트로 들어온 job 을 바로 보낸다. 스케줄러가 먼저 가져갔으면 아무것도 안 한다. */
    public void dispatchNow(UUID jobId) {
        if (transactionService.claimJob(jobId)) {
            dispatchSafely(jobId);
        }
    }

    private void dispatchSafely(UUID jobId) {
        try {
            dispatch(jobId);
        } catch (Exception e) {
            log.error("virtual_try_on_dispatch_failed jobId={}", jobId, e);
            transactionService.failJob(jobId);
        }
    }

    private void dispatch(UUID jobId) {
        DispatchTarget target = transactionService.loadDispatchTarget(jobId);
        String predictionId = fashnClient.predict(target.modelImage(), target.productImage());
        transactionService.markRequested(jobId, predictionId);
    }

    @Scheduled(fixedRateString = "${otboo.virtual-try-on.poll-interval:5000}")
    public void pollProcessingJobs() {
        transactionService.findProcessingJobIds().forEach(this::pollSafely);
    }

    private void pollSafely(UUID jobId) {
        try {
            poll(jobId);
        } catch (Exception e) {
            log.error("virtual_try_on_poll_failed jobId={}", jobId, e);
            transactionService.failJob(jobId);
        }
    }

    private void poll(UUID jobId) {
        PollTarget target = transactionService.loadPollTarget(jobId);

        if (target.createdAt().isBefore(Instant.now().minus(MAX_PROCESSING_TIME))) {
            log.warn("virtual_try_on_timeout jobId={}", jobId);
            transactionService.failJob(jobId);
            return;
        }

        // 선점은 했지만 아직 FASHN 요청 중이거나, 웹훅이 이미 결과를 가져갔다. 다음 폴링 때 다시 본다.
        if (target.predictionId() == null) {
            return;
        }

        FashnStatusResponse status = fashnClient.getStatus(target.predictionId());
        if (status.isInProgress()) {
            return;
        }
        // 그 사이 웹훅이 먼저 가져갔으면 claim 이 false 라 아무것도 안 한다
        if (transactionService.claimResult(target.predictionId())) {
            applyStatus(jobId, status);
        }
    }

    /** FASHN 이 결과를 보내주면 바로 반영한다. 모르는 prediction 이거나 이미 반영한 결과면 무시한다. */
    @Async("fittingDispatchExecutor")
    public void handleWebhook(FashnStatusResponse payload) {
        if (payload.isInProgress()) {
            return;
        }
        Optional<UUID> found = transactionService.findJobIdByPredictionId(payload.id());
        if (found.isEmpty() || !transactionService.claimResult(payload.id())) {
            return;
        }
        UUID jobId = found.get();
        try {
            applyStatus(jobId, payload);
        } catch (Exception e) {
            log.error("virtual_try_on_webhook_failed jobId={}", jobId, e);
            transactionService.failJob(jobId);
        }
    }

    private void applyStatus(UUID jobId, FashnStatusResponse status) {
        if (status.isFailed()) {
            log.warn("virtual_try_on_fashn_failed jobId={} error={}", jobId, status.error());
            transactionService.failJob(jobId);
            return;
        }
        if (status.output() == null || status.output().isEmpty()) {
            log.error("virtual_try_on_completed_without_output jobId={}", jobId);
            transactionService.failJob(jobId);
            return;
        }
        transactionService.applyResult(jobId, status.output().get(0));
    }
}
