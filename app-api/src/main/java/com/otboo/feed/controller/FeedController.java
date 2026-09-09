package com.otboo.feed.controller;

import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.common.security.LoginUser;
import com.otboo.feed.dto.CommentCreateRequest;
import com.otboo.feed.dto.CommentDto;
import com.otboo.feed.dto.FeedCreateRequest;
import com.otboo.feed.dto.FeedDto;
import com.otboo.feed.dto.FeedSearchCondition;
import com.otboo.feed.dto.FeedUpdateRequest;
import com.otboo.feed.service.CommentService;
import com.otboo.feed.service.FeedLikeService;
import com.otboo.feed.service.FeedService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 피드 · 좋아요 · 댓글 API
 *
 * <p><b>작성자 id 는 요청 본문에서 받지 않는다.</b> Swagger 의 {@code FeedCreateRequest} ·
 * 작성자는 언제나 {@code @LoginUser} 가 준 토큰의 주체다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/feeds")
public class FeedController {

    private final FeedService feedService;
    private final FeedLikeService feedLikeService;
    private final CommentService commentService;

    /**
     * 피드 목록 조회.
     *
     * <p>{@code page} 는 {@code cursor · idAfter · limit · sortBy · sortDirection} 을 한꺼번에
     * 받는다({@link CursorRequest}). {@code limit} 상한과 기본 정렬 방향은 그 record 가 강제한다.
     */
    @GetMapping
    public CursorResponse<FeedDto> getFeeds(
            CursorRequest page,
            @RequestParam(required = false) String keywordLike,
            @RequestParam(required = false) String skyStatusEqual,
            @RequestParam(required = false) String precipitationTypeEqual,
            @RequestParam(required = false) UUID authorIdEqual,
            @LoginUser(required = false) AuthPrincipal me
    ) {
        FeedSearchCondition condition = FeedSearchCondition.of(
                page, keywordLike, skyStatusEqual, precipitationTypeEqual, authorIdEqual);
        return feedService.search(condition, me == null ? null : me.userId());
    }

    @PostMapping
    public ResponseEntity<FeedDto> createFeed(
            @LoginUser AuthPrincipal me,
            @Valid @RequestBody FeedCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(feedService.create(me, request));
    }

    @PatchMapping("/{feedId}")
    public FeedDto updateFeed(
            @LoginUser AuthPrincipal me,
            @PathVariable UUID feedId,
            @Valid @RequestBody FeedUpdateRequest request
    ) {
        return feedService.update(me, feedId, request);
    }

    @DeleteMapping("/{feedId}")
    public ResponseEntity<Void> deleteFeed(
            @LoginUser AuthPrincipal me,
            @PathVariable UUID feedId
    ) {
        feedService.delete(me, feedId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 좋아요.
     *
     * <p>스펙에는 204 로 적혀 있지만 본문 스키마가 {@code FeedDto} 로 함께 적혀 있다.
     * 204 는 본문을 가질 수 없는 상태 코드라(RFC 9110) 200 으로 내려준다.
     * 프론트 갱신된 좋아요 수를 돌려주는 편으로 수정? (재조회 x)
     */
    @PostMapping("/{feedId}/like")
    public FeedDto likeFeed(@LoginUser AuthPrincipal me, @PathVariable UUID feedId) {
        return feedLikeService.like(me, feedId);
    }

    @DeleteMapping("/{feedId}/like")
    public ResponseEntity<Void> unlikeFeed(@LoginUser AuthPrincipal me, @PathVariable UUID feedId) {
        feedLikeService.unlike(me, feedId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{feedId}/comments")
    public CursorResponse<CommentDto> getComments(
            @PathVariable UUID feedId,
            CursorRequest page
    ) {
        return commentService.list(feedId, page);
    }

    @PostMapping("/{feedId}/comments")
    public CommentDto createComment(
            @LoginUser AuthPrincipal me,
            @PathVariable UUID feedId,
            @Valid @RequestBody CommentCreateRequest request
    ) {
        return commentService.create(me, feedId, request);
    }

    /** 스펙 확장. 댓글 작성자 · 피드 주인 · 관리자만 지울 수 있다. */
    @DeleteMapping("/{feedId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @LoginUser AuthPrincipal me,
            @PathVariable UUID feedId,
            @PathVariable UUID commentId
    ) {
        commentService.delete(me, feedId, commentId);
        return ResponseEntity.noContent().build();
    }
}
