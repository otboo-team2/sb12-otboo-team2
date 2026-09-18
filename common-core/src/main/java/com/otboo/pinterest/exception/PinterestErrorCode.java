package com.otboo.pinterest.exception;

import com.otboo.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PinterestErrorCode implements ErrorCode {

    // 400
    INVALID_SEARCH_QUERY("PINTEREST_001", HttpStatus.BAD_REQUEST, "검색어가 올바르지 않습니다."),

    // 500 — 설정 문제. 사용자가 고칠 수 없으니 메시지는 일반적으로 둔다.
    ACCESS_TOKEN_NOT_CONFIGURED("PINTEREST_901", HttpStatus.INTERNAL_SERVER_ERROR,
            "코디 이미지를 불러올 수 없습니다."),
    INVALID_BOARD_ID("PINTEREST_902", HttpStatus.INTERNAL_SERVER_ERROR,
            "코디 이미지를 불러올 수 없습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
