package com.otboo.clothes.extraction;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class CImageAnalysisExecutor implements AutoCloseable {

    private final ThreadPoolExecutor executor;
    private final Semaphore permits;
    private final CImageSelectionProperties properties;

    public CImageAnalysisExecutor(CImageSelectionProperties properties) {
        this.properties = properties;
        int concurrencyLimit = properties.maxConcurrentAnalyses();
        if (concurrencyLimit < 1) {
            throw new IllegalArgumentException("maxConcurrentAnalyses must be positive");
        }
        if (properties.acquireTimeout().isNegative()
                || properties.analysisTimeout().isNegative()
                || properties.analysisTimeout().isZero()) {
            throw new IllegalArgumentException("C image analysis timeouts are invalid");
        }

        permits = new Semaphore(concurrencyLimit);
        ThreadFactory threadFactory = new AnalysisThreadFactory();
        executor = new ThreadPoolExecutor(
                concurrencyLimit,
                concurrencyLimit,
                0,
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    public <T> T execute(Callable<T> task) {
        if (executor.isShutdown()) {
            throw new CImageAnalysisException(CImageAnalysisException.Reason.SHUTDOWN);
        }

        try {
            if (!permits.tryAcquire(
                    properties.acquireTimeout().toNanos(), TimeUnit.NANOSECONDS)) {
                throw new CImageAnalysisException(CImageAnalysisException.Reason.BUSY);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CImageAnalysisException(
                    CImageAnalysisException.Reason.INTERRUPTED,
                    exception);
        }

        FutureTask<T> taskFuture = new FutureTask<>(task);
        try {
            executor.execute(() -> {
                try {
                    taskFuture.run();
                } finally {
                    permits.release();
                }
            });
        } catch (RejectedExecutionException exception) {
            permits.release();
            CImageAnalysisException.Reason reason = executor.isShutdown()
                    ? CImageAnalysisException.Reason.SHUTDOWN
                    : CImageAnalysisException.Reason.BUSY;
            throw new CImageAnalysisException(reason, exception);
        }

        try {
            return taskFuture.get(
                    properties.analysisTimeout().toNanos(),
                    TimeUnit.NANOSECONDS);
        } catch (TimeoutException exception) {
            taskFuture.cancel(true);
            throw new CImageAnalysisException(CImageAnalysisException.Reason.TIMEOUT, exception);
        } catch (InterruptedException exception) {
            taskFuture.cancel(true);
            Thread.currentThread().interrupt();
            throw new CImageAnalysisException(
                    CImageAnalysisException.Reason.INTERRUPTED,
                    exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new CImageAnalysisException(
                    CImageAnalysisException.Reason.ANALYSIS_ERROR,
                    cause);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }

    private static class AnalysisThreadFactory implements ThreadFactory {

        private final AtomicInteger threadNumber = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            return new Thread(task, "clothes-c-image-analysis-" + threadNumber.incrementAndGet());
        }
    }
}
