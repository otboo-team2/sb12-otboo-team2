package com.otboo.user.exception;

import com.otboo.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 사용자 · 프로필 도메인 에러 코드. (담당: 여운정)
 *
 * <p><b>다른 파트는 이 파일을 복사해서 자기 도메인 패키지에 만들면 된다.</b>
 * 예: {@code com.otboo.clothes.exception.ClothesErrorCode}
 *
 * <p>번호 대역은 {@link ErrorCode} 주석 참고.
 */
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    // 400
    INVALID_EMAIL_FORMAT("USER_001", HttpStatus.BAD_REQUEST, "이메일 형식이 올바르지 않습니다."),
    INVALID_PASSWORD_FORMAT("USER_002", HttpStatus.BAD_REQUEST, "비밀번호 형식이 올바르지 않습니다."),
    INVALID_TEMPERATURE_SENSITIVITY("USER_003", HttpStatus.BAD_REQUEST, "온도 민감도는 1~5 사이여야 합니다."),

    // 403
    LOCKED("USER_100", HttpStatus.FORBIDDEN, "잠긴 계정입니다."),
    NOT_OWNER("USER_101", HttpStatus.FORBIDDEN, "본인의 정보만 수정할 수 있습니다."),

    // 404
    NOT_FOUND("USER_200", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    PROFILE_NOT_FOUND("USER_201", HttpStatus.NOT_FOUND, "프로필을 찾을 수 없습니다."),

    // 409
    EMAIL_DUPLICATED("USER_300", HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
