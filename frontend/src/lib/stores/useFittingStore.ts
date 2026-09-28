import { create } from 'zustand';
import { getFittingJob } from '@/lib/api/fittings';
import type { VirtualTryOnJobDto } from '@/lib/api/types';

const POLL_INTERVAL_MS = 4000;
const MAX_POLL_DURATION_MS = 11 * 60 * 1000;
const STORAGE_KEY = 'otboo:virtual-fitting:job';

const TIMEOUT_MESSAGE = '처리 시간이 너무 오래 걸리고 있습니다. 잠시 후 다시 시도해주세요.';
const POLL_ERROR_MESSAGE = '상태 확인 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.';

function isTerminal(status: VirtualTryOnJobDto['status']): boolean {
    return status === 'SUCCEEDED' || status === 'FAILED';
}

function keepsAfterFinish(status: VirtualTryOnJobDto['status']): boolean {
    return status === 'SUCCEEDED';
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

export function hasStoredFittingJob(): boolean {
    return loadStoredJob() !== null;
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
let generation = 0;

export const useFittingStore = create<FittingState>((set, get) => {
    const stopPolling = () => {
        generation++;
        if (intervalId) {
            clearInterval(intervalId);
            intervalId = null;
        }
        set({ polling: false });
    };

    const failCurrent = (failureReason: string) => {
        const current = get().job;
        if (current) {
            set({ job: { ...current, status: 'FAILED', failureReason, retryable: true } });
        }
    };

    const beginPolling = (jobId: string) => {
        if (intervalId) clearInterval(intervalId);
        const myGeneration = generation;
        set({ polling: true });

        intervalId = setInterval(async () => {
            if (myGeneration !== generation) return;

            if (Date.now() - startedAt > MAX_POLL_DURATION_MS) {
                stopPolling();
                clearStoredJob();
                failCurrent(TIMEOUT_MESSAGE);
                return;
            }

            try {
                const latest = await getFittingJob(jobId);
                if (myGeneration !== generation) return;

                set({ job: latest });
                if (isTerminal(latest.status)) {
                    stopPolling();
                    if (!keepsAfterFinish(latest.status)) clearStoredJob();
                }
            } catch (error) {
                if (myGeneration !== generation) return;

                console.error('가상피팅 상태 조회 실패:', error);
                stopPolling();
                clearStoredJob();
                failCurrent(POLL_ERROR_MESSAGE);
            }
        }, POLL_INTERVAL_MS);
    };

    return {
        job: null,
        polling: false,

        start: (initialJob) => {
            stopPolling();
            set({ job: initialJob });
            startedAt = Date.now();

            if (isTerminal(initialJob.status)) {
                // 캐시 완전 일치면 요청 즉시 SUCCEEDED 로 온다
                if (keepsAfterFinish(initialJob.status)) {
                    saveStoredJob({ jobId: initialJob.jobId, startedAt });
                } else {
                    clearStoredJob();
                }
                return;
            }

            saveStoredJob({ jobId: initialJob.jobId, startedAt });
            beginPolling(initialJob.jobId);
        },

        resume: () => {
            const stored = loadStoredJob();
            if (!stored) return;

            stopPolling();
            startedAt = stored.startedAt;
            const myGeneration = generation;

            getFittingJob(stored.jobId)
                .then((latest) => {
                    if (myGeneration !== generation) return;

                    set({ job: latest });
                    if (isTerminal(latest.status)) {
                        if (!keepsAfterFinish(latest.status)) clearStoredJob();
                        return;
                    }
                    if (Date.now() - startedAt > MAX_POLL_DURATION_MS) {
                        clearStoredJob();
                        failCurrent(TIMEOUT_MESSAGE);
                        return;
                    }
                    beginPolling(stored.jobId);
                })
                .catch((error) => {
                    if (myGeneration !== generation) return;

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
