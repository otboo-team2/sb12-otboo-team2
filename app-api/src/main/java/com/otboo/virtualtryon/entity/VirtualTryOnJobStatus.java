package com.otboo.virtualtryon.entity;

public enum VirtualTryOnJobStatus {
    PENDING, PROCESSING, SUCCEEDED, FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
