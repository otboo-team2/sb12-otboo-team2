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
            // log.error("virtual_try_on_dispatch_failed jobId={}", jobId, e);
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
            // log.error("virtual_try_on_poll_failed jobId={}", jobId, e);
            transactionService.failJob(jobId);
        }
    }

    private void poll(UUID jobId) {
        PollTarget target = transactionService.loadPollTarget(jobId);

        if (target.createdAt().isBefore(Instant.now().minus(MAX_PROCESSING_TIME))) {
            // log.warn("virtual_try_on_timeout jobId={}", jobId);
            transactionService.failJob(jobId);
            return;
        }

        FashnStatusResponse status = fashnClient.getStatus(target.predictionId());
        if (status.isInProgress()) {
            return;
        }
        if (status.isFailed()) {
            // log.warn("fashn_failed jobId={} name={} message={}",
            //     jobId, status.error().name(), status.error().message());
            transactionService.failJob(jobId);
            return;
        }
        if (status.output() == null || status.output().isEmpty()) {
            // log.error("virtual_try_on_completed_without_output jobId={}", jobId);
            transactionService.failJob(jobId);
            return;
        }

        transactionService.applyResult(jobId, status.output().get(0));
    }
}
