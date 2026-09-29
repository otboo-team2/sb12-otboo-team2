package com.otboo.virtualtryon;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.otboo.virtualtryon.VirtualTryOnJobTransactionService.DispatchTarget;
import com.otboo.virtualtryon.VirtualTryOnJobTransactionService.PollTarget;
import com.otboo.virtualtryon.client.FashnClient;
import com.otboo.virtualtryon.client.FashnStatusResponse;
import com.otboo.virtualtryon.client.FashnStatusResponse.FashnError;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.otboo.virtualtryon.entity.VirtualTryOnStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VirtualTryOnPollerTest {

    @Mock
    VirtualTryOnJobTransactionService transactionService;

    @Mock
    FashnClient fashnClient;

    private VirtualTryOnPoller poller;

    @BeforeEach
    void setUp() {
        poller = new VirtualTryOnPoller(transactionService, fashnClient);
    }

    @Test
    @DisplayName("대기 job을 FASHN에 요청하고 prediction id를 기록한다")
    void dispatchesClaimedJob() {
        UUID jobId = UUID.randomUUID();
        given(transactionService.claimPendingJobs(10)).willReturn(List.of(jobId));
        given(transactionService.loadDispatchTarget(jobId))
            .willReturn(new DispatchTarget("model", "product", VirtualTryOnStep.TOP, Instant.now()));
        given(fashnClient.predict("model", "product")).willReturn("prediction-1");

        poller.dispatchPendingJobs();

        verify(transactionService).markRequested(jobId, "prediction-1");
    }

    @Test
    @DisplayName("FASHN 요청 예외는 스케줄러를 죽이지 않고 해당 job만 실패 처리한다")
    void failsJobWhenDispatchThrows() {
        UUID jobId = UUID.randomUUID();
        given(transactionService.claimPendingJobs(10)).willReturn(List.of(jobId));
        given(transactionService.loadDispatchTarget(jobId)).willThrow(new RuntimeException("boom"));

        poller.dispatchPendingJobs();

        verify(transactionService).failJob(jobId);
    }

    @Test
    @DisplayName("처리 중 응답이면 결과를 적용하거나 실패 처리하지 않는다")
    void keepsInProgressJob() {
        UUID jobId = processingJob(new FashnStatusResponse("p1", "processing", null, null));

        poller.pollProcessingJobs();

        verify(transactionService, never()).applyResult(org.mockito.ArgumentMatchers.eq(jobId),
                org.mockito.ArgumentMatchers.anyString());
        verify(transactionService, never()).failJob(jobId);
    }

    @Test
    @DisplayName("실패 응답은 job을 실패 처리한다")
    void failsRejectedJob() {
        UUID jobId = processingJob(new FashnStatusResponse(
                "p1", "failed", null, new FashnError("invalid", "bad image")));

        poller.pollProcessingJobs();

        verify(transactionService).failJob(jobId);
    }

    @Test
    @DisplayName("완료 응답에 출력이 없으면 job을 실패 처리한다")
    void failsCompletedJobWithoutOutput() {
        UUID jobId = processingJob(new FashnStatusResponse("p1", "completed", List.of(), null));

        poller.pollProcessingJobs();

        verify(transactionService).failJob(jobId);
    }

    @Test
    @DisplayName("완료 응답의 첫 번째 출력만 적용한다")
    void appliesCompletedResult() {
        UUID jobId = processingJob(new FashnStatusResponse(
                "p1", "completed", List.of("first", "second"), null));

        poller.pollProcessingJobs();

        verify(transactionService).applyResult(jobId, "first");
    }

    @Test
    @DisplayName("10분을 넘긴 job은 외부 조회 없이 실패 처리한다")
    void failsTimedOutJobWithoutPollingFashn() {
        UUID jobId = UUID.randomUUID();
        given(transactionService.findProcessingJobIds()).willReturn(List.of(jobId));
        given(transactionService.loadPollTarget(jobId))
            .willReturn(new PollTarget(Instant.now().minusSeconds(11 * 60), "p1", VirtualTryOnStep.TOP, Instant.now()));

        poller.pollProcessingJobs();

        verify(transactionService).failJob(jobId);
        verify(fashnClient, never()).getStatus("p1");
    }

    private UUID processingJob(FashnStatusResponse response) {
        UUID jobId = UUID.randomUUID();
        given(transactionService.findProcessingJobIds()).willReturn(List.of(jobId));
        given(transactionService.loadPollTarget(jobId))
            .willReturn(new PollTarget(Instant.now(), "p1", VirtualTryOnStep.TOP, Instant.now()));
        given(fashnClient.getStatus("p1")).willReturn(response);
        // 결과 반영 권한을 폴링이 가져간 상황. 처리 중 테스트에서는 호출되지 않으므로 lenient
        lenient().when(transactionService.claimResult("p1")).thenReturn(true);
        return jobId;
    }

    @Test
    @DisplayName("FASHN 요청 중이라 prediction id가 없으면 상태 조회 없이 건너뛴다")
    void skipsJobWithoutPredictionId() {
        UUID jobId = UUID.randomUUID();
        given(transactionService.findProcessingJobIds()).willReturn(List.of(jobId));
        given(transactionService.loadPollTarget(jobId))
            .willReturn(new PollTarget(Instant.now(), null, VirtualTryOnStep.TOP, Instant.now()));

        poller.pollProcessingJobs();

        verify(fashnClient, never()).getStatus(org.mockito.ArgumentMatchers.any());
        verify(transactionService, never()).failJob(jobId);
    }

    @Test
    @DisplayName("웹훅이 먼저 결과를 가져갔으면 폴링은 반영하지 않는다")
    void skipsWhenWebhookClaimedFirst() {
        UUID jobId = processingJob(new FashnStatusResponse("p1", "completed", List.of("first"), null));
        given(transactionService.claimResult("p1")).willReturn(false);

        poller.pollProcessingJobs();

        verify(transactionService, never()).applyResult(org.mockito.ArgumentMatchers.eq(jobId),
            org.mockito.ArgumentMatchers.anyString());
    }
}
