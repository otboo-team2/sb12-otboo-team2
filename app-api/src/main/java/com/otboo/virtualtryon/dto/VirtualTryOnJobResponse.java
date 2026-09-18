package com.otboo.virtualtryon.dto;

import com.otboo.common.storage.ImageStorage;
import com.otboo.virtualtryon.entity.VirtualTryOnJob;
import com.otboo.virtualtryon.entity.VirtualTryOnJobStatus;
import java.util.UUID;

public record VirtualTryOnJobResponse(
    UUID jobId,
    VirtualTryOnJobStatus status,
    String resultImageUrl,
    String failureReason,
    boolean retryable
) {
    public static VirtualTryOnJobResponse from(VirtualTryOnJob job, ImageStorage imageStorage) {
        String resultKey = job.getResultCache() != null ? job.getResultCache().getResultImageKey() : null;
        String resultUrl = resultKey != null ? imageStorage.resolveUrl(resultKey) : null;
        boolean failed = job.getStatus() == VirtualTryOnJobStatus.FAILED;
        return new VirtualTryOnJobResponse(
            job.getId(), job.getStatus(), resultUrl,
            failed ? "일시적인 오류로 처리에 실패했습니다. 다시 시도해주세요." : null,
            failed);
    }
}
