package com.otboo.feed.exception;

import com.otboo.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 댓글 도메인 에러 코드. (담당: 류승지) */
@Getter
@RequiredArgsConstructor
public enum CommentErrorCode implements ErrorCode {

    // 403
    NOT_AUTHOR("COMMENT_100", HttpStatus.FORBIDDEN, "본인이 작성한 댓글만 삭제할 수 있습니다."),

    // 404
    NOT_FOUND("COMMENT_200", HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다."),

    // 409 — 다른 피드의 댓글 id 로 요청이 들어온 경우
    FEED_MISMATCH("COMMENT_300", HttpStatus.CONFLICT, "해당 피드의 댓글이 아닙니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
