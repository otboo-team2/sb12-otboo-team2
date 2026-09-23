package com.otboo.feed.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 댓글 등록 요청.
 *
 * <p>Swagger 의 {@code CommentCreateRequest} 에는 {@code feedId}·{@code authorId} 가 있지만
 * 받지 않는다. 피드는 경로변수 {@code /api/feeds/{feedId}/comments} 가, 작성자는 토큰이 정한다.
 * 본문으로 받으면 <b>남의 이름으로 댓글을 달 수 있다.</b>
 */
public record CommentCreateRequest(
        @NotBlank(message = "댓글 내용을 입력해 주세요.")
        @Size(max = 1000, message = "댓글은 1000자를 넘을 수 없습니다.")
        String content
) {
}
