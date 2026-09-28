import { create } from 'zustand';
import { getFittingJob } from '@/lib/api/fittings';
import type { VirtualTryOnJobDto } from '@/lib/api/types';

const POLL_INTERVAL_MS = 4000;
const MAX_POLL_DURATION_MS = 11 * 60 * 1000;
const STORAGE_KEY = 'otboo:virtual-fitting:job';

function isTerminal(status: VirtualTryOnJobDto['status']): boolean {
    return status === 'SUCCEEDED' || status === 'FAILED';
}

interface StoredJob {
    jobId: string;
    startedAt: number;
}

function saveStoredJob(stored: StoredJob) {
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
    } catch {
        // 저장 실패해도 폴링 자체는 계속 동작해야 하니 무시한다
    }
}

function loadStoredJob(): StoredJob | null {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        return raw ? (JSON.parse(raw) as StoredJob) : null;
    } catch {
        return null;
    }
}

function clearStoredJob() {
    try {
        localStorage.removeItem(STORAGE_KEY);
    } catch {
        // ignore
    }
}

interface FittingState {
    job: VirtualTryOnJobDto | null;
    polling: boolean;
    start: (job: VirtualTryOnJobDto) => void;
    resume: () => void;
    reset: () => void;
}

let intervalId: ReturnType<typeof setInterval> | null = null;
let startedAt = 0;

export const useFittingStore = create<FittingState>((set, get) => {
    const stopPolling = () => {
        if (intervalId) {
            clearInterval(intervalId);
            intervalId = null;
        }
        set({ polling: false });
    };

    const beginPolling = (jobId: string) => {
        intervalId = setInterval(async () => {
            if (Date.now() - startedAt > MAX_POLL_DURATION_MS) {
                stopPolling();
                clearStoredJob();
                const current = get().job;
                if (current) {
                    set({
                        job: {
                            ...current,
                            status: 'FAILED',
                            failureReason: '처리 시간이 너무 오래 걸리고 있습니다. 잠시 후 다시 시도해주세요.',
                            retryable: true,
                        },
                    });
                }
                return;
            }
            try {
                const latest = await getFittingJob(jobId);
                set({ job: latest });
                if (isTerminal(latest.status)) {
                    stopPolling();
                    clearStoredJob();
                }
                // 수정
            } catch (error) {
                console.error('가상피팅 상태 조회 실패:', error);
                stopPolling();
                clearStoredJob();
                const current = get().job;
                if (current) {
                    set({
                        job: {
                            ...current,
                            status: 'FAILED',
                            failureReason: '상태 확인 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.',
                            retryable: true,
                        },
                    });
                }
            }
        }, POLL_INTERVAL_MS);
    };

    return {
        job: null,
        polling: false,
        start: (initialJob) => {
            stopPolling();
            set({ job: initialJob });
            if (isTerminal(initialJob.status)) {
                clearStoredJob();
                return;
            }
            startedAt = Date.now();
            saveStoredJob({ jobId: initialJob.jobId, startedAt });
            set({ polling: true });
            beginPolling(initialJob.jobId);
        },
        resume: () => {
            const stored = loadStoredJob();
            if (!stored) return;
            stopPolling();
            startedAt = stored.startedAt;

            getFittingJob(stored.jobId)
                .then((latest) => {
                    set({ job: latest });
                    if (isTerminal(latest.status)) {
                        clearStoredJob();
                        return;
                    }
                    if (Date.now() - startedAt > MAX_POLL_DURATION_MS) {
                        clearStoredJob();
                        set({
                            job: {
                                ...latest,
                                status: 'FAILED',
                                failureReason: '처리 시간이 너무 오래 걸리고 있습니다. 잠시 후 다시 시도해주세요.',
                                retryable: true,
                            },
                        });
                        return;
                    }
                    set({ polling: true });
                    beginPolling(stored.jobId);
                })
                .catch((error) => {
                    console.error('가상피팅 상태 복구 실패:', error);
                    clearStoredJob();
                });
        },
        reset: () => {
            stopPolling();
            clearStoredJob();
            set({ job: null });
        },
    };
});
