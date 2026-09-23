package com.otboo.notification.exception;

import com.otboo.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

    // 403
    ACCESS_DENIED("NOTIFICATION_100", HttpStatus.FORBIDDEN, "본인의 알림만 삭제할 수 있습니다."),

    // 404
    NOT_FOUND("NOTIFICATION_200", HttpStatus.NOT_FOUND, "알림을 찾을 수 없습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
