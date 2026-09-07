package com.otboo.feed.exception;

import com.otboo.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 피드 · 좋아요 도메인 에러 코드. (담당: 류승지) */
@Getter
@RequiredArgsConstructor
public enum FeedErrorCode implements ErrorCode {

    // 403
    NOT_AUTHOR("FEED_100", HttpStatus.FORBIDDEN, "본인이 작성한 피드만 수정·삭제할 수 있습니다."),
    CLOTHES_NOT_OWNED("FEED_101", HttpStatus.FORBIDDEN, "본인의 옷만 피드에 올릴 수 있습니다."),

    // 404
    NOT_FOUND("FEED_200", HttpStatus.NOT_FOUND, "피드를 찾을 수 없습니다."),
    WEATHER_NOT_FOUND("FEED_201", HttpStatus.NOT_FOUND, "해당 날씨 정보를 찾을 수 없습니다."),
    CLOTHES_NOT_FOUND("FEED_202", HttpStatus.NOT_FOUND, "해당 의상을 찾을 수 없습니다."),

    // 409
    ALREADY_LIKED("FEED_300", HttpStatus.CONFLICT, "이미 좋아요를 누른 피드입니다."),
    NOT_LIKED("FEED_301", HttpStatus.CONFLICT, "좋아요를 누르지 않은 피드입니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
