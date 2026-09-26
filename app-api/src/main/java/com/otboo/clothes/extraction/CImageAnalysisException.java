package com.otboo.clothes.extraction;

public class CImageAnalysisException extends RuntimeException {

    private final Reason reason;

    public CImageAnalysisException(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public CImageAnalysisException(Reason reason, Throwable cause) {
        super(reason.name(), cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        BUSY,
        TIMEOUT,
        MODEL_ERROR,
        DECODE_ERROR,
        SCAN_LIMIT,
        INTERRUPTED,
        SHUTDOWN,
        ANALYSIS_ERROR
    }
}
