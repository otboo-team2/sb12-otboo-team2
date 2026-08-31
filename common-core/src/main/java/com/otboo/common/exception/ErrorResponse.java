package com.otboo.common.exception;

import java.util.Map;

/**
 * 에러 응답 본문. Swagger 스펙의 ErrorResponse 스키마와 필드가 일치해야 한다.
 *
 * <p>{@code exceptionName} 에는 예외 클래스명이 아니라 <b>에러 코드</b>를 넣는다.
 * 내부 클래스명을 그대로 노출하면 구현 정보가 새고, 프론트가 분기하기에도 코드값이 안정적이다.
 *
 * @param exceptionName 에러 코드 (예: {@code CLOTHES_201})
 * @param message       사용자에게 보여줄 메시지
 * @param details       부가 정보. 없으면 빈 맵
 */
public record ErrorResponse(
        String exceptionName,
        String message,
        Map<String, String> details
) {

    public static ErrorResponse of(ErrorCode errorCode, Map<String, String> details) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), details);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, Map<String, String> details) {
        return new ErrorResponse(errorCode.getCode(), message, details);
    }
}
