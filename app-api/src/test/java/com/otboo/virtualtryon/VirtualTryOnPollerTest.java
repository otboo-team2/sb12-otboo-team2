package com.otboo.virtualtryon;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.otboo.virtualtryon.VirtualTryOnJobTransactionService.DispatchTarget;
import com.otboo.virtualtryon.VirtualTryOnJobTransactionService.PollTarget;
import com.otboo.virtualtryon.client.FashnClient;
import com.otboo.virtualtryon.client.FashnStatusResponse;
import com.otboo.virtualtryon.client.FashnStatusResponse.FashnError;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    @Nested
    @DisplayName("dispatch")
    class Dispatch {

        @Test
        @DisplayName("대기 job을 FASHN에 요청하고 prediction id를 기록한다")
        void dispatchesClaimedJob() {
            UUID jobId = UUID.randomUUID();
            given(transactionService.claimPendingJobs(10)).willReturn(List.of(jobId));
            given(transactionService.loadDispatchTarget(jobId)).willReturn(new DispatchTarget("model", "product"));
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
        @DisplayName("이벤트로 들어온 job은 선점에 성공하면 바로 FASHN에 요청한다")
        void dispatchesNowWhenClaimed() {
            UUID jobId = UUID.randomUUID();
            given(transactionService.claimJob(jobId)).willReturn(true);
            given(transactionService.loadDispatchTarget(jobId)).willReturn(new DispatchTarget("model", "product"));
            given(fashnClient.predict("model", "product")).willReturn("prediction-1");

            poller.dispatchNow(jobId);

            verify(transactionService).markRequested(jobId, "prediction-1");
        }

        @Test
        @DisplayName("스케줄러가 먼저 가져간 job은 이벤트로 다시 보내지 않는다")
        void skipsDispatchNowWhenAlreadyClaimed() {
            UUID jobId = UUID.randomUUID();
            given(transactionService.claimJob(jobId)).willReturn(false);

            poller.dispatchNow(jobId);

            verify(transactionService, never()).loadDispatchTarget(jobId);
            verify(fashnClient, never()).predict(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("poll")
    class Poll {

        @Test
        @DisplayName("처리 중 응답이면 결과를 적용하거나 실패 처리하지 않는다")
        void keepsInProgressJob() {
            UUID jobId = processingJob(new FashnStatusResponse("p1", "processing", null, null));

            poller.pollProcessingJobs();

            verify(transactionService, never()).claimResult(anyString());
            verify(transactionService, never()).applyResult(eq(jobId), anyString());
            verify(transactionService, never()).failJob(jobId);
        }

        @Test
        @DisplayName("실패 응답은 job을 실패 처리한다")
        void failsRejectedJob() {
            UUID jobId = processingJob(new FashnStatusResponse(
                "p1", "failed", null, new FashnError("invalid", "bad image")));
            given(transactionService.claimResult("p1")).willReturn(true);

            poller.pollProcessingJobs();

            verify(transactionService).failJob(jobId);
        }

        @Test
        @DisplayName("완료 응답에 출력이 없으면 job을 실패 처리한다")
        void failsCompletedJobWithoutOutput() {
            UUID jobId = processingJob(new FashnStatusResponse("p1", "completed", List.of(), null));
            given(transactionService.claimResult("p1")).willReturn(true);

            poller.pollProcessingJobs();

            verify(transactionService).failJob(jobId);
        }

        @Test
        @DisplayName("완료 응답의 첫 번째 출력만 적용한다")
        void appliesCompletedResult() {
            UUID jobId = processingJob(new FashnStatusResponse(
                "p1", "completed", List.of("first", "second"), null));
            given(transactionService.claimResult("p1")).willReturn(true);

            poller.pollProcessingJobs();

            verify(transactionService).applyResult(jobId, "first");
        }

        @Test
        @DisplayName("웹훅이 먼저 결과를 가져갔으면 폴링은 반영하지 않는다")
        void skipsWhenWebhookClaimedFirst() {
            UUID jobId = processingJob(new FashnStatusResponse("p1", "completed", List.of("first"), null));
            given(transactionService.claimResult("p1")).willReturn(false);

            poller.pollProcessingJobs();

            verify(transactionService, never()).applyResult(eq(jobId), anyString());
            verify(transactionService, never()).failJob(jobId);
        }

        @Test
        @DisplayName("FASHN 요청 중이라 prediction id가 없으면 상태 조회 없이 건너뛴다")
        void skipsJobWithoutPredictionId() {
            UUID jobId = UUID.randomUUID();
            given(transactionService.findProcessingJobIds()).willReturn(List.of(jobId));
            given(transactionService.loadPollTarget(jobId)).willReturn(new PollTarget(Instant.now(), null));

            poller.pollProcessingJobs();

            verify(fashnClient, never()).getStatus(any());
            verify(transactionService, never()).failJob(jobId);
        }

        @Test
        @DisplayName("10분을 넘긴 job은 외부 조회 없이 실패 처리한다")
        void failsTimedOutJobWithoutPollingFashn() {
            UUID jobId = UUID.randomUUID();
            given(transactionService.findProcessingJobIds()).willReturn(List.of(jobId));
            given(transactionService.loadPollTarget(jobId))
                .willReturn(new PollTarget(Instant.now().minusSeconds(11 * 60), "p1"));

            poller.pollProcessingJobs();

            verify(transactionService).failJob(jobId);
            verify(fashnClient, never()).getStatus("p1");
        }

        private UUID processingJob(FashnStatusResponse response) {
            UUID jobId = UUID.randomUUID();
            given(transactionService.findProcessingJobIds()).willReturn(List.of(jobId));
            given(transactionService.loadPollTarget(jobId)).willReturn(new PollTarget(Instant.now(), "p1"));
            given(fashnClient.getStatus("p1")).willReturn(response);
            return jobId;
        }
    }

    @Nested
    @DisplayName("webhook")
    class Webhook {

        @Test
        @DisplayName("완료 웹훅은 prediction id로 job을 찾아 첫 번째 출력을 적용한다")
        void appliesCompletedWebhook() {
            UUID jobId = UUID.randomUUID();
            given(transactionService.findJobIdByPredictionId("p1")).willReturn(Optional.of(jobId));
            given(transactionService.claimResult("p1")).willReturn(true);

            poller.handleWebhook(new FashnStatusResponse("p1", "completed", List.of("first", "second"), null));

            verify(transactionService).applyResult(jobId, "first");
        }

        @Test
        @DisplayName("실패 웹훅은 job을 실패 처리한다")
        void failsJobOnFailedWebhook() {
            UUID jobId = UUID.randomUUID();
            given(transactionService.findJobIdByPredictionId("p1")).willReturn(Optional.of(jobId));
            given(transactionService.claimResult("p1")).willReturn(true);

            poller.handleWebhook(new FashnStatusResponse("p1", "failed", null, null));

            verify(transactionService).failJob(jobId);
        }

        @Test
        @DisplayName("모르는 prediction id거나 이미 반영한 결과면 무시한다")
        void ignoresUnknownPrediction() {
            given(transactionService.findJobIdByPredictionId("p1")).willReturn(Optional.empty());

            poller.handleWebhook(new FashnStatusResponse("p1", "completed", List.of("first"), null));

            verify(transactionService, never()).claimResult(anyString());
            verify(transactionService, never()).applyResult(any(), anyString());
        }

        @Test
        @DisplayName("폴링이 먼저 가져갔거나 재전송된 웹훅이면 반영하지 않는다")
        void skipsWhenAlreadyClaimed() {
            UUID jobId = UUID.randomUUID();
            given(transactionService.findJobIdByPredictionId("p1")).willReturn(Optional.of(jobId));
            given(transactionService.claimResult("p1")).willReturn(false);

            poller.handleWebhook(new FashnStatusResponse("p1", "completed", List.of("first"), null));

            verify(transactionService, never()).applyResult(eq(jobId), anyString());
            verify(transactionService, never()).failJob(jobId);
        }

        @Test
        @DisplayName("처리 중 웹훅은 job을 조회하지 않고 무시한다")
        void ignoresInProgressWebhook() {
            poller.handleWebhook(new FashnStatusResponse("p1", "processing", null, null));

            verify(transactionService, never()).findJobIdByPredictionId(anyString());
        }
    }
}
