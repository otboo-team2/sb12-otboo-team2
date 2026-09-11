package com.otboo.auth.exception;

import com.otboo.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    // 401 — 로그인 실패 사유를 나누지 않는다. 나누면 어떤 이메일이 가입돼 있는지 알려주는 셈이다.
    INVALID_CREDENTIALS("AUTH_100", HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    REFRESH_TOKEN_MISSING("AUTH_101", HttpStatus.UNAUTHORIZED, "리프레시 토큰이 없습니다."),
    REFRESH_TOKEN_INVALID("AUTH_102", HttpStatus.UNAUTHORIZED, "만료되었거나 이미 사용된 토큰입니다."),

    // 403
    ACCOUNT_LOCKED("AUTH_110", HttpStatus.FORBIDDEN, "잠긴 계정입니다. 관리자에게 문의하세요."),

    // 400 — 소셜 로그인. 메시지가 그대로 로그인 화면에 뜨므로 사용자가 뭘 해야 하는지 적는다.
    OAUTH_EMAIL_REQUIRED("AUTH_001", HttpStatus.BAD_REQUEST,
            "이메일 제공에 동의해야 가입할 수 있습니다. 다시 시도해주세요."),
    OAUTH_EMAIL_ALREADY_REGISTERED("AUTH_002", HttpStatus.BAD_REQUEST,
            "이미 가입된 이메일입니다. 비밀번호로 로그인해주세요."),
    OAUTH_PROVIDER_ID_MISSING("AUTH_003", HttpStatus.BAD_REQUEST,
            "소셜 계정 정보를 받지 못했습니다. 다시 시도해주세요.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
