package com.otboo.feed.service;

import com.otboo.common.exception.BusinessException;
import com.otboo.common.pagination.CursorRequest;
import com.otboo.common.pagination.CursorResponse;
import com.otboo.common.pagination.SortDirection;
import com.otboo.common.security.AuthPrincipal;
import com.otboo.feed.dto.CommentCreateRequest;
import com.otboo.feed.dto.CommentDto;
import com.otboo.feed.entity.Comment;
import com.otboo.feed.entity.Feed;
import com.otboo.feed.exception.CommentErrorCode;
import com.otboo.feed.exception.FeedErrorCode;
import com.otboo.feed.query.CommentViewLoader;
import com.otboo.feed.repository.CommentRepository;
import com.otboo.feed.repository.FeedRepository;
import com.otboo.user.entity.User;
import com.otboo.user.exception.UserErrorCode;
import com.otboo.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 피드 댓글.
 *
 * <p>목록의 {@code totalCount} 는 {@code COUNT(*)} 가 아니라 {@code feeds.comment_count} 를 쓴다.
 * 무한 스크롤은 페이지를 넘길 때마다 목록을 조회, 집계 쿼리시 비용 올라감
 */
@Service
@RequiredArgsConstructor
public class CommentService {

    /** 댓글 목록의 정렬 기준. 클라이언트가 고를 수 없고 서버가 고정한다. */
    private static final String SORT_BY_CREATED_AT = "createdAt";

    private final CommentRepository commentRepository;
    private final FeedRepository feedRepository;
    private final UserRepository userRepository;
    private final CommentViewLoader viewLoader;

    @Transactional
    public CommentDto create(AuthPrincipal me, UUID feedId, CommentCreateRequest request) {
        Feed feed = findFeed(feedId);
        User author = userRepository.findById(me.userId())
                .orElseThrow(() -> new BusinessException(UserErrorCode.NOT_FOUND));

        Comment comment = commentRepository.saveAndFlush(
                Comment.create(feed, author, request.content()));
        feedRepository.increaseCommentCount(feedId);

        // 조회 전용 SQL 로 다시 읽는다. flush -> INSERT 되지 않은 행을 못 찾는 일이 없다.
        CommentDto created = viewLoader.loadOne(comment.getId());
        if (created == null) {
            throw new BusinessException(CommentErrorCode.NOT_FOUND);
        }
        return created;
    }

    @Transactional(readOnly = true)
    public CursorResponse<CommentDto> list(UUID feedId, CursorRequest page) {
        Feed feed = findFeed(feedId);
        List<CommentDto> fetched = viewLoader.loadSlice(feedId, page);

        // 스펙에 sortBy·sortDirection 이 없어 클라이언트가 보내지 않는다. 그대로 두면 응답의
        // sortBy 가 null 로 나가 "무슨 순서로 정렬된 목록인지" 를 응답이 설명하지 못한다.
        // 실제 정렬(created_at DESC — CommentViewLoader 가 고정)을 그대로 실어 보낸다.
        CursorRequest described = new CursorRequest(
                page.cursor(), page.idAfter(), page.limit(),
                SORT_BY_CREATED_AT, SortDirection.DESCENDING);

        return CursorResponse.of(
                fetched, described, feed.getCommentCount(), CommentDto::createdAt, CommentDto::id);
    }

    /**
     * 댓글 삭제
     * 프론트 수정 필요
     */
    @Transactional
    public void delete(AuthPrincipal me, UUID feedId, UUID commentId) {
        Comment comment = commentRepository.findWithAuthorById(commentId)
                .orElseThrow(() -> new BusinessException(CommentErrorCode.NOT_FOUND)
                        .addDetail("commentId", commentId.toString()));

        // 경로의 피드와 댓글이 실제로 이어져 있는지 확인한다. 이게 없으면 아무 피드 경로로나
        // 남의 댓글 id 를 넣어 지울 수 있다.
        if (!comment.getFeed().getId().equals(feedId)) {
            throw new BusinessException(CommentErrorCode.FEED_MISMATCH)
                    .addDetail("feedId", feedId.toString())
                    .addDetail("commentId", commentId.toString());
        }
        // 피드 주인도 자기 피드에 달린 댓글을 정리할 수 있어야 한다.
        boolean canDelete = comment.isAuthor(me.userId())
                || comment.getFeed().isAuthor(me.userId())
                || me.isAdmin();
        if (!canDelete) {
            throw new BusinessException(CommentErrorCode.NOT_AUTHOR)
                    .addDetail("commentId", commentId.toString());
        }

        commentRepository.delete(comment);
        feedRepository.decreaseCommentCount(feedId);
    }

    private Feed findFeed(UUID feedId) {
        return feedRepository.findById(feedId)
                .orElseThrow(() -> new BusinessException(FeedErrorCode.NOT_FOUND)
                        .addDetail("feedId", feedId.toString()));
    }
}
