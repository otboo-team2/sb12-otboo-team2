package com.otboo.virtualtryon;

import com.otboo.virtualtryon.client.FashnClient;
import com.otboo.virtualtryon.client.FashnStatusResponse;
import com.otboo.virtualtryon.VirtualTryOnJobTransactionService.DispatchTarget;
import com.otboo.virtualtryon.VirtualTryOnJobTransactionService.PollTarget;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        long waitedMs = Duration.between(target.pendingSince(), Instant.now()).toMillis();

        long requestStartedAt = System.nanoTime();
        String predictionId = fashnClient.predict(target.modelImage(), target.productImage());
        long requestMs = (System.nanoTime() - requestStartedAt) / 1_000_000;

        transactionService.markRequested(jobId, predictionId);
        log.info("virtual_try_on_dispatched jobId={} step={} dispatchWaitMs={} fashnRequestMs={} modelImageKb={} productImageKb={}",
            jobId, target.step(), waitedMs, requestMs,
            target.modelImage().length() / 1024, target.productImage().length() / 1024);
    }

    /** 이벤트로 들어온 job 을 바로 보낸다. 스케줄러가 먼저 가져갔으면 아무것도 안 한다. */
    public void dispatchNow(UUID jobId) {
        if (!transactionService.claimJob(jobId)) {
            return;
        }
        dispatchSafely(jobId);
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

        // 선점은 했지만 아직 FASHN 요청 중이라 prediction id 가 없다. 다음 폴링 때 다시 본다.
        if (target.predictionId() == null) {
            return;
        }

        FashnStatusResponse status = fashnClient.getStatus(target.predictionId());
        if (status.isInProgress()) {
            return;
        }

        long fashnPlusPollMs = Duration.between(target.requestedAt(), Instant.now()).toMillis();

        if (status.isFailed()) {
            log.warn("virtual_try_on_fashn_done jobId={} step={} result=FAILED fashnPlusPollMs={} name={} message={}",
                jobId, target.step(), fashnPlusPollMs, status.error().name(), status.error().message());
            transactionService.failJob(jobId);
            return;
        }
        if (status.output() == null || status.output().isEmpty()) {
            log.error("virtual_try_on_completed_without_output jobId={}", jobId);
            transactionService.failJob(jobId);
            return;
        }

        long applyStartedAt = System.nanoTime();
        transactionService.applyResult(jobId, status.output().get(0));
        long applyMs = (System.nanoTime() - applyStartedAt) / 1_000_000;

        log.info("virtual_try_on_fashn_done jobId={} step={} result=COMPLETED fashnPlusPollMs={} applyMs={}",
            jobId, target.step(), fashnPlusPollMs, applyMs);
    }
}
