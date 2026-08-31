package com.otboo.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 도메인에 속하지 않는 공통 에러 코드.
 *
 * <p>도메인 고유 사유는 각 파트의 enum 에 정의한다. 여기에 추가하지 말 것.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    // 400 — 검증 / 잘못된 요청
    INVALID_INPUT_VALUE("COMMON_001", HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    MISSING_PARAMETER("COMMON_002", HttpStatus.BAD_REQUEST, "필수 파라미터가 없습니다."),
    TYPE_MISMATCH("COMMON_003", HttpStatus.BAD_REQUEST, "파라미터 타입이 올바르지 않습니다."),
    INVALID_CURSOR("COMMON_004", HttpStatus.BAD_REQUEST, "커서 값이 올바르지 않습니다."),

    // 401 / 403 — 인증 · 인가
    UNAUTHORIZED("COMMON_100", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN("COMMON_101", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // 404
    RESOURCE_NOT_FOUND("COMMON_200", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),

    // 405 / 415
    METHOD_NOT_ALLOWED("COMMON_005", HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE("COMMON_006", HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 형식입니다."),

    // 5xx — 서버 · 외부 연동
    INTERNAL_ERROR("COMMON_900", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    EXTERNAL_API_ERROR("COMMON_901", HttpStatus.BAD_GATEWAY, "외부 서비스 호출에 실패했습니다."),
    EXTERNAL_API_TIMEOUT("COMMON_902", HttpStatus.GATEWAY_TIMEOUT, "외부 서비스 응답이 지연되었습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
