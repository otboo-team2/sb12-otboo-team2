package com.otboo.virtualtryon.exception;

import com.otboo.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum VirtualTryOnErrorCode implements ErrorCode {

    // 400
    UNSUPPORTED_CLOTHES_TYPE("VIRTUAL_TRY_ON_001", HttpStatus.BAD_REQUEST, "드레스는 가상 피팅을 지원하지 않습니다."),
    CLOTHES_CATEGORY_MISMATCH("VIRTUAL_TRY_ON_002", HttpStatus.BAD_REQUEST, "선택한 의상의 카테고리가 올바르지 않습니다."),

    // 404
    NOT_FOUND("VIRTUAL_TRY_ON_200", HttpStatus.NOT_FOUND, "요청을 찾을 수 없습니다."),

    // 500
    INVALID_JOB_STATE("VIRTUAL_TRY_ON_900", HttpStatus.INTERNAL_SERVER_ERROR, "가상 피팅 처리 상태가 올바르지 않습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
