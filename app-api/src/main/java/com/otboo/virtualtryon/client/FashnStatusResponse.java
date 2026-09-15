package com.otboo.virtualtryon.client;

import java.util.List;

public record FashnStatusResponse(String id, String status, List<String> output, FashnError error) {
    public boolean isCompleted() { return "completed".equals(status); }
    public boolean isFailed() { return "failed".equals(status); }
    public boolean isInProgress() { return !isCompleted() && !isFailed(); }

    public record FashnError(String name, String message) {}
}
