package com.otboo.common.logging;

/** 원문 message/cause/suppressed 없이 예외 타입과 발생 위치만 로그에 전달한다. */
public final class SafeExceptionLog {
    private SafeExceptionLog() {
    }

    public static RuntimeException sanitized(Throwable exception) {
        RuntimeException safe = new RuntimeException(exception.getClass().getName());
        safe.setStackTrace(exception.getStackTrace());
        return safe;
    }
}
