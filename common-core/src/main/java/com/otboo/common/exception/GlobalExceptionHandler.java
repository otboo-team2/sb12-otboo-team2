package com.otboo.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리. 이 클래스 하나로 응답 형식을 통일한다.
 *
 * <p>각 파트에서 별도의 {@code @RestControllerAdvice} 를 만들지 말 것.
 * 도메인 사유는 {@link ErrorCode} enum 을 추가하는 것으로 표현한다.
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnWebApplication  // 배치 앱(web-application-type: none)에서는 등록하지 않는다
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        // 5xx 만 스택트레이스를 남긴다. 4xx 는 정상적인 사용자 오류라 로그를 오염시킨다.
        if (errorCode.getStatus().is5xxServerError()) {
            log.error("[{}] {}", errorCode.getCode(), e.getMessage(), e);
        } else {
            log.warn("[{}] {} {}", errorCode.getCode(), e.getMessage(), e.getDetails());
        }
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, e.getMessage(), e.getDetails()));
    }

    /** {@code @Valid} 검증 실패 — 어떤 필드가 왜 틀렸는지 details 에 담는다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        Map<String, String> details = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> details.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return build(CommonErrorCode.INVALID_INPUT_VALUE, details);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException e) {
        return build(CommonErrorCode.MISSING_PARAMETER, Map.of("parameter", e.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return build(CommonErrorCode.TYPE_MISMATCH, Map.of("parameter", e.getName()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException e) {
        return build(CommonErrorCode.INVALID_INPUT_VALUE, Map.of("reason", "요청 본문을 읽을 수 없습니다."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return build(CommonErrorCode.METHOD_NOT_ALLOWED, Map.of("method", String.valueOf(e.getMethod())));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return build(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE,
                Map.of("contentType", String.valueOf(e.getContentType())));
    }

    /** 매칭되는 핸들러가 없는 경로. catch-all 로 흘러가면 404 가 500 으로 나간다. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException e) {
        return build(CommonErrorCode.RESOURCE_NOT_FOUND, Map.of("path", e.getResourcePath()));
    }

    /**
     * 마지막 방어선. 예상 못한 예외의 내부 메시지를 그대로 내보내면 구현 정보가 샌다.
     * 상세는 로그에만 남기고 응답에는 고정 메시지를 준다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception e) {
        log.error("[{}] 처리되지 않은 예외", CommonErrorCode.INTERNAL_ERROR.getCode(), e);
        return build(CommonErrorCode.INTERNAL_ERROR, Map.of());
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode errorCode, Map<String, String> details) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, details));
    }
}
