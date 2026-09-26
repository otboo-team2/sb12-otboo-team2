package com.otboo.clothes.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CImageAnalysisExecutorTest {

    private final ExecutorService callers = Executors.newSingleThreadExecutor();
    private final CImageAnalysisExecutor executor = new CImageAnalysisExecutor(
            properties(Duration.ofMillis(100), Duration.ofSeconds(2)));

    @AfterEach
    void shutDownExecutors() {
        executor.close();
        callers.shutdownNow();
    }

    @Test
    void rejectsASecondAnalysisWhenTheSinglePermitIsBusy() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        Future<String> first = callers.submit(() -> executor.execute(() -> {
            firstStarted.countDown();
            releaseFirst.await();
            return "finished";
        }));

        try {
            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> executor.execute(() -> "second"))
                    .isInstanceOfSatisfying(CImageAnalysisException.class, exception ->
                            assertThat(exception.reason())
                                    .isEqualTo(CImageAnalysisException.Reason.BUSY));
        } finally {
            releaseFirst.countDown();
        }

        assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo("finished");
    }

    @Test
    void keepsPermitUntilTimedOutWorkerActuallyFinishes() throws Exception {
        CImageAnalysisExecutor shortTimeoutExecutor = new CImageAnalysisExecutor(
                properties(Duration.ofMillis(100), Duration.ofMillis(100)));
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch firstWorkerFinished = new CountDownLatch(1);

        Future<CImageAnalysisException> first = callers.submit(() -> captureFailure(
                shortTimeoutExecutor,
                () -> {
                    firstStarted.countDown();
                    awaitIgnoringInterrupt(releaseFirst);
                    firstWorkerFinished.countDown();
                    return "finished";
                }));

        try {
            assertThat(firstStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(first.get(2, TimeUnit.SECONDS).reason())
                    .isEqualTo(CImageAnalysisException.Reason.TIMEOUT);

            assertThatThrownBy(() -> shortTimeoutExecutor.execute(() -> "second"))
                    .isInstanceOfSatisfying(CImageAnalysisException.class, exception ->
                            assertThat(exception.reason())
                                    .isEqualTo(CImageAnalysisException.Reason.BUSY));
        } finally {
            releaseFirst.countDown();
            shortTimeoutExecutor.close();
        }

        assertThat(firstWorkerFinished.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void rejectsNewAnalysesAfterShutdown() {
        executor.close();
        executor.close();

        assertThatThrownBy(() -> executor.execute(() -> "not-run"))
                .isInstanceOfSatisfying(CImageAnalysisException.class, exception ->
                        assertThat(exception.reason())
                                .isEqualTo(CImageAnalysisException.Reason.SHUTDOWN));
    }

    private static CImageAnalysisException captureFailure(
            CImageAnalysisExecutor executor,
            java.util.concurrent.Callable<String> task
    ) throws Exception {
        try {
            executor.execute(task);
        } catch (CImageAnalysisException exception) {
            return exception;
        }
        throw new AssertionError("Expected the analysis to fail");
    }

    private static void awaitIgnoringInterrupt(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException ignored) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static CImageSelectionProperties properties(
            Duration acquireTimeout,
            Duration analysisTimeout
    ) {
        return new CImageSelectionProperties(
                CImageSelectionProperties.Mode.B0,
                null,
                "",
                Path.of(System.getProperty("java.io.tmpdir")),
                8,
                100L * 1024 * 1024,
                200_000_000L,
                1,
                acquireTimeout,
                analysisTimeout);
    }
}
