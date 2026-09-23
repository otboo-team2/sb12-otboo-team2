package com.otboo.feed.exception;

import com.otboo.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 팔로우 도메인 에러 코드. (담당: 류승지) */
@Getter
@RequiredArgsConstructor
public enum FollowErrorCode implements ErrorCode {

    // 400
    // DB 의 ck_follows_not_self 가 최종 방어, further action -> 500 이 나간다.
    SELF_FOLLOW("FOLLOW_001", HttpStatus.BAD_REQUEST, "자기 자신은 팔로우할 수 없습니다."),

    // 403
    NOT_FOLLOWER("FOLLOW_100", HttpStatus.FORBIDDEN, "본인이 한 팔로우만 취소할 수 있습니다."),

    // 404
    NOT_FOUND("FOLLOW_200", HttpStatus.NOT_FOUND, "팔로우를 찾을 수 없습니다."),

    // 409
    ALREADY_FOLLOWING("FOLLOW_300", HttpStatus.CONFLICT, "이미 팔로우한 사용자입니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
