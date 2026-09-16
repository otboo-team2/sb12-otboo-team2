package com.otboo.virtualtryon.dto;

import com.otboo.virtualtryon.entity.VirtualTryOnJob;
import com.otboo.virtualtryon.entity.VirtualTryOnJobStatus;
import java.util.UUID;

public record VirtualTryOnJobResponse(UUID jobId, VirtualTryOnJobStatus status, String resultImageUrl) {
    public static VirtualTryOnJobResponse from(VirtualTryOnJob job) {
        String resultUrl = job.getResultCache() != null ? job.getResultCache().getResultImageKey() : null;
        return new VirtualTryOnJobResponse(job.getId(), job.getStatus(), resultUrl);
    }
}
